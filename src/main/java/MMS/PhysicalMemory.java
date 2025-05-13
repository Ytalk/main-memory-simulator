package MMS;

import com.google.common.util.concurrent.Striped;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.List;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;


public class PhysicalMemory {
    private Frame[] frames;
    private final int frameSizeB;
    private final int frameSizeInt;
    private final int numFrames;

    private int[] heap;
    private final int heapSizeKB;
    private final int heapSizeB;
    private final int heapSizeInt;

    private final BitSet freeFrames;

    private final Striped<Lock> frameLocks;
    private final Lock allocationLock = new ReentrantLock();

    public PhysicalMemory(int heapSizeKB, int frameSizeB) {//heap e page
        this.heapSizeKB = heapSizeKB;
        this.heapSizeB = heapSizeKB * 1024;
        this.heapSizeInt = heapSizeB / 4;
        this.heap = new int[heapSizeInt];

        this.frameSizeB = frameSizeB;
        this.frameSizeInt = frameSizeB / 4;
        this.numFrames = heapSizeB / frameSizeB;
        this.frames = new Frame[numFrames];

        this.freeFrames = new BitSet(numFrames);

        this.frameLocks = Striped.lock(numFrames);

        initializeFrames();
    }

    private void initializeFrames() {
        freeFrames.set(0, numFrames);
        for (int i = 0; i < numFrames; i++) {
            frames[i] = new Frame(i);
        }
    }

    /*private void initializeFrames() {
        for (int i = 0; i < numFrames; i++) {
            frames[i] = new Frame(i, false);
        }
    }*/


    //aloca frames contíguos ou espalhados
    /*public int[] allocateFrames(int pagesNeeded) {
        List<Integer> allocatedFrames = new ArrayList<>();

        //busca qualquer frame livre
        //if (allocatedFrames.size() < pagesNeeded) {
            for (int i = 0; i < numFrames && allocatedFrames.size() < pagesNeeded; i++) {
                ///System.out.println("frame " + i);
                ///System.out.println("alocado? " + frames[i].isAllocated());
                if (!frames[i].isAllocated()) {
                    allocatedFrames.add(i);
                    frames[i].setAllocated(true);
                    ///System.out.println("entrou");
                }
            }
        //}

        if (allocatedFrames.size() < pagesNeeded) {
            for (int frame : allocatedFrames) {//libera frames que alocou
                frames[frame].setAllocated(false);
            }
            return null;//não há frames suficientes
        }

        return allocatedFrames.stream().mapToInt(i -> i).toArray();
    }*/

    public int[] allocateFrames(int pagesNeeded) {
        allocationLock.lock();
        try {
            int[] allocated = new int[pagesNeeded];
            int found = 0;

            //first-fit in non-contiguous allocation
            int start = freeFrames.nextSetBit(0);
            while (start != -1 && found < pagesNeeded) {
                allocated[found++] = start;
                freeFrames.clear(start);
                start = freeFrames.nextSetBit(start + 1);
            }

            if (found == pagesNeeded) return allocated;

            //rollback if not enough frames
            for (int i = 0; i < found; i++) {
                freeFrames.set(allocated[i]);
            }
            return null;
        } finally {
            allocationLock.unlock();
        }
    }

    //escreve em um frame específico, marcando a heap com o ID da variável (versão mais realista)
    /*public void writeHeap(int frameIndex, int frameOffset, int variableId) {
        int startIndex = frameIndex * frameSizeInt + frameOffset;
        //if (startIndex >= 0 && startIndex < heap.length) {
            heap[startIndex] = variableId;
        //}
    }*/

    //(versão otimizada)
    public void writeBlock(int frameIndex, int variableId, int count) {
        Lock lock = frameLocks.get(frameIndex);
        lock.lock();
        try {
            int start = frameIndex * frameSizeInt;
            int end = Math.min(start + count, start + frameSizeInt);
            Arrays.fill(heap, start, end, variableId);
        } finally {
            lock.unlock();
        }

        /*frameLock.writeLock().lock();
        try {
            int start = frameIndex * frameSizeInt;
            int end = Math.min(start + count, start + frameSizeInt);
            for (int i = start; i < end; i++) {
                heap[i] = variableId;//heap.set(i, variableId); <- atomic
            }
        } finally {
            frameLock.writeLock().unlock();
        }*/
    }

    public void freeFrame(int frame) {
        Lock lock = frameLocks.get(frame);
        lock.lock();
        try {
            freeFrames.set(frame);
            Arrays.fill(heap, frame * frameSizeInt, (frame + 1) * frameSizeInt, 0);
        } finally {
            lock.unlock();
        }


        /*framesLock.writeLock().lock();
        try {
            if (frame >= 0 && frame < numFrames) {
                frames[frame].setAllocated(false);
                ///System.out.println(frame + " mudado para falso");
                //limpa heap associado a esse frame
                int start = frame * frameSizeInt;
                int end = start + frameSizeInt;
                for (int i = start; i < end && i < heap.length; i++) {
                    heap[i] = 0;
                }
            }
        } finally {
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
}