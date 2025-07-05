package MMS.utils;

import MMS.memory.manager.MemoryManager;
import MMS.concurrency.RequestProducerConsumer;

import java.io.IOException;
import java.util.Arrays;
import java.util.concurrent.CountDownLatch;

public class TimingRuns {
    private final RequestProducerConsumer producerConsumer = new RequestProducerConsumer();

    public double runSequentialFile(MemoryManager simulator, String filePath) {
        System.out.println("\n-- Execução Sequencial (file) --");
        long startTime = System.nanoTime();
        simulator.loadRequestsFromFile(filePath);
        long endTime = System.nanoTime();

        double runtimeMS = (endTime - startTime) / 1_000_000.0;
        System.out.printf("Tempo Sequencial (file): %.2f ms\n", runtimeMS);
        simulator.report();
        simulator.reset();
        return runtimeMS;
    }

    public double runSequentialRandom(MemoryManager simulator) {
        System.out.println("\n-- Execução Sequencial (random) --");
        long startTime = System.nanoTime();
        for(int x = 0; x < simulator.getQuantity(); x++){
            simulator.allocateVariable( simulator.getRequestGenerator().generateRequest() );
        }
        long endTime = System.nanoTime();

        double runtimeMS = (endTime - startTime) / 1_000_000.0;
        System.out.printf("Tempo Sequencial (random): %.2f ms\n", runtimeMS);
        simulator.updateMeanRequestsSizeB();
        simulator.report();
        simulator.reset();
        return runtimeMS;
    }


    public double runParallelRandom(MemoryManager simulator) {
        System.out.println("\n-- Execução Paralela (random) --");
        CountDownLatch latch = new CountDownLatch(1 + producerConsumer.getNumConsumers());//encerrar produtor e consumidores
        producerConsumer.randomProducer(simulator.getQuantity(), simulator.getRequestGenerator(), latch);
        producerConsumer.consumer(simulator, latch);

        long startTime = System.nanoTime();
        try {
            latch.await();//espera terminarem
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        long endTime = System.nanoTime();

        producerConsumer.shutdownThreads();
        double runtimeMS = (endTime - startTime) / 1_000_000.0;
        System.out.printf("Tempo Paralelo (random): %.2f ms\n", runtimeMS);
        simulator.updateMeanRequestsSizeB();
        simulator.report();
        simulator.reset();
        return runtimeMS;
    }

    public double runParallelFile(MemoryManager simulator, String filePath) {
        System.out.println("\n-- Execução Paralela (file) --");
        CountDownLatch latch = new CountDownLatch(1 + producerConsumer.getNumConsumers());//encerrar produtor + consumidores
        producerConsumer.readerProducer(filePath, latch, simulator);
        producerConsumer.consumer(simulator, latch);

        long startTime = System.nanoTime();
        try {
            latch.await();//espera terminarem
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        long endTime = System.nanoTime();

        producerConsumer.shutdownThreads();
        double runtimeMS = (endTime - startTime) / 1_000_000.0;
        System.out.printf("Tempo Paralelo (file): %.2f ms\n", runtimeMS);
        simulator.report();
        simulator.reset();
        return runtimeMS;
    }


    public void runComparison(MemoryManager simulator, String filePath) {
        System.out.println("\n-- Comparação e Exportação de Gráfico --");
        final int EXECUTIONS = 15;
        double[] seqTimes = new double[EXECUTIONS];
        double[] parTimes = new double[EXECUTIONS];

        for(int x = 0; x < EXECUTIONS; x++){
            System.out.println("\nexecução " + (x+1));
            seqTimes[x] = runSequentialFile(simulator, filePath);
            parTimes[x] = runParallelFile(simulator, filePath);
        }

        double seqMeanTime = Arrays.stream(seqTimes).average().orElse(0);
        double parMeanTime = Arrays.stream(parTimes).average().orElse(0);

        System.out.printf("Sequencial: %.2f ms | Paralelo: %.2f ms\n", seqMeanTime, parMeanTime);
        try {
            PerformanceChartExporter.exportBarChart(seqMeanTime, parMeanTime, "barChart.png");
            PerformanceChartExporter.exportBoxPlot(seqTimes, parTimes, "boxplot.png");
            PerformanceChartExporter.exportExecutionTimesBarChart(seqTimes, parTimes, "execTimesBarChart.png");
            System.out.println("Gráficos exportados com sucesso: barChart.png e boxplot.png e execTimesBarChart.png");
        } catch (IOException e) {
            System.err.println("Erro ao exportar gráfico: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public void reset(){
        producerConsumer.init();
    }



    public void runTest(MemoryManager simulator) {
        System.out.println("\n-- Execução paralela (test) --");
        CountDownLatch latch = new CountDownLatch(1 + producerConsumer.getNumConsumers());//encerrar produtor + consumidores
        producerConsumer.testProducer(latch);
        producerConsumer.consumer(simulator, latch);

        long startTime = System.nanoTime();
        try {
            latch.await();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        long endTime = System.nanoTime();

        producerConsumer.shutdownThreads();
        double runtimeMS = (endTime - startTime) / 1_000_000.0;
        System.out.printf("Tempo Sequencial (file): %.2f ms\n", runtimeMS);
        simulator.report();
        simulator.reset();
    }

}