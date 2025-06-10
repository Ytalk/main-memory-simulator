package MMS.memory.manager;

import MMS.ConsoleUI;
import MMS.Scheduler;
import MMS.memory.physical.PhysicalMemory;
import MMS.memory.virtual.PageTable;

import MMS.process.Request;
import MMS.process.RequestGenerator;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;

import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Semaphore;

public class MemoryManager {
    private final PhysicalMemory physicalMemory;
    private final PageTable pageTable;
    private static RequestGenerator requestGenerator;

    private static ConsoleUI console = new ConsoleUI();
    private static Scheduler scheduler = new Scheduler();

    private final ConcurrentLinkedQueue<Request> readyQueue = new ConcurrentLinkedQueue();
    private final Semaphore lockForFreeding = new Semaphore(1);
    private final Object toMonitor = new Object();

    private static int totalFreedRequests = 0;
    private static int totalCallsToFreeOldest = 0;


    private MemoryManager(PhysicalMemory physicalMemory, PageTable PageTable, RequestGenerator requestGenerator) {
        this.physicalMemory = physicalMemory;
        this.pageTable = PageTable;
        this.requestGenerator = requestGenerator;
    }


    private void logAllocationRequest(Request req, int ints, int pages, int pageSizeInts) {
        //o espaço utilizado na memória é o tamanho requisição, mas o espaço consumido é maior ou igual (deve ser múltiplo do tamanho da página)
        System.out.printf("\nRequest: ID = %d | %d bytes -> %d ints | %d pages (pageSize = %d ints) | %d fragmented ints\n",
                req.getVariableId(), req.getSizeB(), ints, pages, pageSizeInts, (pages * pageSizeInts - ints));
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

        synchronized (toMonitor) {
            logAllocationRequest(request, sizeInt, pagesNeeded, pageTable.getPageSizeInt());
            physicalMemory.printHeap();
            System.out.println();
            pageTable.printPageTable();
        }
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
        console.configureParameters();

        PhysicalMemory physicalMemory = new PhysicalMemory(console.getHeapSizeKB(), console.getPageSizeB());
        PageTable pageTable = new PageTable(console.getHeapSizeKB(), console.getPageSizeB());
        RequestGenerator requestGenerator = new RequestGenerator(console.getMinSize(), console.getMaxSize());//informa limite de tamanho (B) mínimo e máximo de requests
        MemoryManager simulator = new MemoryManager(physicalMemory, pageTable, requestGenerator);


        int option;
        do {
            console.showMenu();
            option = console.readInt("Escolha uma opção: ");
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
                    console.configureParameters();
                    physicalMemory = new PhysicalMemory(console.getHeapSizeKB(), console.getPageSizeB());
                    pageTable = new PageTable(console.getHeapSizeKB(), console.getPageSizeB());
                    //RequestGenerator requestGenerator = new RequestGenerator(minSize, maxSize);//informa limite de tamanho (B) mínimo e máximo de requests
                    simulator = new MemoryManager(physicalMemory, pageTable, requestGenerator);
                    break;
                case 2:
                    scheduler.runSequentialFile(simulator);
                    break;
                case 3:
                    scheduler.runParallelFile(simulator);
                    break;
                case 4:
                    scheduler.runComparison(simulator);
                    break;
                case 5:
                    scheduler.runSequentialRandom(simulator);
                    break;
                case 6:
                    scheduler.runParallelRandom(simulator);
                    simulator.report();
                    simulator.reset();
                    break;
                case 7:
                    System.out.println("Saindo...");
                    break;
                default:
                    System.out.println("Opção inválida. Tente novamente.");
            }
        } while (option != 7);
        console.closeScanner();
    }


    public void report() {
        System.out.println("numero total de variaveis removidas da heap: " + totalFreedRequests);
        System.out.println("\nnumero total de requisiçoes atendidas: " + console.getQuantity());
        //double meanRequestsSizeB = (double) simulator.requestGenerator.getTotalRandomSizeB() / quantity;//alternativas
        //System.out.println("tamanho medio das variaveis alocadas em bytes: " + meanRequestsSizeB );
    }

    public void reset() {
        readyQueue.clear();
        totalFreedRequests = 0;
        //totalCallsToFreeOldest = 0;

        physicalMemory.reset();//
        pageTable.reset();///
        requestGenerator.reset();
        scheduler.reset();
    }

    public static int getQuantity() {
        return console.getQuantity();
    }

    public static RequestGenerator getRequestGenerator() {
        return requestGenerator;
    }
}