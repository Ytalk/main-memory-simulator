package MMS;

import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;

public class RequestGenerator {
    private final int minSizeB;
    private final int maxSizeB;
    private final AtomicInteger idGenerator = new AtomicInteger(1);;

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


    private int generateRandomSize(){
        return ThreadLocalRandom.current().nextInt(minSizeB, maxSizeB + 1);
    }

}