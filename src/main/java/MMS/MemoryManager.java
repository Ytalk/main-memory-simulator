package MMS;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;

public class MemoryManager {
    private PhysicalMemory physicalMemory;
    private PageTable pageTable;
    private Queue<Request> requestQueue;
    private static int totalFreedRequests = 0;

    private MemoryManager(PhysicalMemory physicalMemory, PageTable PageTable) {
        this.physicalMemory = physicalMemory;
        this.pageTable = PageTable;
        this.requestQueue = new LinkedList<>();
    }


    private void allocateVariable(Request request) {
        System.out.println("tamanho da requisição em bytes: " + request.getSizeB() );
        int sizeInt = request.getSizeB() / 4;
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
                physicalMemory.writeHeap(allocatedFrames[i], j, request.getVariableId());
            }
            remaining -= write;//subtrai size até ele zerar (como última page ou fragmentação interna)
        }

        request.setPagesAllocated(pagesNeeded);
        request.setFirstVirtualPage(firstVirtualPage);
        requestQueue.add( request );

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
            totalFreedRequests++;//total de variáveis removidas da heap

            //desmapeia todas as páginas da requisição e desaloca da memória
            for (int i = 0; i < oldest.getPagesAllocated(); i++) {
                int virtualPage = oldest.getFirstVirtualPage() + i;
                int physicalFrame = pageTable.getPhysicalFrame(virtualPage);

                pageTable.unmap(virtualPage);
                physicalMemory.freeFrame(physicalFrame);
            }

            System.out.println("liberado " + oldest.getPagesAllocated() + " frames da variavel " + oldest.getVariableId());
        }

        System.out.println("total liberado: " + freedFrames + " frames. | total de variaveis removidas ate agora: " + totalFreedRequests);
    }



    public static void main(String[] args) {
        //user informa tamanho da heap (KB) e page (B)  -  size/pageSize=numPages
        int heapSizeKB = 1;
        int pageSizeB = 64;
        PhysicalMemory physicalMemory = new PhysicalMemory(heapSizeKB, pageSizeB);
        PageTable pageTable = new PageTable(heapSizeKB, pageSizeB);

        MemoryManager simulator = new MemoryManager(physicalMemory, pageTable);
        RequestGenerator rg = new RequestGenerator(4, 256);//informa limite de tamanho (B) mínimo e máximo de requests

        int quantidade = 5;//informa quantidade de requests
        long startTime = System.nanoTime();
        for(int x = 0; x < quantidade; x++){
            simulator.allocateVariable( rg.generateRequest() );
        }


        //page e fragmentação
        /*simulator.allocateVariable(1, 512);
        simulator.allocateVariable(2, 388);
        simulator.allocateVariable(3, 256);
        simulator.allocateVariable(4, 256);
        simulator.allocateVariable(5, 64);*/

        //page
        /*simulator.allocateVariable( new Request(1, 128) );
        simulator.allocateVariable( new Request(2, 256) );
        simulator.allocateVariable( new Request(3, 128) );
        simulator.allocateVariable( new Request(4, 128) );
        simulator.allocateVariable( new Request(5, 128) );
        simulator.allocateVariable( new Request(6, 128) );
        simulator.allocateVariable( new Request(7, 256) );
        simulator.allocateVariable( new Request(8, 256) );*/

        long endTime = System.nanoTime();
        double runtimeMS = (endTime - startTime) / 1000000.0;

        double AverageRequestSizeB = (double) rg.getTotalRandomSizeB() / quantidade;

        System.out.println("tamanho medio das variaveis alocadas em bytes: " + AverageRequestSizeB );
        System.out.println("tempo total de execucao da memoria em MS: " + runtimeMS);
        //System.out.println("numero total de requisiçoes atendidas: " + quantidade);
        System.out.println("numero total de variaveis removidas da heap: " + totalFreedRequests);
    }

}