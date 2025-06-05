package MMS.memory.manager;

import MMS.PerformanceChartGenerator;
import MMS.memory.physical.PhysicalMemory;
import MMS.memory.virtual.PageTable;

import MMS.process.Request;
import MMS.process.RequestProducerConsumer;
import MMS.process.RequestGenerator;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.Arrays;
import java.util.Scanner;

import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Semaphore;
import java.util.concurrent.locks.Condition;
import java.util.stream.Collectors;

public class MemoryManager {
    private final PhysicalMemory physicalMemory;
    private final PageTable pageTable;
    private final RequestProducerConsumer producerConsumer = new RequestProducerConsumer();
    private static RequestGenerator requestGenerator;

    private final ConcurrentLinkedQueue<Request> readyQueue = new ConcurrentLinkedQueue();
    private final Semaphore lockForFreeding = new Semaphore(1);
    private final Object toMonitor = new Object();

    private static int totalFreedRequests = 0;
    private static int totalCallsToFreeOldest = 0;


    private static Scanner scanner = new Scanner(System.in);
    private static int heapSizeKB;
    private static int pageSizeB;
    private static int quantity;
    private static int minSize;
    private static int maxSize;

    private MemoryManager(PhysicalMemory physicalMemory, PageTable PageTable,
                          RequestGenerator requestGenerator) {
        this.physicalMemory = physicalMemory;
        this.pageTable = PageTable;
        this.requestGenerator = requestGenerator;
    }

    private void logAllocationRequest(Request req, int ints, int pages, int pageSizeInts) {
        //o espaço utilizado na memória é o tamanho requisição, mas o espaço consumido é maior ou igual (deve ser múltiplo do tamanho da página)
        System.out.printf("\nRequest: ID = %d | %d bytes -> %d ints | %d pages (pageSize = %d ints) | %d fragmented ints\n",
                req.getVariableId(), req.getSizeB(), ints, pages, pageSizeInts, (pages * pageSizeInts - ints));
    }

    //encontra frames livres ou descobre que não há frames/espaço suficiente
    private int[] tryFindFreeFrames(int pagesNeeded) {
        int[] allocatedFrames = physicalMemory.findFreePhysicalFrames(pagesNeeded);
        if (allocatedFrames == null) {
            //System.out.println("\nmemoria insuficiente! liberando...");
            freeOldestRequests();
            allocatedFrames = physicalMemory.findFreePhysicalFrames(pagesNeeded);

            if (allocatedFrames == null) {
                //System.out.println("\nmemoria insuficiente! liberando... (segunda e ultima tentativa)");
                freeOldestRequests();
                allocatedFrames = physicalMemory.findFreePhysicalFrames(pagesNeeded);
                if (allocatedFrames == null) {
                    throw new RuntimeException("falha ao alocar mesmo após liberar espaço na memória.");//custom
                }
            }
        }
        return allocatedFrames;
    }


    public void freeOldestRequests() {//Medium-Term Scheduler
        //aproximadamente 30% do tamanho total da heap (de acordo com o número de frames)
        int targetFreed = (int) Math.ceil(physicalMemory.getNumFrames() * 0.6);
        //System.out.println("precisa liberar pelo menos " + targetFreed + " frames (30% da heap)");

        //totalCallsToFreeOldest++;

        int freedFrames = 0;
        while (freedFrames < targetFreed && !readyQueue.isEmpty()) {//acumula requests até bater o número de frames alvo ou mais (30%+)
            Request oldest = readyQueue.poll();
            freedFrames += oldest.getPagesUsedNum();
            totalFreedRequests++;//total de variáveis removidas da heap

            for (int virtualPage : oldest.getPagesUsedList()) {//desmapeia todas as páginas da requisição e desaloca da memória
                int physicalFrame = pageTable.getPhysicalFrame(virtualPage);

                //libera da memória e tabela, atualiza as mesmas, além de atualizar as filas de livres
                pageTable.unmap(virtualPage);
                physicalMemory.freeFrame(physicalFrame);//atualiza freeFramesQueue com o parametro e zera o heap associado ao frame (numero e tamanho do frame)
                //System.out.println("desmapeado: pagina " + virtualPage + " -> frame " + physicalFrame);
            }
            //System.out.println("liberado " + oldest.getPagesUsedNum() + " frames - variavel " + oldest.getVariableId());
        }
    }


