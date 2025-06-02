package MMS.process;

import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

public class RequestGenerator {
    private final int minSizeB;
    private final int maxSizeB;
    private final AtomicInteger idGenerator = new AtomicInteger(1);
    private final AtomicLong totalRandomSizeB = new AtomicLong();

    public RequestGenerator(int minSizeB, int maxSizeB){
        if (minSizeB <= 0 || minSizeB >= maxSizeB) {
            throw new IllegalArgumentException("o tamanho mínimo deve ser positivo e menor que o máximo!");
        }

        this.minSizeB = minSizeB;
        this.maxSizeB = maxSizeB;
    }

    public Request generateRequest(){
        return new Request( idGenerator.getAndIncrement(), generateRandomSize() );
    }

    public long getTotalRandomSizeB(){
        return totalRandomSizeB.get();
    }

    private int generateRandomSize(){
        int RandomSizeB = ThreadLocalRandom.current().nextInt(minSizeB, maxSizeB + 1);
        totalRandomSizeB.addAndGet(RandomSizeB);
        return RandomSizeB;
    }

    public void reset(){
        idGenerator.set(1);
        totalRandomSizeB.set(0);
    }
}