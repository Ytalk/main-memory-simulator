package MMS.concurrency;

import MMS.memory.manager.MemoryManager;
import MMS.process.Request;
import MMS.process.RequestGenerator;

import java.io.BufferedReader;
import java.io.FileReader;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.CountDownLatch;

public class RequestProducerConsumer {
    private BlockingQueue<Request> jobQueue;//buffer
    private int numThreads;
    private ExecutorService exec;
    //private final ExecutorService exec = Executors.newWorkStealingPool();

    public RequestProducerConsumer() {
        init();
    }

    public int getNumThreads(){
        return numThreads;
    }

    public void init() {
        numThreads = Runtime.getRuntime().availableProcessors() * 2;//x threads por processador
        exec = Executors.newFixedThreadPool(numThreads);
        jobQueue = new LinkedBlockingQueue<>();
    }

    public void shutdownThreads() {
        exec.shutdown();
        try {
            if (!exec.awaitTermination(5, TimeUnit.SECONDS)) {
                exec.shutdownNow();
            }
        } catch (InterruptedException e) {
            exec.shutdownNow();
        }
    }


    //indica aos consumidores que o produtor terminou. quando um consumidor (thread) pega uma poison pill, ele encerra sua execução
    private void addPoisonPills() {
        for (int i = 0; i < numThreads; i++) {
            try {
                jobQueue.put(new Request(-1, -1));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    public void readerProducer(String filePath, CountDownLatch latch, MemoryManager simulator) {
        exec.submit(() -> {
            try (BufferedReader reader = new BufferedReader(new FileReader(filePath))) {

                String firstLine = reader.readLine();
                if (firstLine != null) {
                    String[] header = firstLine.trim().split(",");
                    if (header.length == 2) {
                        simulator.getConsole().setQuantity( Integer.parseInt(header[0].trim()) );
                        simulator.setMeanRequestsSizeB( Double.parseDouble(header[1].trim()) );
                    }
                }

                String line;
                while ((line = reader.readLine()) != null) {
                    line = line.trim();

                    if (line.isEmpty()) continue;
                    String[] parts = line.split(",");
                    if (parts.length >= 2) {
                        int a = Integer.parseInt(parts[0].trim());
                        int b = Integer.parseInt(parts[1].trim());
                        jobQueue.put(new Request(a, b));
                    }

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
        exec.submit(() -> {
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
        exec.submit(() -> {
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
        for (int i = 0; i < numThreads; i++) {
            exec.submit(() -> {
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