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
        System.out.println("\ntamanho da requisição em bytes: " + request.getSizeB() );
        int sizeInt = request.getSizeB() / 4;
        int pagesNeeded = (int) Math.ceil((double) sizeInt / pageTable.getPageSizeInt());
        ///System.out.println("precisara da seguinte quantidade de paginas para alocar: " + pagesNeeded);

        //tamanho real a ser alocado (múltiplo da página)
        int allocatedInts = pagesNeeded * pageTable.getPageSizeInt();
        System.out.println("solicitado: " + sizeInt + " ints | alocado: " + allocatedInts + " ints | fragmentado: " + (allocatedInts - sizeInt));


        int[] allocatedFrames = physicalMemory.allocateFrames(pagesNeeded);
        if (allocatedFrames == null) {
            System.out.println("\nmemoria insuficiente! liberando...");
            freeOldestRequests();
            allocatedFrames = physicalMemory.allocateFrames(pagesNeeded);

            if (allocatedFrames == null) {
                throw new RuntimeException("falha ao alocar mesmo após liberar espaço na memória.");
            }
        }


        //atualiza pageTable (mapeia páginas -> frames)
        List<Integer> freePages = findFreeVirtualPages(pagesNeeded);
        System.out.println(freePages + " <- paginas livres encontradas para alocamento\n");

        for (int i = 0; i < pagesNeeded; i++) {
            //listas de paginas e frames utilizados pela requisição
            int virtualPage = freePages.get(i);
            int physicalFrame = allocatedFrames[i];
            pageTable.map(virtualPage, physicalFrame);
        }


        //escreve nos frames alocados
        int remaining = sizeInt;
        for (int i = 0; i < pagesNeeded; i++) {
            int write = Math.min(remaining, pageTable.getPageSizeInt());//fragmentará (remaining menor que page) ou preencherá a page
            for (int j = 0; j < write; j++) {
                physicalMemory.writeHeap(allocatedFrames[i], j, request.getVariableId());
            }
            remaining -= write;//subtrai size até ele zerar (como última page ou fragmentação interna)
        }

        request.setPagesAllocated(pagesNeeded);
        request.setPagesAllocatedList(freePages);
        requestQueue.add( request );

        physicalMemory.printHeap();
        System.out.println("\n");
        pageTable.printPageTable();
    }


    //retorna uma lista de paginas livres
    private List<Integer> findFreeVirtualPages(int pagesNeeded) {
        List<Integer> freePages = new ArrayList<>();
        for (int i = 0; i < pageTable.getNumPages() && freePages.size() < pagesNeeded; i++) {
            if (!pageTable.isMapped(i)) {
                freePages.add(i);
            }
        }
        return freePages;
    }


    private void freeOldestRequests() {
        //aproximadamente 30% do tamanho total da heap (de acordo com o número de frames)
        int heapPorcentage = (int) Math.ceil(physicalMemory.getHeapSizeB() * 0.3);
        int targetFreed = (int) Math.ceil( (double) heapPorcentage / physicalMemory.getFrameSizeB() );//converte para número de frames

        int freedFrames = 0;
        System.out.println("precisa liberar pelo menos " + heapPorcentage + " bytes (" + targetFreed + " frames)");

        while (freedFrames < targetFreed && !requestQueue.isEmpty()) {//acumula requests até bater o número de frames alvo ou mais (30%+)
            Request oldest = requestQueue.poll();
            freedFrames += oldest.getPagesAllocated();
            totalFreedRequests++;//total de variáveis removidas da heap

            //desmapeia todas as páginas da requisição e desaloca da memória
            for (int virtualPage : oldest.getPagesAllocatedList()) {
                int physicalFrame = pageTable.getPhysicalFrame(virtualPage);

                pageTable.unmap(virtualPage);
                physicalMemory.freeFrame(physicalFrame);//muda alocado para falso e zera o heap associado ao frame (numero e tamanho do frame)
                System.out.println("desmapeado: pagina " + virtualPage + " -> frame " + physicalFrame);
            }

            System.out.println("liberado " + oldest.getPagesAllocated() + " frames - variavel " + oldest.getVariableId());
        }

        System.out.println("\ntotal liberado: " + freedFrames + " frames. | total de variaveis removidas ate agora: " + totalFreedRequests);
    }


    public static void main(String[] args) {
        //user informa tamanho da heap (KB) e page (B) - size/pageSize=numPages
        int heapSizeKB = 1;
        int pageSizeB = 64;
        PhysicalMemory physicalMemory = new PhysicalMemory(heapSizeKB, pageSizeB);
        PageTable pageTable = new PageTable(heapSizeKB, pageSizeB);

        MemoryManager simulator = new MemoryManager(physicalMemory, pageTable);
        //RequestGenerator rg = new RequestGenerator(4, 256);//informa limite de tamanho (B) mínimo e máximo de requests

        //int quantidade = 55;//informa quantidade de requests
        long startTime = System.nanoTime();
        /*for(int x = 0; x < quantidade; x++){
            simulator.allocateVariable( rg.generateRequest() );
        }*/

        //page e fragmentação
        simulator.allocateVariable( new Request(1, 512) );
        simulator.allocateVariable( new Request(2, 388) );
        simulator.allocateVariable( new Request(3, 240) );
        simulator.allocateVariable( new Request(4, 256) );
        simulator.allocateVariable( new Request(5, 512) );

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

        //double AverageRequestSizeB = (double) rg.getTotalRandomSizeB() / quantidade;

        //System.out.println("\nnumero total de requisiçoes atendidas: " + quantidade);
        //System.out.println("tamanho medio das variaveis alocadas em bytes: " + AverageRequestSizeB );
        System.out.println("numero total de variaveis removidas da heap: " + totalFreedRequests);
        System.out.println("tempo total de execucao da memoria em MS: " + runtimeMS);
    }

}