package MMS;

import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import java.util.Scanner;
import java.util.ArrayList;

public class MemoryManager {
    private final PhysicalMemory physicalMemory;
    private final PageTable pageTable;
    private final Queue<Request> requestQueue = new LinkedList<>();
    private static int totalFreedRequests = 0;

    private MemoryManager(PhysicalMemory physicalMemory, PageTable PageTable) {
        this.physicalMemory = physicalMemory;
        this.pageTable = PageTable;
    }


    private void logAllocationRequest(Request req, int ints, int pages, int pageSizeInts) {
        //o espaço utilizado na memória é o tamanho requisição, mas o espaço consumido é maior ou igual (deve ser múltiplo do tamanho da página)
        System.out.printf("\nRequest: ID = %d | %d bytes -> %d ints | %d pages (pageSize = %d ints) | %d fragmented ints\n",
                req.getVariableId(), req.getSizeB(), ints, pages, pageSizeInts, (pages * pageSizeInts - ints));
    }


    private int[] tryAllocateFrames(int pagesNeeded) {
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
                    throw new RuntimeException("falha ao alocar mesmo após liberar espaço na memória.");//custom
                }
            }
        }
        return allocatedFrames;
    }

    private void freeOldestRequests() {
        //aproximadamente 30% do tamanho total da heap (de acordo com o número de frames)
        int targetFreed = (int) Math.ceil(physicalMemory.getNumFrames() * 0.3);
        System.out.println("precisa liberar pelo menos " + targetFreed + " frames");

        int freedFrames = 0;
        while (freedFrames < targetFreed && !requestQueue.isEmpty()) {//acumula requests até bater o número de frames alvo ou mais (30%+)
            Request oldest = requestQueue.poll();
            freedFrames += oldest.getPagesAllocated();
            totalFreedRequests++;//total de variáveis removidas da heap

            for (int virtualPage : oldest.getPagesAllocatedList()) {//desmapeia todas as páginas da requisição e desaloca da memória
                int physicalFrame = pageTable.getPhysicalFrame(virtualPage);

                pageTable.unmap(virtualPage);
                physicalMemory.freeFrame(physicalFrame);//muda alocado para falso e zera o heap associado ao frame (numero e tamanho do frame)
                System.out.println("desmapeado: pagina " + virtualPage + " -> frame " + physicalFrame);
            }
            System.out.println("liberado " + oldest.getPagesAllocated() + " frames - variavel " + oldest.getVariableId());
        }
        System.out.println("\ntotal liberado: " + freedFrames + " frames. | total de variaveis removidas ate agora: " + totalFreedRequests);
    }


    private void allocateVariable(Request request) {
        int sizeInt = (request.getSizeB() + Integer.BYTES - 1) / Integer.BYTES;
        int pagesNeeded = (sizeInt + pageTable.getPageSizeInt() - 1) / pageTable.getPageSizeInt();

        logAllocationRequest(request, sizeInt, pagesNeeded, pageTable.getPageSizeInt());


        int[] allocatedFrames = tryAllocateFrames(pagesNeeded);

        mapPages(request, allocatedFrames);

        //escreve nos frames alocados
        /*int remaining = sizeInt;
        for (int i = 0; i < pagesNeeded; i++) {
            //fragmentará (remaining menor que page) ou preencherá a page
            int write = Math.min(remaining, pageTable.getPageSizeInt());
            for (int j = 0; j < write; j++) {
                physicalMemory.writeHeap(allocatedFrames[i], j, request.getVariableId());
            }
            remaining -= write;//subtrai size até ele zerar (como última page ou fragmentação interna)
        }*/
        writeDataBatch(request, allocatedFrames, sizeInt, pageTable.getPageSizeInt());

        requestQueue.add( request );

        physicalMemory.printHeap();
        System.out.print("\n");
        pageTable.printPageTable();
    }


    //escreve nos frames usando escrita em lote
    private void writeDataBatch(Request request, int[] frames, int intsNeeded, int pageSizeInts) {
        int remaining = intsNeeded;
        for (int frame : frames) {
            int toWrite = Math.min(remaining, pageSizeInts);
            //escrita em lote: preenche "toWrite" posições do frame
            physicalMemory.writeBlock(frame, request.getVariableId(), toWrite);
            remaining -= toWrite;
        }
    }


    //atualiza pageTable (mapeia páginas -> frames)
    private void mapPages(Request request, int[] allocatedFrames) {
        List<Integer> freePages = pageTable.findFreeVirtualPages(allocatedFrames.length);
        System.out.println(freePages + " <- paginas livres encontradas para alocamento\n");

        for (int i = 0; i < allocatedFrames.length; i++) {
            //listas, paginas e frames, utilizados pela requisição
            pageTable.map(freePages.get(i), allocatedFrames[i]);
        }

        request.setPagesAllocated(allocatedFrames.length);
        request.setPagesAllocatedList(freePages);
    }


    public static void main(String[] args) {
        //user informa tamanho da heap (KB) e page (B) - size/pageSize=numPages
        Scanner scanner = new Scanner(System.in);

        System.out.print("informar tamanho da Heap (KB): ");
        int heapSizeKB = 1;
        System.out.print("informar tamanho da page (Bytes): ");
        int pageSizeB = 32;
        System.out.print("informar quantidade de requests: ");
        int quantidade = 11;
        System.out.print("informar tamanho (Bytes) mínimo das requests: ");
        int min = 4;
        System.out.print("informar tamanho (Bytes) máximo das requests: ");
        int max = 256;


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