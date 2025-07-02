package MMS.memory.physical;

import com.google.common.util.concurrent.Striped;

import java.util.Arrays;
import java.util.concurrent.ConcurrentLinkedQueue;

public class PhysicalMemory {
    private Frame[] frames;
    private final int frameSizeB;
    private final int frameSizeInt;
    private final int numFrames;

    private int[] heap;
    private final int heapSizeKB;
    private final int heapSizeB;
    private final int heapSizeInt;

    private final ConcurrentLinkedQueue<Integer> freeFramesQueue = new ConcurrentLinkedQueue<>();

    public PhysicalMemory(int heapSizeKB, int frameSizeB) {//heap e page
        this.heapSizeKB = heapSizeKB;
        this.heapSizeB = heapSizeKB * 1024;
        this.heapSizeInt = heapSizeB / 4;
        this.heap = new int[heapSizeInt];

        this.frameSizeB = frameSizeB;
        this.frameSizeInt = frameSizeB / 4;
        this.numFrames = heapSizeB / frameSizeB;
        this.frames = new Frame[numFrames];

        //this.frameLocks = Striped.lock(numFrames);
        initializeFrames();
    }

    private void initializeFrames() {
        for (int i = 0; i < numFrames; i++) {
            frames[i] = new Frame(i);
            freeFramesQueue.add(i);
        }
    }

    //para alocar frames (contíguos ou espalhados)
    public int[] findFreePhysicalFrames(int pagesNeeded) {
        int[] freeFramesFound = new int[pagesNeeded];

        //lock.lock();
        //try {
            for (int i = 0; i < pagesNeeded; i++) {
                Integer frame = freeFramesQueue.poll();

                if (frame == null) {
                    //rollback dos frames já alocados
                    for (int j = 0; j < i; j++) {
                        freeFramesQueue.add(freeFramesFound[j]);
                    }
                    return null;
                }

                freeFramesFound[i] = frame;
            }
            return freeFramesFound;
        //} finally { lock.unlock(); }
    }

    //batch write com Arrays
    public void writeToHeap(int variableId, int[] frames, int intsNeeded) {
        int remaining = intsNeeded;
        for (int frameIndex : frames) {
            int toWrite = Math.min(remaining, frameSizeInt);//vai até uma parte (fragmentação) ou final do frame
            int start = frameIndex * frameSizeInt;
            int end = start + toWrite;

            //Lock lock = frameLocks.get(frameIndex);
            //lock.lock();
            //try {
                Arrays.fill(heap, start, end, variableId);
            //} finally { lock.unlock(); }
            remaining -= toWrite;
        }
    }

    //limpa região da heap associado a esse frame (IDEIA [em média trade-off caro]: abordagem hybrid para não sobreescrever 0 [fragmentação])
    public void freeFrame(int frame) {
        freeFramesQueue.add(frame);
        //Lock lock = frameLocks.get(frame);
        //lock.lock();
        //try {
            //(page 16int) = 0...15 (16num); 16...31 (16num); 32...47 (16 num).... end = start + frameSizeInt
            Arrays.fill(heap, frame * frameSizeInt, (frame + 1) * frameSizeInt, 0);
        /*} finally {
            lock.unlock();
        }*/
    }


    public Frame getFrame(int frameIndex) {
        return frames[frameIndex];
    }

    public int getNumFrames(){
        return numFrames;
    }

    public int getFrameSizeB() {
        return frameSizeB;
    }

    public int getHeapSizeB() {
        return heapSizeB;
    }

    public void printHeap() {
        for (int i = 0; i < heapSizeInt; i++) {
            System.out.println("index: " + i + " | ID da variavel(0 se nao houver): " + heap[i]);
        }
    }

    public void reset(){
        freeFramesQueue.clear();
        Arrays.fill(heap, 0);
        initializeFrames();
    }

}