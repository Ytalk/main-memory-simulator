package MMS.memory.manager;

import MMS.utils.ConsoleUI;
import MMS.utils.TimingRuns;
import MMS.memory.physical.PhysicalMemory;
import MMS.memory.virtual.PageTable;

import MMS.process.Request;
import MMS.process.RequestGenerator;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;

import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

public class MemoryManager {
    private final PhysicalMemory physicalMemory;
    private final PageTable pageTable;
    private static RequestGenerator requestGenerator;

    private static ConsoleUI console = new ConsoleUI();
    private static TimingRuns timing = new TimingRuns();


    private final AtomicBoolean freeingInProgress = new AtomicBoolean(false);
    private final AtomicInteger waitingThreads    = new AtomicInteger(0);
    private final Semaphore wakeupSemaphore       = new Semaphore(0);
    private int numSleepers = Runtime.getRuntime().availableProcessors();//consumers - 1

    private final Object toMonitor = new Object();
    private final ConcurrentLinkedQueue<Request> readyQueue = new ConcurrentLinkedQueue();
    private final Semaphore lockForReplacement = new Semaphore(1);


    private static int totalFreedRequests = 0;
    private static int totalCallsToFreeOldest = 0;
    private final AtomicInteger totalFragmentedInt = new AtomicInteger(0);
    private static double meanRequestsSizeB = 0;

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


    public void fifoPageReplacement() {//Medium-Term Scheduler
        //int targetFreed = (int) Math.ceil(physicalMemory.getNumFrames() * 0.6);//aproximadamente 60% do tamanho total da heap (de acordo com o número de frames)
        int targetFreed = (physicalMemory.getNumFrames() * 6 + 9) / 10;
        //System.out.println("precisa liberar pelo menos " + targetFreed + " frames (30% da heap)");

        //totalCallsToFreeOldest++;
        int freedFrames = 0;
        while (freedFrames < targetFreed) {//acumula requests até bater o número de frames alvo ou mais (30%+) // && !readyQueue.isEmpty()
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
        int sizeInt = (request.getSizeB() + 3) / 4;//getSizeB / 4 = floor //getSizeB + BYTES_PER_INT - 1) / BYTES_PER_INT = legibilidade
        int pagesNeeded = (sizeInt + pageTable.getPageSizeInt() - 1) / pageTable.getPageSizeInt();

        int fragmentedInt = ( pagesNeeded * pageTable.getPageSizeInt() ) - sizeInt;
        totalFragmentedInt.addAndGet(fragmentedInt);

        int[] allocatedFrames = physicalMemory.findFreePhysicalFrames(pagesNeeded);

        //happy flow - há espaço na mem (não deve executar junto com freeOldestRequests)
        if (allocatedFrames != null) {
            finishAllocation(request, sizeInt, pagesNeeded, allocatedFrames);
            return;
        }

        //bad flow - não houve espaço, fifoPageReplacement [seção crítica], será acionado pelo menos 1 vez
        // 2) bad flow: quem ganhar a flag faz a liberação
        if (freeingInProgress.compareAndSet(false, true)) {
            try {
                fifoPageReplacement();
            } finally {
                // libera as outras threads
                freeingInProgress.set(false);
                //int n = waitingThreads.getAndSet(0);
                //wakeupSemaphore.release(n);
                wakeupSemaphore.release(numSleepers);
            }
        } else {
            // quem não liberou fica esperando
            //waitingThreads.incrementAndGet();
            try {
                wakeupSemaphore.acquire();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException("Thread interrompida aguardando liberação", e);
            }
        }


        // 3) todas as threads (inclusive quem não liberou) tentam de novo
        allocatedFrames = physicalMemory.findFreePhysicalFrames(pagesNeeded);
        if (allocatedFrames == null) {
            throw new RuntimeException("falha ao alocar mesmo após liberar espaço na memória.");//criar custom
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
        try (BufferedReader reader = new BufferedReader( new FileReader(filePath), 8192 * 4 )) {
            String firstLine = reader.readLine();
            if (firstLine != null) {
                String[] header = firstLine.split(",", 2);
                console.setQuantity( Integer.parseInt( header[0] ) );
                meanRequestsSizeB = Double.parseDouble( header[1] );
            }

            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isEmpty()) continue;

                int sep = line.indexOf(',');
                int id = Integer.parseInt(line, 0, sep, 10);
                int size = Integer.parseInt(line, sep + 1, line.length(), 10);

                allocateVariable(new Request(id, size));
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

        String filePath;
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
                    filePath = console.selectRequestFile();
                    timing.runSequentialFile(simulator, filePath);
                    break;
                case 3:
                    filePath = console.selectRequestFile();
                    timing.runParallelFile(simulator, filePath);
                    break;
                case 4:
                    filePath = console.selectRequestFile();
                    timing.runComparison(simulator, filePath);
                    break;
                case 5:
                    timing.runSequentialRandom(simulator);
                    break;
                case 6:
                    timing.runParallelRandom(simulator);
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
        System.out.println("numero total de requisiçoes atendidas: " + console.getQuantity());
        System.out.println("tamanho medio das variaveis alocadas em bytes: " + meanRequestsSizeB );
        System.out.println("total de fragmentação interna ocorrida na heap (int): " + totalFragmentedInt);
    }

    public void reset() {
        readyQueue.clear();
        totalFreedRequests = 0;
        //totalCallsToFreeOldest = 0;
        totalFragmentedInt.set(0);

        physicalMemory.reset();//
        pageTable.reset();///
        requestGenerator.reset();
        timing.reset();
    }

    public static int getQuantity() {
        return console.getQuantity();
    }

    public ConsoleUI getConsole(){
        return console;
    }

    public void setMeanRequestsSizeB(double mean){
        meanRequestsSizeB = mean;
    }

    public void updateMeanRequestsSizeB(){
        meanRequestsSizeB = (double) requestGenerator.getTotalRandomSizeB() / console.getQuantity();
    }

    public static RequestGenerator getRequestGenerator() {
        return requestGenerator;
    }



    //getters para strategy e fila
    public PhysicalMemory getPhysicalMemory() { return physicalMemory; }
    public PageTable getPageTable() { return pageTable; }
    public ConcurrentLinkedQueue<Request> getReadyQueue() { return readyQueue; }
    public void incrementFreedCount() { totalFreedRequests++; }
}