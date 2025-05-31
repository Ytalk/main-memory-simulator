package MMS.memory.physical;

public class Frame {
    private final int indexFrame;
    private volatile boolean modified;
    private volatile boolean referenced;

    public Frame(int index) {
        this.indexFrame = index;
    }

    public int getIndex() {
        return indexFrame;
    }
}