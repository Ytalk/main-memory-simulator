package MMS;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.List;
import java.util.Scanner;
import java.util.concurrent.ConcurrentLinkedQueue;

public class MemoryManager {
    private final PhysicalMemory physicalMemory;
    private final PageTable pageTable;
    private final ConcurrentLinkedQueue<Request> requestQueue = new ConcurrentLinkedQueue();//essa fila não tem problema com cheio/vazio (não precisa de muito controle e é CAS-based), então NÃO foi optado o BlockingQueue
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


    private int[] tryFindFreeFrames(int pagesNeeded) {
        int[] allocatedFrames = physicalMemory.findFreePhysicalFrames(pagesNeeded);
        if (allocatedFrames == null) {
            System.out.println("\nmemoria insuficiente! liberando...");
            freeOldestRequests();
            allocatedFrames = physicalMemory.findFreePhysicalFrames(pagesNeeded);

            if (allocatedFrames == null) {
                System.out.println("\nmemoria insuficiente! liberando... (segunda e ultima tentativa)");
                freeOldestRequests();
                allocatedFrames = physicalMemory.findFreePhysicalFrames(pagesNeeded);
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
        System.out.println("precisa liberar pelo menos " + targetFreed + " frames (30% da heap)");

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

        int[] allocatedFrames = tryFindFreeFrames(pagesNeeded);
        mapPages(request, allocatedFrames);
        //escreve nos frames alocados
        physicalMemory.writeToHeap(request.getVariableId(), allocatedFrames, sizeInt, pageTable.getPageSizeInt());

        requestQueue.add( request );

        physicalMemory.printHeap();
        System.out.print("\n");
        pageTable.printPageTable();
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


    public void loadRequestsFromFile(String filePath) {
        try (BufferedReader reader = new BufferedReader( new FileReader(filePath) )) {
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();

                if (!line.isEmpty()) {
                    String[] partes = line.split(",");
                    if (partes.length == 2) {
                        int id = Integer.parseInt(partes[0].trim());
                        int size = Integer.parseInt(partes[1].trim());

                        allocateVariable(new Request(id, size));
                    }
                }
            }
        } catch (IOException e) {
            System.err.println("error: " + e.getMessage());
        }
    }



    public static void main(String[] args) {
        //user informa tamanho da heap (KB) e page (B) - size/pageSize=numPages
        Scanner scanner = new Scanner(System.in);

        System.out.print("informar tamanho da Heap (KB): ");
        int heapSizeKB = 2;
        System.out.print("informar tamanho da page (Bytes): ");
        int pageSizeB = 64;
        System.out.print("informar quantidade de requests: ");
        int quantidade = 1000;
        System.out.print("informar tamanho (Bytes) mínimo das requests: ");
        //int min = 4;
        System.out.print("informar tamanho (Bytes) máximo das requests: ");
        //int max = 256;


        PhysicalMemory physicalMemory = new PhysicalMemory(heapSizeKB, pageSizeB);
        PageTable pageTable = new PageTable(heapSizeKB, pageSizeB);

        MemoryManager simulator = new MemoryManager(physicalMemory, pageTable);

        //RequestGenerator generator = new RequestGenerator(min, max);//informa limite de tamanho (B) mínimo e máximo de requests
        long startTime = System.nanoTime();
        //for(int x = 0; x < quantidade; x++){
            //simulator.allocateVariable( generator.generateRequest() );
        //}

        //page e fragmentação (teste)
        /*simulator.allocateVariable( new Request(1, 512) );
        simulator.allocateVariable( new Request(2, 388) );
        simulator.allocateVariable( new Request(3, 230) );
        simulator.allocateVariable( new Request(4, 256) );
        simulator.allocateVariable( new Request(5, 530) );*/
        simulator.loadRequestsFromFile("C:\\dev\\requests_converted.txt");


        long endTime = System.nanoTime();
        double runtimeMS = (endTime - startTime) / 1_000_000.0;

        //double AverageRequestSizeB = (double) generator.getTotalRandomSizeB() / quantidade;

        System.out.println("\nnumero total de requisiçoes atendidas: " + quantidade);
        //System.out.println("tamanho medio das variaveis alocadas em bytes: " + AverageRequestSizeB );
        System.out.println("numero total de variaveis removidas da heap: " + totalFreedRequests);
        System.out.println("tempo total de execucao da memoria em MS: " + runtimeMS);
    }

}