package MMS;

import java.util.LinkedList;
import java.util.Queue;

public class MemoryManager {
    private PhysicalMemory physicalMemory;
    private PageTable pageTable;
    private HeapMemory heap;
    private Queue<Request> requestQueue;
    private int requestCounter;

    private MemoryManager(int heapSizeKB, int pageSizeB) {
        this.physicalMemory = new PhysicalMemory(heapSizeKB, pageSizeB);
        this.pageTable = new PageTable(heapSizeKB, pageSizeB);
        this.heap = new HeapMemory(heapSizeKB);
        this.requestQueue = new LinkedList<>();
        this.requestCounter = 0;
    }


    private void allocateVariable(int variableId, int sizeBytes, Request request) {
        int startAddress = 0;//test - index inicial da heap - x4/pageSize = index inicial da page

        //calcula quantas páginas são necessárias para a requisição
        int pagesRequired = (int) Math.ceil( (double) sizeBytes / pageTable.getPageSizeB() );
        //int pagesRequired = ( (sizeBytes / 4) + pageTable.getPageSizeInt() - 1 ) / pageTable.getPageSizeInt();///page >= 4b
        System.out.println("precisara da seguinte quantidade de paginas: " + pagesRequired);


        //atualiza pageTable (mapeia páginas -> frames)
        for (int i = 0; i < pagesRequired; i++) {
            int virtualPage = ( (startAddress * 4) / pageTable.getPageSizeB() ) + i;///páginas contíguas int?
            int physicalFrame = physicalMemory.allocateFrame();//primeiro frame livre encontrado

            if (physicalFrame != -1) {
                pageTable.map(virtualPage, physicalFrame);
            } else {
                freeOldestRequests();
                int newFrame = physicalMemory.allocateFrame();
                pageTable.map(virtualPage, newFrame);
            }
        }

        //marca o espaço no heap com o ID
        int d = sizeBytes / 4;///size >= 4
        heap.allocateHeap(startAddress, d, variableId);

        //registra a requisição
        requestQueue.add( request );

        heap.printHeap();
        pageTable.printPageTable();
    }


    private void freeOldestRequests() {
        int requestsToRemove = (int) Math.ceil(requestQueue.size() * 0.3);
        System.out.println("liberar " + requestsToRemove);

        for (int i = 0; i < requestsToRemove; i++) {
            Request oldest = requestQueue.poll();
            if (oldest != null) {
                freeRequest(oldest);
            }
        }
    }


    private void freeRequest(Request request) {
        int pagesAllocated = (int) Math.ceil((double) request.getRequestSizeB() / pageTable.getPageSizeB());

        for (int i = 0; i < pagesAllocated; i++) {
            int virtualPage = ((request.getStartAddress() * 4) / pageTable.getPageSizeB()) + i;
            if (pageTable.isMapped(virtualPage)) {
                int frame = pageTable.getPhysicalFrame(virtualPage);
                physicalMemory.getFrame(frame).setAllocated(false);
                pageTable.unmap(virtualPage);
            }
        }

        int sizeInt = request.getRequestSizeB() / 4;
        heap.freeHeap(sizeInt, request.getStartAddress());
    }


    public static void main(String[] args) {//test
        MemoryManager simulator = new MemoryManager(1, 256);//size/pageSize=numPages
        simulator.allocateVariable( 5, 1000, new Request(5, 0, 1000, 0) );
        simulator.allocateVariable( 3, 64, new Request(3, 250, 64, 1) );
    }
}