package MMS;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.List;
import java.util.Scanner;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class MemoryManager {
    private final PhysicalMemory physicalMemory;
    private final PageTable pageTable;

    private static RequestGenerator generator;

    private final static BlockingQueue<Request> waitingRequestsQueue = new LinkedBlockingQueue<>();//buffer
    private final ConcurrentLinkedQueue<Request> allocatedRequestsQueue = new ConcurrentLinkedQueue();

    private final Object toMonitor = new Object();
    private static int totalFreedRequests = 0;

    private int numThreads = Runtime.getRuntime().availableProcessors();


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
        synchronized (toMonitor) {

            //aproximadamente 30% do tamanho total da heap (de acordo com o número de frames)
            int targetFreed = (int) Math.ceil(physicalMemory.getNumFrames() * 0.3);
            System.out.println("precisa liberar pelo menos " + targetFreed + " frames (30% da heap)");

            int freedFrames = 0;
            while (freedFrames < targetFreed && !allocatedRequestsQueue.isEmpty()) {//acumula requests até bater o número de frames alvo ou mais (30%+)
                Request oldest = allocatedRequestsQueue.poll();
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
    }


    private void allocateVariable(Request request) {
        int sizeInt = (request.getSizeB() + Integer.BYTES - 1) / Integer.BYTES;
        int pagesNeeded = (sizeInt + pageTable.getPageSizeInt() - 1) / pageTable.getPageSizeInt();

        synchronized (toMonitor) {
            logAllocationRequest(request, sizeInt, pagesNeeded, pageTable.getPageSizeInt());
            int[] allocatedFrames = tryFindFreeFrames(pagesNeeded);//encontra frames livres ou descobre que não há frames/espaço suficiente
            mapPages(request, allocatedFrames);//encontra pages livres e faz mapeamento com os frames livres
            physicalMemory.writeToHeap(request.getVariableId(), allocatedFrames, sizeInt, pageTable.getPageSizeInt());//escreve nos frames encontrados
            allocatedRequestsQueue.add(request);

            physicalMemory.printHeap();
            System.out.print("\n");
            pageTable.printPageTable();
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

    Runnable readerProducer = () -> {
        try (BufferedReader reader = new BufferedReader(new FileReader("C:\\dev\\requests_converted.txt"))) {
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty()) continue;
                String[] parts = line.split(",");
                Request req = new Request(Integer.parseInt(parts[0]), Integer.parseInt(parts[1]));
                waitingRequestsQueue.put(req);
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {//sempre entra em finally para sinalizar fim, inserindo poison pills
            for (int i = 0; i < numThreads; i++) {
                try {
                    waitingRequestsQueue.put( new Request(-1, -1) );
                } catch (InterruptedException ignored) {
                    Thread.currentThread().interrupt();
                }
            }
        }
    };

    Runnable randomProducer(int quantity) {
        return () -> {
            try {
                for (int x = 0; x < quantity; x++) {
                    waitingRequestsQueue.put(generator.generateRequest());
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                System.err.println("Thread interrompida: " + e.getMessage());
            } finally {//sempre entra em finally para sinalizar fim, inserindo poison pills
                for (int i = 0; i < numThreads; i++) {
                    try {
                        waitingRequestsQueue.put(new Request(-1, -1));
                    } catch (InterruptedException ignored) {
                        Thread.currentThread().interrupt();
                    }
                }
            }
        };
    }

    Runnable testProduder = () -> {
        try {
            waitingRequestsQueue.put(new Request(1, 512));
            waitingRequestsQueue.put(new Request(2, 388));
            waitingRequestsQueue.put(new Request(3, 230));
            waitingRequestsQueue.put(new Request(4, 256));
            waitingRequestsQueue.put(new Request(5, 530));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            System.err.println("Thread interrompida: " + e.getMessage());
        } finally {
            for (int i = 0; i < numThreads; i++) {
                try {
                    waitingRequestsQueue.put(new Request(-1, -1)); //poison pill por thread
                } catch (InterruptedException ignored) {
                    Thread.currentThread().interrupt();
                }
            }
        }
    };


    Runnable consumer = () -> {
        try {
            while (true) {
                Request req = waitingRequestsQueue.take();
                if (req.getSizeB() == -1) break;//poison pill
                allocateVariable(req);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    };




    public static void main(String[] args) {
        Scanner scanner = new Scanner(System.in);//user informa tamanho da heap (KB) e page (B) - size/pageSize=numPages

        System.out.print("informar tamanho da Heap (KB): ");
        int heapSizeKB = 4;
        System.out.print("informar tamanho da page (Bytes): ");
        int pageSizeB = 64;
        System.out.print("informar quantidade de requests: ");
        int quantity = 1000;
        System.out.print("informar tamanho (Bytes) mínimo das requests: ");
        int min = 4;
        System.out.print("informar tamanho (Bytes) máximo das requests: ");
        int max = 256;

        PhysicalMemory physicalMemory = new PhysicalMemory(heapSizeKB, pageSizeB);
        PageTable pageTable = new PageTable(heapSizeKB, pageSizeB);
        MemoryManager simulator = new MemoryManager(physicalMemory, pageTable);
        generator = new RequestGenerator(min, max);//informa limite de tamanho (B) mínimo e máximo de requests

        ExecutorService exec = Executors.newFixedThreadPool( simulator.numThreads );
        //ExecutorService exec = Executors.newWorkStealingPool();



        long startTime = System.nanoTime();

        ///SEQUENTIAL
        //teste semi-automático
        /*for(int x = 0; x < quantity; x++){
            simulator.allocateVariable( generator.generateRequest() );
        }*/

        //page e fragmentação (teste manual)
        /*simulator.allocateVariable( new Request(1, 512) );
        simulator.allocateVariable( new Request(2, 388) );
        simulator.allocateVariable( new Request(3, 230) );
        simulator.allocateVariable( new Request(4, 256) );
        simulator.allocateVariable( new Request(5, 530) );*/

        //simulator.loadRequestsFromFile("C:\\dev\\requests_converted.txt");//teste automático



        ///PARALLELISM (PRODUCER-CONSUMER)
        //producer (3 options):
        exec.submit(simulator.readerProducer);
        //exec.submit(simulator.randomProducer(quantity));
        //exec.submit(simulator.testProduder);

        //consumers:
        for (int i = 0; i < simulator.numThreads; i++) {
            exec.submit(simulator.consumer);
        }

        //encerrar
        exec.shutdown();
        try {
            exec.awaitTermination(1, TimeUnit.MINUTES);
        } catch (InterruptedException e){
            Thread.currentThread().interrupt();
        }



        ///REPORT
        long endTime = System.nanoTime();
        double runtimeMS = (endTime - startTime) / 1_000_000.0;

        System.out.println("\nnumero total de requisiçoes atendidas: " + quantity);
        //double AverageRequestSizeB = (double) generator.getTotalRandomSizeB() / quantidade;
        //System.out.println("tamanho medio das variaveis alocadas em bytes: " + AverageRequestSizeB );
        System.out.println("numero total de variaveis removidas da heap: " + totalFreedRequests);
        System.out.println("tempo total de execucao da memoria em MS: " + runtimeMS);
    }

}