package MMS;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;

public class MemoryManager {
    private PhysicalMemory physicalMemory;
    private PageTable pageTable;
    private Queue<Request> requestQueue;
    private int requestCounter;

    private MemoryManager(int heapSizeKB, int pageSizeB) {
        this.physicalMemory = new PhysicalMemory(heapSizeKB, pageSizeB);
        this.pageTable = new PageTable(heapSizeKB, pageSizeB);
        this.requestQueue = new LinkedList<>();
        this.requestCounter = 0;
    }


    private void allocateVariable(int variableId, int sizeBytes) {
        int sizeInt = sizeBytes / 4;
        int pagesNeeded = (int) Math.ceil((double) sizeInt / pageTable.getPageSizeInt());
        System.out.println("precisara da seguinte quantidade de paginas para alocar: " + pagesNeeded);


        //tamanho real a ser alocado (múltiplo da página) DEBUG
        int allocatedInts = pagesNeeded * pageTable.getPageSizeInt();
        System.out.println("solicitado: " + sizeInt + " ints | alocado: " + allocatedInts + " ints | fragmentado: " + (allocatedInts - sizeInt));

        int[] allocatedFrames = physicalMemory.allocateFrames(pagesNeeded);
        if (allocatedFrames == null) {
            System.out.println("memória insuficiente. liberando...");
            freeOldestRequests();
            allocatedFrames = physicalMemory.allocateFrames(pagesNeeded);

            if (allocatedFrames == null) {
                System.out.println("falha");
                return;
            }
        }


        //atualiza pageTable (mapeia páginas -> frames)
        int firstVirtualPage = findFreeVirtualPages(pagesNeeded);//pega apenas a primeira (deve ser corrigido para mapeamento não-contíguo)
        System.out.println("primeira pagina virtual: " + firstVirtualPage);

        for (int i = 0; i < pagesNeeded; i++) {
            pageTable.map(firstVirtualPage  + i, allocatedFrames[i]);
        }


        //escreve nos frames alocados
        int remaining = sizeInt;
        for (int i = 0; i < pagesNeeded; i++) {
            int write = Math.min(remaining, pageTable.getPageSizeInt());//fragmentará ou preencherá a page
            for (int j = 0; j < write; j++) {
                physicalMemory.writeHeap(allocatedFrames[i], j, variableId);
            }
            remaining -= write;//subtrai size até ele zerar (como última page ou fragmentação interna)
        }

        requestQueue.add( new Request(variableId, firstVirtualPage, pagesNeeded, sizeBytes) );

        physicalMemory.printHeap();
        pageTable.printPageTable();
    }


    private int findFreeVirtualPages(int pagesNeeded) {
        //busca qualquer página livre
        List<Integer> freePages = new ArrayList<>();
        for (int i = 0; i < pageTable.getNumPages() && freePages.size() < pagesNeeded; i++) {
            if (!pageTable.isMapped(i)) {
                System.out.println("a seguinte page encontra-se livre: " + i);
                freePages.add(i);
            }
        }

        return freePages.get(0);
    }


    private void freeOldestRequests() {
        //aproximadamente 30% do tamanho total da heap (de acordo com o número de frames)
        int targetFreed = (int) Math.ceil(physicalMemory.getNumFrames() * 0.3);
        int freedFrames = 0;

        System.out.println("precisa liberar pelo menos " + targetFreed + " frames");

        while (freedFrames < targetFreed && !requestQueue.isEmpty()) {//acumula requests até bater o número de frames alvo ou mais (30%+)
            Request oldest = requestQueue.poll();
            freedFrames += oldest.getPagesAllocated();

            //desmapeia todas as páginas da requisição e desaloca da memória
            for (int i = 0; i < oldest.getPagesAllocated(); i++) {
                int virtualPage = oldest.getFirstVirtualPage() + i;
                int physicalFrame = pageTable.getPhysicalFrame(virtualPage);

                pageTable.unmap(virtualPage);
                physicalMemory.freeFrame(physicalFrame);
            }

            System.out.println("liberado " + oldest.getPagesAllocated() + " frames da variavel " + oldest.getVariableId());
        }

        System.out.println("total liberado: " + freedFrames + " frames");
    }



    public static void main(String[] args) {
        MemoryManager simulator = new MemoryManager(1, 64);//size/pageSize=numPages

        /*simulator.allocateVariable(1, 512);
        simulator.allocateVariable(2, 388);
        simulator.allocateVariable(3, 256);
        simulator.allocateVariable(4, 256);
        simulator.allocateVariable(5, 64);*/

        simulator.allocateVariable(1, 128);
        simulator.allocateVariable(2, 256);
        simulator.allocateVariable(3, 128);
        simulator.allocateVariable(4, 128);
        simulator.allocateVariable(5, 128);
        simulator.allocateVariable(6, 128);
        simulator.allocateVariable(7, 256);
        simulator.allocateVariable(8, 256);
    }
}