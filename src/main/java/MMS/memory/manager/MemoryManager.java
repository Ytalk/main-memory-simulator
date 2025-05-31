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
import java.util.Scanner;

import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Semaphore;
import java.util.concurrent.locks.Condition;

public class MemoryManager {
    private final PhysicalMemory physicalMemory;
    private final PageTable pageTable;
    private final RequestProducerConsumer producerConsumer = new RequestProducerConsumer();
    private final RequestGenerator requestGenerator;

    private final ConcurrentLinkedQueue<Request> readyQueue = new ConcurrentLinkedQueue();
    private final Semaphore lockForFreeding = new Semaphore(1);
    private final Object toMonitor = new Object();

    private static int totalFreedRequests = 0;
    private static int totalCallsToFreeOldest = 0;


    private static Scanner scanner = new Scanner(System.in);
    private static int heapSizeKB = 2;
    private static int pageSizeB = 64;
    private static int quantity = 1000;
    private static int minSize = 4;
    private static int maxSize = 256;
    private static String outputPath = "chart.png";

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


    public void freeOldestRequests() {//Medium-Term Scheduler
        //aproximadamente 30% do tamanho total da heap (de acordo com o número de frames)
        int targetFreed = (int) Math.ceil(physicalMemory.getNumFrames() * 0.3);
        System.out.println("precisa liberar pelo menos " + targetFreed + " frames (30% da heap)");

        totalCallsToFreeOldest++;

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
                System.out.println("desmapeado: pagina " + virtualPage + " -> frame " + physicalFrame);
            }
            System.out.println("liberado " + oldest.getPagesUsedNum() + " frames - variavel " + oldest.getVariableId());
        }
        //System.out.println("\ntotal liberado: " + freedFrames + " frames. | total de variaveis removidas ate agora: " + totalFreedRequests);
    }


    /*public void allocateVariable(Request request) {//Long-Term Scheduler
        int sizeInt = (request.getSizeB() + Integer.BYTES - 1) / Integer.BYTES;
        int pagesNeeded = (sizeInt + pageTable.getPageSizeInt() - 1) / pageTable.getPageSizeInt();


        synchronized (toMonitor) {
            int[] allocatedFrames = physicalMemory.findFreePhysicalFrames(pagesNeeded);


            while (allocatedFrames == null) {

                if (isFreeing) {
                    while(isFreeing) {
                        //espera free
                        try {
                            toMonitor.wait();
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                            System.err.println("Thread interrompida: " + e.getMessage());
                        }
                        allocatedFrames = physicalMemory.findFreePhysicalFrames(pagesNeeded);
                    }
                }
                else {
                    isFreeing = true;
                    System.out.println("\nmemoria insuficiente! liberando...");
                    if (count.get() != 0) {
                        //espera threads à frente
                        try {
                            toMonitor.wait();//ultimo monitor adormecido
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                            System.err.println("Thread interrompida: " + e.getMessage());
                        }
                    }
                    freeOldestRequests();
                    allocatedFrames = physicalMemory.findFreePhysicalFrames(pagesNeeded);
                    if (allocatedFrames == null) {
                        throw new RuntimeException("falha ao alocar mesmo após liberar espaço na memória.");//custom
                    }
                    isFreeing = false;
                    //acorda os esperantes de free
                    toMonitor.notifyAll();
                }
            }


            count.incrementAndGet();
            mapPages(request, allocatedFrames);//encontra pages livres e faz mapeamento com os frames livres
            physicalMemory.writeToHeap(request.getVariableId(), allocatedFrames, sizeInt, pageTable.getPageSizeInt());//escreve nos frames encontrados
            readyQueue.add(request);

            logAllocationRequest(request, sizeInt, pagesNeeded, pageTable.getPageSizeInt());
            physicalMemory.printHeap();
            //System.out.print("\n");
            pageTable.printPageTable();

            count.decrementAndGet();
            if (count.get() == 0) {
                //acorda o ultimo monitor adormecido
                toMonitor.notifyAll();
            }
        }
    }*/


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
                System.out.println("\nmemoria insuficiente! liberando...");
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

        for (int i = 0; i < allocatedFrames.length; i++) {
            System.out.println(freePages[i] + " <- paginas livres encontradas para mapear\n");
            pageTable.map(freePages[i], allocatedFrames[i]);//listas, paginas e frames, utilizados pela requisição
        }

        request.setPagesUsedNum(allocatedFrames.length);
        request.setPagesUsedList(freePages);
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
                    System.out.println("Saindo...");
                    break;
                default:
                    System.out.println("Opção inválida. Tente novamente.");
            }
        } while (option != 5);
        scanner.close();
    }


    private static void showMenu() {
        System.out.println("\n=== Menu do MemoryManager ===");
        System.out.println("1. Configurar parâmetros");
        System.out.println("2. Executar modo sequencial");
        System.out.println("3. Executar modo paralelo");
        System.out.println("4. Comparar sequencial vs paralelo e exportar gráfico");
        System.out.println("5. Sair");
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
        //double AverageRequestSizeB = (double) TotalSizeB / quantity; pegar ambas do file
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
        double runtimeMS = (endTime - startTime) / 1_000_000.0;

        System.out.printf("Tempo Sequencial: %.2f ms\n", runtimeMS);
        System.out.println("numero total de variaveis removidas da heap: " + totalFreedRequests);
        System.out.println("\nnumero total de requisiçoes atendidas: " + quantity);
        double AverageRequestSizeB = (double) simulator.requestGenerator.getTotalRandomSizeB() / quantity;
        System.out.println("tamanho medio das variaveis alocadas em bytes: " + AverageRequestSizeB );
        simulator.reset();
        return runtimeMS;
    }


    private static double runParallelFile(MemoryManager simulator) {
        System.out.println("\n-- Execução Paralela --");

        long startTime = System.nanoTime();
        simulator.producerConsumer.readerProducer("C:\\dev\\requests_converted.txt");
        simulator.producerConsumer.consumer(simulator);
        long endTime = System.nanoTime();

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

        double seqTime = runSequentialFile(simulator);
        double parTime = runParallelFile(simulator);

        System.out.printf("Sequencial: %.2f ms | Paralelo: %.2f ms\n", seqTime, parTime);
        try {
            PerformanceChartGenerator.exportComparisonChart(seqTime, parTime, outputPath);
        } catch (IOException e) {
            System.err.println("Erro ao exportar gráfico: " + e.getMessage());
        }
    }

    public void reset() {
        readyQueue.clear();
        totalFreedRequests = 0;
        totalCallsToFreeOldest = 0;

        physicalMemory.reset();//
        pageTable.reset();///
        producerConsumer.reset();
        requestGenerator.reset();
    }

}