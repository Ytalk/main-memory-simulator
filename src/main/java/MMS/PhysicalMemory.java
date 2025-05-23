package MMS;

import com.google.common.util.concurrent.Striped;

import java.util.Arrays;
import java.util.BitSet;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.locks.Lock;


public class PhysicalMemory {
    private Frame[] frames;
    private final int frameSizeB;
    private final int frameSizeInt;
    private final int numFrames;

    private int[] heap;
    private final int heapSizeKB;
    private final int heapSizeB;
    private final int heapSizeInt;

    //private final BitSet freeFrames;
    private final ConcurrentLinkedQueue<Integer> freeFramesQueue = new ConcurrentLinkedQueue<>();
    private final Striped<Lock> frameLocks;

    public PhysicalMemory(int heapSizeKB, int frameSizeB) {//heap e page
        this.heapSizeKB = heapSizeKB;
        this.heapSizeB = heapSizeKB * 1024;
        this.heapSizeInt = heapSizeB / 4;
        this.heap = new int[heapSizeInt];

        this.frameSizeB = frameSizeB;
        this.frameSizeInt = frameSizeB / 4;
        this.numFrames = heapSizeB / frameSizeB;
        this.frames = new Frame[numFrames];

        //this.freeFrames = new BitSet(numFrames);
        this.frameLocks = Striped.lock(numFrames);
        initializeFrames();
    }

    private void initializeFrames() {
        //freeFrames.set(0, numFrames);
        for (int i = 0; i < numFrames; i++) {
            frames[i] = new Frame(i);
            freeFramesQueue.add(i);
        }
    }

    //para alocar frames (contíguos ou espalhados)
    /*public int[] findFreePhysicalFrames(int pagesNeeded) {
        allocationLock.lock();
        try {
            int[] freeFramesFound = new int[pagesNeeded];
            int found = 0;

            //first-fit in non-contiguous allocation
            int start = freeFrames.nextSetBit(0);
            while (start != -1 && found < pagesNeeded) {
                freeFramesFound[found++] = start;
                freeFrames.clear(start);
                start = freeFrames.nextSetBit(start + 1);
            }

            if (found == pagesNeeded) return freeFramesFound;

            //rollback if not enough frames
            for (int i = 0; i < found; i++) {
                freeFrames.set(freeFramesFound[i]);
            }
            return null;
        } finally {
            allocationLock.unlock();
        }
    }*/
    public int[] findFreePhysicalFrames(int pagesNeeded) {
        int[] freeFramesFound = new int[pagesNeeded];
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
    }

    //batch write com Arrays
    public void writeToHeap(int variableId, int[] frames, int intsNeeded, int pageSizeInts) {
        int remaining = intsNeeded;
        for (int frameIndex : frames) {
            int toWrite = Math.min(remaining, pageSizeInts);

            Lock lock = frameLocks.get(frameIndex);
            lock.lock();
            try {
                int start = frameIndex * frameSizeInt;
                //vai até uma parte (fragmentação) ou final do frame
                int end = Math.min(start + toWrite, start + frameSizeInt);
                Arrays.fill(heap, start, end, variableId);
            } finally {
                lock.unlock();
            }

            remaining -= toWrite;
        }
    }

    //limpa heap associado a esse frame (abordagem hybrid para não sobreescrever 0 [fragmentação]?)
    public void freeFrame(int frame) {
        //(frame >= 0 && frame < numFrames)
        freeFramesQueue.add(frame);
        Lock lock = frameLocks.get(frame);
        lock.lock();
        try {
            //freeFrames.set(frame);
            //(page 16int) = 0...15 (16num); 16...31 (16num); 32...47 (16 num).... end = start + frameSizeInt
            Arrays.fill(heap, frame * frameSizeInt, (frame + 1) * frameSizeInt, 0);
        } finally {
            lock.unlock();
        }
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
}