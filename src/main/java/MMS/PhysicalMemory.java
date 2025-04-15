package MMS;

import java.util.ArrayList;
import java.util.List;

public class PhysicalMemory {
    private Frame[] frames;
    private final int frameSizeB;
    private final int numFrames;

    private int[] heap;
    private int heapSizeKB;
    private int heapSizeInt;
    private int pageSizeInt;
    private int heapSizeB;
    private int nextFreeAddress;
    private int frameSizeInt;

    public PhysicalMemory(int heapSizeKB, int frameSizeB) {//heap e page
        this.heapSizeKB = heapSizeKB;
        this.heapSizeB = heapSizeKB * 1024;
        this.heapSizeInt = heapSizeB / 4;
        this.heap = new int[heapSizeInt];

        this.frameSizeB = frameSizeB;
        this.frameSizeInt = frameSizeB / 4;
        this.numFrames = heapSizeB / frameSizeB;
        this.frames = new Frame[numFrames];

        nextFreeAddress = 0;
        initializeFrames();
    }

    private void initializeFrames() {
        for (int i = 0; i < numFrames; i++) {
            frames[i] = new Frame(i, false);
        }
    }

    //aloca frames contíguos ou espalhados
    public int[] allocateFrames(int pagesNeeded) {
        List<Integer> allocatedFrames = new ArrayList<>();

        //busca qualquer frame livre
        if (allocatedFrames.size() < pagesNeeded) {
            for (int i = 0; i < numFrames && allocatedFrames.size() < pagesNeeded; i++) {
                System.out.println("frame " + i);
                System.out.println("alocado? " + frames[i].isAllocated());
                if (!frames[i].isAllocated()) {
                    allocatedFrames.add(i);
                    frames[i].setAllocated(true);
                    System.out.println("entrou");
                }
            }
        }

        if (allocatedFrames.size() < pagesNeeded) {
            for (int frame : allocatedFrames) {//libera frames que alocou
                frames[frame].setAllocated(false);
            }
            return null;//não há frames suficientes
        }

        return allocatedFrames.stream().mapToInt(i -> i).toArray();
    }

    public boolean isFrameAllocated(int frameIndex) {
        return frames[frameIndex].isAllocated();
    }

    public Frame getFrame(int frameIndex) {
        return frames[frameIndex];
    }


    //escreve em um frame específico, marcando a heap com o ID da variável
    public void writeHeap(int frameIndex, int frameOffset, int variableId) {
        int startIndex = frameIndex * frameSizeInt + frameOffset;
        if (startIndex >= 0 && startIndex < heap.length) {
            heap[startIndex] = variableId;
        }
    }

    public void freeFrame(int frame) {
        if (frame >= 0 && frame < numFrames) {
            frames[frame].setAllocated(false);
            System.out.println(frame + " mudado para falso");
            //limpa heap associado a esse frame
            int start = frame * frameSizeInt;
            int end = start + frameSizeInt;
            for (int i = start; i < end && i < heap.length; i++) {
                heap[i] = 0;
            }
        }
    }

    public int getHeapSizeInt(){
        return heapSizeInt;
    }

    public int getNumFrames(){
        return numFrames;
    }

    public void printHeap() {
        for (int i = 0; i < heapSizeInt; i++) {
            System.out.println("index: " + i + " | ID da variavel(0 se nao houver): " + heap[i]);
        }
    }

}