    public void allocateVariable(Request request) {//Long-Term Scheduler
        int sizeInt = (request.getSizeB() + Integer.BYTES - 1) / Integer.BYTES;
        int pagesNeeded = (sizeInt + pageTable.getPageSizeInt() - 1) / pageTable.getPageSizeInt();

        int[] allocatedFrames = physicalMemory.findFreePhysicalFrames(pagesNeeded);
        //happy flow - há espaço na mem (não deve executar junto com freeOldestRequests)
        if (allocatedFrames != null) {
            finishAllocation(request, sizeInt, pagesNeeded, allocatedFrames);
            return;
        }

        //bad flow - não houve espaço, freeOldestRequests [seção crítica], será acionado pelo menos 1 vez
        try {
            lockForFreeding.acquire();
            allocatedFrames = physicalMemory.findFreePhysicalFrames(pagesNeeded);//para threads que chegam depois (liberadas)

            if (allocatedFrames == null) {
                //System.out.println("\nmemoria insuficiente! liberando...");
                freeOldestRequests();
                allocatedFrames = physicalMemory.findFreePhysicalFrames(pagesNeeded);
                if (allocatedFrames == null) {
                    throw new RuntimeException("falha ao alocar mesmo após liberar espaço na memória.");//custom
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            System.err.println("Thread interrompida: " + e.getMessage());
        } finally {
            lockForFreeding.release();
        }
        finishAllocation(request, sizeInt, pagesNeeded, allocatedFrames);
    }


    private void finishAllocation(Request request, int sizeInt, int pagesNeeded, int[] frames) {
        mapPages(request, frames);//encontra pages livres e faz mapeamento com os frames livres
        physicalMemory.writeToHeap(request.getVariableId(), frames, sizeInt);
        readyQueue.add(request);

        /*synchronized (toMonitor) {
            logAllocationRequest(request, sizeInt, pagesNeeded, pageTable.getPageSizeInt());
            physicalMemory.printHeap();
            System.out.println();
            pageTable.printPageTable();
        }*/
    }


    //atualiza pageTable (mapeia páginas -> frames)
    private void mapPages(Request request, int[] allocatedFrames) {
        int[] freePages = pageTable.findFreeVirtualPages(allocatedFrames.length);
        //System.out.println( Arrays.stream(freePages).boxed().collect(Collectors.toList()) + " <- paginas livres encontradas para mapear\n" );

        for (int i = 0; i < allocatedFrames.length; i++) {
            pageTable.map(freePages[i], allocatedFrames[i]);//listas, paginas e frames, utilizados pela requisição
        }

        request.setPagesUsedNum(allocatedFrames.length);
        request.setPagesUsedList(freePages);
    }


    public void loadRequestsFromFile(String filePath) {
        try (BufferedReader reader = new BufferedReader(new FileReader(filePath))) {
            /*String firstLine = reader.readLine();
            if (firstLine != null) {
                String[] header = firstLine.trim().split(",");
                if (header.length == 3) {
                    quantity = Integer.parseInt(header[0].trim());
                    minSize = Integer.parseInt(header[1].trim());
                    maxSize = Integer.parseInt(header[2].trim());
                }
            }*/

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
        MemoryManager simulator = configureParameters();

        int option;
        do {
            showMenu();
            option = readInt("Escolha uma opção: ");
            switch (option) {
                case 0://case teste
                    simulator.allocateVariable( new Request(1, 512) );
                    simulator.allocateVariable( new Request(2, 388) );
                    simulator.allocateVariable( new Request(3, 230) );
                    simulator.allocateVariable( new Request(4, 256) );
                    simulator.allocateVariable( new Request(5, 530) );
                    simulator.reset();
                    break;
                case 1:
                    configureParameters();
                    break;
                case 2:
                    runSequentialFile(simulator);
                    break;
                case 3:
                    runParallelFile(simulator);
                    break;
                case 4:
                    runComparison(simulator);
                    break;
                case 5:
                    runSequentialRandom(simulator);
                    break;
                case 6:
                    runParallelRandom(simulator);
                    break;
                case 7:
                    System.out.println("Saindo...");
                    break;
                default:
                    System.out.println("Opção inválida. Tente novamente.");
            }
        } while (option != 7);
        scanner.close();
    }


    private static void showMenu() {
        System.out.println("\n=== Menu do MemoryManager ===");
        System.out.println("1. Configurar parâmetros");
        System.out.println("2. Executar modo sequencial");
        System.out.println("3. Executar modo paralelo");
        System.out.println("4. Comparar sequencial vs paralelo e exportar gráfico");
        System.out.println("5. Executar modo sequencial random");
        System.out.println("6. Executar modo paralelo random");
        System.out.println("7. Sair");
    }

    private static MemoryManager configureParameters() {
        heapSizeKB = readInt("Tamanho da Heap (KB): ");
        pageSizeB = readInt("Tamanho da Página (Bytes): ");
        quantity = readInt("Quantidade de requests: ");
        minSize = readInt("Tamanho mínimo das requests (Bytes): ");
        maxSize = readInt("Tamanho máximo das requests (Bytes): ");

        PhysicalMemory physicalMemory = new PhysicalMemory(heapSizeKB, pageSizeB);
        PageTable pageTable = new PageTable(heapSizeKB, pageSizeB);
        RequestGenerator requestGenerator = new RequestGenerator(minSize, maxSize);//informa limite de tamanho (B) mínimo e máximo de requests
        return new MemoryManager(physicalMemory, pageTable, requestGenerator);
    }

    private static int readInt(String prompt) {
        while (true) {
            System.out.print(prompt);
            try {
                return Integer.parseInt(scanner.nextLine().trim());
            } catch (NumberFormatException e) {
                System.out.println("Entrada inválida. Digite um número inteiro.");
            }
        }
    }


    private static double runSequentialFile(MemoryManager simulator) {
        System.out.println("\n-- Execução Sequencial --");
        long startTime = System.nanoTime();
        simulator.loadRequestsFromFile("C:\\dev\\requests_converted.txt");
        long endTime = System.nanoTime();
        double runtimeMS = (endTime - startTime) / 1_000_000.0;

        System.out.printf("Tempo Sequencial: %.2f ms\n", runtimeMS);
        System.out.println("numero total de variaveis removidas da heap: " + totalFreedRequests);
        //System.out.println("\nnumero total de requisiçoes atendidas: " + quantity);
        //double AverageRequestSizeB = (double) TotalSizeB / quantity;
        //System.out.println("tamanho medio das variaveis alocadas em bytes: " + AverageRequestSizeB );
        simulator.reset();
        return runtimeMS;
    }

    private static double runSequentialRandom(MemoryManager simulator) {
        System.out.println("\n-- Execução Sequencial Random--");
        long startTime = System.nanoTime();
        for(int x = 0; x < quantity; x++){
            simulator.allocateVariable( simulator.requestGenerator.generateRequest() );
        }
        long endTime = System.nanoTime();
        simulator.producerConsumer.shutdownThreads();

        double runtimeMS = (endTime - startTime) / 1_000_000.0;
        System.out.printf("Tempo Sequencial: %.2f ms\n", runtimeMS);
        System.out.println("numero total de variaveis removidas da heap: " + totalFreedRequests);
        System.out.println("\nnumero total de requisiçoes atendidas: " + quantity);
        double meanRequestsSizeB = (double) simulator.requestGenerator.getTotalRandomSizeB() / quantity;
        System.out.println("tamanho medio das variaveis alocadas em bytes: " + meanRequestsSizeB );
        simulator.reset();
        return runtimeMS;
    }

    private static double runParallelRandom(MemoryManager simulator) {
        System.out.println("\n-- Execução Paralela Random--");

        long startTime = System.nanoTime();
        simulator.producerConsumer.randomProducer(quantity, requestGenerator);
        //simulator.producerConsumer.consumer(simulator);
        long endTime = System.nanoTime();
        simulator.producerConsumer.shutdownThreads();

        double runtimeMS = (endTime - startTime) / 1_000_000.0;
        System.out.printf("Tempo Paralelo: %.2f ms\n", runtimeMS);
        System.out.println("numero total de variaveis removidas da heap: " + totalFreedRequests);
        System.out.println("\nnumero total de requisiçoes atendidas: " + quantity);
        //double AverageRequestSizeB = (double) TotalSizeB / quantity;
        //System.out.println("tamanho medio das variaveis alocadas em bytes: " + AverageRequestSizeB );
        simulator.reset();
        return runtimeMS;
    }

    private static double runParallelFile(MemoryManager simulator) {
        System.out.println("\n-- Execução Paralela --");

        CountDownLatch latch = new CountDownLatch(1 + simulator.producerConsumer.getNumThreads());

        simulator.producerConsumer.readerProducer("C:\\dev\\requests_converted.txt", latch);
        simulator.producerConsumer.consumer(simulator, latch);
        long startTime = System.nanoTime();

        try {
            latch.await();//espera terminarem
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        long endTime = System.nanoTime();

        simulator.producerConsumer.shutdownThreads();
        double runtimeMS = (endTime - startTime) / 1_000_000.0;
        System.out.printf("Tempo Paralelo: %.2f ms\n", runtimeMS);

        System.out.println("numero total de variaveis removidas da heap: " + totalFreedRequests);
        //System.out.println("\nnumero total de requisiçoes atendidas: " + quantity);
        //double AverageRequestSizeB = (double) TotalSizeB / quantity; pegar ambas do file
        //System.out.println("tamanho medio das variaveis alocadas em bytes: " + AverageRequestSizeB );
        simulator.reset();
        return runtimeMS;
    }


    private static void runComparison(MemoryManager simulator) {
        System.out.println("\n-- Comparação e Exportação de Gráfico --");
        int executions = 10;
        double[] seqTimes = new double[executions];
        double[] parTimes = new double[executions];

        for(int x = 0; x < executions; x++){
            System.out.println("\nexecução " + x);
            seqTimes[x] = runSequentialFile(simulator);
            parTimes[x] = runParallelFile(simulator);
        }

        double seqMeanTime = Arrays.stream(seqTimes).average().orElse(0);
        double parMeanTime = Arrays.stream(parTimes).average().orElse(0);
        //seqMeanTime /= executions;
        //parMeanTime /= executions;

        System.out.printf("Sequencial: %.2f ms | Paralelo: %.2f ms\n", seqMeanTime, parMeanTime);
        try {
            PerformanceChartGenerator.exportComparisonChart(seqMeanTime, parMeanTime, "bar.png");
            PerformanceChartGenerator.exportBoxPlot(seqTimes, parTimes, "boxplot.png");
        } catch (IOException e) {
            System.err.println("Erro ao exportar gráfico: " + e.getMessage());
        }
    }

    public void reset() {
        readyQueue.clear();
        totalFreedRequests = 0;
        //totalCallsToFreeOldest = 0;

        physicalMemory.reset();//
        pageTable.reset();///
        producerConsumer.reset();
        requestGenerator.reset();
    }

}