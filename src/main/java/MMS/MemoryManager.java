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


    private void allocateVariable(int variableId, int sizeBytes) {
        int sizeInt = sizeBytes / 4;
        int pagesNeeded = (int) Math.ceil((double) sizeInt / pageTable.getPageSizeInt());

        //tamanho real a ser alocado (múltiplo da página)
        int allocatedInts = pagesNeeded * pageTable.getPageSizeInt();

        System.out.println("solicitado: " + sizeInt + " ints | alocado: " + allocatedInts + " ints | fragmentado: " + (allocatedInts - sizeInt));

        int startAddress = heap.findFreeHeap(allocatedInts * 4);
        System.out.println("address inicial: " + startAddress);

        requestQueue.add( new Request(variableId, startAddress, sizeBytes) );

        if (startAddress == -1) {
            System.out.println("memória insuficiente. liberando...");
            freeOldestRequests();
            startAddress = heap.findFreeHeap(allocatedInts * 4);
            System.out.println("address inicial: " + startAddress);
            if (startAddress == -1) {
                System.out.println("falha");
                //return;
            }
        }

        //calcula quantas páginas são necessárias para a requisição
        int pagesRequired = (int) Math.ceil( (double) sizeBytes / pageTable.getPageSizeB() );
        //int pagesRequired = ( (sizeBytes / 4) + pageTable.getPageSizeInt() - 1 ) / pageTable.getPageSizeInt();///page >= 4b
        System.out.println("precisara da seguinte quantidade de paginas para alocar: " + pagesRequired);


        //atualiza pageTable (mapeia páginas -> frames)
        for (int i = 0; i < pagesRequired; i++) {
            int virtualPage = ( startAddress / pageTable.getPageSizeB() ) + i;///páginas contíguas int?//////////
            int physicalFrame = physicalMemory.allocateFrame();//primeiro frame livre encontrado
            System.out.println("mapeamento escolhido 1: pagina " + virtualPage + " frame " + physicalFrame);

            if (physicalFrame != -1) {
                pageTable.map(virtualPage, physicalFrame);
            } else {
                freeOldestRequests();
                int newFrame = physicalMemory.allocateFrame();
                System.out.println("mapeamento escolhido 2: pagina " + virtualPage + " frame " + physicalFrame);
                pageTable.map(virtualPage, newFrame);
            }
        }

        //marca o espaço no heap com o ID
        int startIndex = startAddress / 4;
        heap.allocateHeap(startIndex, allocatedInts, variableId);

        heap.printHeap();
        pageTable.printPageTable();
    }


    private void freeOldestRequests() {
        //30% do tamanho total da heap (em inteiros)
        int targetFreeSpace = (int) Math.ceil(heap.getSizeInt() * 0.3);
        int freedSpace = 0;

        System.out.println("equivalência a 30% para liberar em int: " + targetFreeSpace);

        //até atingir o espaço necessário
        while (freedSpace < targetFreeSpace && !requestQueue.isEmpty()) {
            Request oldest = requestQueue.poll();
            if (oldest != null) {
                int requestSizeInt = oldest.getRequestSizeB() / 4;

                freeRequest(oldest);
                freedSpace += requestSizeInt;
                System.out.println("tamanho liberado em int: " + freedSpace);
                System.out.println("liberado: " + requestSizeInt + " inteiros com ID: " + oldest.getVariableId());
            }
        }

    }

    private void freeRequest(Request request) {
        int pagesAllocated = (int) Math.ceil((double) request.getRequestSizeB() / pageTable.getPageSizeB());
        System.out.println("paginas alocadas para liberar: " + pagesAllocated);

        for (int i = 0; i < pagesAllocated; i++) {
            int virtualPage = ((request.getStartAddress() * 4) / pageTable.getPageSizeB()) + i;
            if (pageTable.isMapped(virtualPage)) {
                int frame = pageTable.getPhysicalFrame(virtualPage);
                physicalMemory.getFrame(frame).setAllocated(false);
                pageTable.unmap(virtualPage);
            }
        }

        heap.freeHeap(request.getRequestSizeB() / 4, request.getStartAddress());
    }


    public static void main(String[] args) {//test
        MemoryManager simulator = new MemoryManager(1, 64);//size/pageSize=numPages

        simulator.allocateVariable(1, 512);
        simulator.allocateVariable(2, 388);
        simulator.allocateVariable(3, 256);
        simulator.allocateVariable(4, 256);

    }
}