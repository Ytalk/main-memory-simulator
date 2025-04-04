package MMS;

public class PhysicalMemory {
    private Frame[] frames;
    private final int frameSize;
    private final int numFrames;

    public PhysicalMemory(int totalMemorySize, int frameSize) {//heap e page
        this.numFrames = totalMemorySize / frameSize;
        this.frameSize = frameSize;
        this.frames = new Frame[numFrames];
        initializeFrames();
    }

    private void initializeFrames() {
        for (int i = 0; i < numFrames; i++) {
            frames[i] = new Frame(i, false);
        }
    }

    //aloca um frame livre (retorna index ou -1 se não houver)
    public int allocateFrame() {
        for (int i = 0; i < numFrames; i++) {
            if (!frames[i].isAllocated()) {
                frames[i].setAllocated(true);
                return i;
            }
        }
        return -1;//memória cheia
    }

    public boolean isFrameAllocated(int frameIndex) {
        return frames[frameIndex].isAllocated();
    }

    public Frame getFrame(int frameIndex) {
        return frames[frameIndex];
    }

}