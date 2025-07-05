package MMS.concurrency;

import MMS.memory.manager.MemoryManager;
import MMS.process.Request;
import MMS.process.RequestGenerator;

import java.io.BufferedReader;
import java.io.FileReader;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.CountDownLatch;

public class RequestProducerConsumer {
    private BlockingQueue<Request> jobQueue;//buffer
    private int numConsumers;
    private ExecutorService consumerExec;
    private ExecutorService producerExec;
    private static final Request POISON_PILL = new Request(-1, -1);

    public RequestProducerConsumer() {
        init();
    }

    public int getNumConsumers(){
        return numConsumers;
    }

    public void init() {
        numConsumers = Runtime.getRuntime().availableProcessors() + 1;//threads em relação ao processador
        producerExec = Executors.newSingleThreadExecutor();
        consumerExec = Executors.newFixedThreadPool(numConsumers);
        jobQueue = new LinkedBlockingQueue<>();
    }

    public void shutdownThreads() {
        producerExec.shutdown();
        try {
            if (!producerExec.awaitTermination(5, TimeUnit.SECONDS)) {
                producerExec.shutdownNow();
            }
        } catch (InterruptedException e) {
            producerExec.shutdownNow();
            Thread.currentThread().interrupt();
        }

        consumerExec.shutdown();
        try {
            if (!consumerExec.awaitTermination(5, TimeUnit.SECONDS)) {
                consumerExec.shutdownNow();
            }
        } catch (InterruptedException e) {
            consumerExec.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }


    //indica aos consumidores que o produtor terminou. quando um consumidor (thread) pega uma poison pill, ele encerra sua execução
    private void addPoisonPills() {
        for (int i = 0; i < numConsumers; i++) {
            try {
                jobQueue.put(POISON_PILL);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    public void readerProducer(String filePath, CountDownLatch latch, MemoryManager simulator) {
        Path path = Paths.get(filePath);
        producerExec.submit(() -> {
            try (BufferedReader reader = new BufferedReader( new FileReader(path.toFile()) )) {

                String header  = reader.readLine();
                if (header  != null) {
                    int comma = header.indexOf(',');
                    simulator.getConsole().setQuantity( Integer.parseInt( header.substring(0, comma).trim()) );
                    simulator.setMeanRequestsSizeB(Double.parseDouble( header.substring(comma + 1).trim()) );
                }

                String line;
                while ((line = reader.readLine()) != null) {
                    line = line.trim();
                    if (line.isEmpty()) continue;

                    int comma = line.indexOf(',');
                    int id = Integer.parseInt(line.substring(0, comma));
                    int size = Integer.parseInt(line.substring(comma + 1).trim());

                    jobQueue.put(new Request(id, size));
                }
            } catch (Exception e) {
                e.printStackTrace();
            } finally {//sempre entra em finally para sinalizar fim, inserindo poison pills
                addPoisonPills();
                latch.countDown();
            }
        });
    }

    public void randomProducer(int quantity, RequestGenerator generator, CountDownLatch latch) {
        producerExec.submit(() -> {
            try {
                for (int x = 0; x < quantity; x++) {
                    jobQueue.put(generator.generateRequest());
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                System.err.println("Thread interrompida: " + e.getMessage());
            } finally {
                addPoisonPills();
                latch.countDown();
            }
        });
    }

    public void testProducer() {
        producerExec.submit(() -> {
            try {
                jobQueue.put(new Request(1, 512));
                jobQueue.put(new Request(2, 388));
                jobQueue.put(new Request(3, 230));
                jobQueue.put(new Request(4, 256));
                jobQueue.put(new Request(5, 530));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                System.err.println("Thread interrompida: " + e.getMessage());
            } finally {
                addPoisonPills();
            }
        });
    }


    public void consumer(MemoryManager simulator, CountDownLatch latch) {
        for (int i = 0; i < numConsumers; i++) {
            consumerExec.submit(() -> {
                try {
                    while (true) {
                        Request req = jobQueue.take();
                        if (req.getSizeB() == -1) return;
                        simulator.allocateVariable(req);
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    latch.countDown();//sinaliza termino
                }
            });
        }
    }

}