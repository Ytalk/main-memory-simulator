package MMS;

import java.util.*;

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
        int sizeInt = request.getSizeB() / 4;
        int pagesNeeded = (int) Math.ceil((double) sizeInt / pageTable.getPageSizeInt());

        //tamanho real a ser alocado (múltiplo da página)
        int allocatedInts = pagesNeeded * pageTable.getPageSizeInt();
        System.out.println("\ntamanho da requisição: " + request.getSizeB() + " bytes | solicitado: " + sizeInt +
                " ints | alocado: " + allocatedInts + " ints | paginas usadas: " + pagesNeeded + " | fragmentado: " + (allocatedInts - sizeInt));


        int[] allocatedFrames = physicalMemory.allocateFrames(pagesNeeded);
        if (allocatedFrames == null) {
            System.out.println("\nmemoria insuficiente! liberando...");
            freeOldestRequests();
            allocatedFrames = physicalMemory.allocateFrames(pagesNeeded);

            if (allocatedFrames == null) {
                System.out.println("\nmemoria insuficiente! liberando... (segunda e ultima tentativa)");
                freeOldestRequests();
                allocatedFrames = physicalMemory.allocateFrames(pagesNeeded);

                if (allocatedFrames == null) {
                    throw new RuntimeException("falha ao alocar mesmo após liberar espaço na memória.");
                }
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
            //fragmentará (remaining menor que page) ou preencherá a page
            int write = Math.min(remaining, pageTable.getPageSizeInt());
            for (int j = 0; j < write; j++) {
                physicalMemory.writeHeap(allocatedFrames[i], j, request.getVariableId());
            }
            remaining -= write;//subtrai size até ele zerar (como última page ou fragmentação interna)
        }

        request.setPagesAllocated(pagesNeeded);
        request.setPagesAllocatedList(freePages);
        requestQueue.add( request );

        physicalMemory.printHeap();
        System.out.print("\n");
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
        Scanner scanner = new Scanner(System.in);

        System.out.print("informar tamanho da Heap (KB): ");
        int heapSizeKB = scanner.nextInt();
        System.out.print("informar tamanho da page (Bytes): ");
        int pageSizeB = scanner.nextInt();
        System.out.print("informar quantidade de requests: ");
        int quantidade = scanner.nextInt();
        System.out.print("informar tamanho (Bytes) mínimo das requests: ");
        int min = scanner.nextInt();
        System.out.print("informar tamanho (Bytes) máximo das requests: ");
        int max = scanner.nextInt();


        PhysicalMemory physicalMemory = new PhysicalMemory(heapSizeKB, pageSizeB);
        PageTable pageTable = new PageTable(heapSizeKB, pageSizeB);
        MemoryManager simulator = new MemoryManager(physicalMemory, pageTable);


        RequestGenerator generator = new RequestGenerator(min, max);//informa limite de tamanho (B) mínimo e máximo de requests
        long startTime = System.nanoTime();
        for(int x = 0; x < quantidade; x++){
            simulator.allocateVariable( generator.generateRequest() );
        }

        //page e fragmentação (teste)
        /*simulator.allocateVariable( new Request(1, 512) );
        simulator.allocateVariable( new Request(2, 388) );
        simulator.allocateVariable( new Request(3, 230) );
        simulator.allocateVariable( new Request(4, 256) );
        simulator.allocateVariable( new Request(5, 530) );*/


        long endTime = System.nanoTime();
        double runtimeMS = (endTime - startTime) / 1000000.0;

        double AverageRequestSizeB = (double) generator.getTotalRandomSizeB() / quantidade;

        System.out.println("\nnumero total de requisiçoes atendidas: " + quantidade);
        System.out.println("tamanho medio das variaveis alocadas em bytes: " + AverageRequestSizeB );
        System.out.println("numero total de variaveis removidas da heap: " + totalFreedRequests);
        System.out.println("tempo total de execucao da memoria em MS: " + runtimeMS);
    }

}