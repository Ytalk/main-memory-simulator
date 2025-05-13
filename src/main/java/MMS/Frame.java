package MMS;

public class Frame {
    private final int indexFrame;
    //private boolean isAllocated;
    private volatile boolean modified;
    private volatile boolean referenced;

    /*public Frame(int index, boolean isAllocated){
        this.indexFrame = index;
        this.isAllocated = isAllocated;
    }*/

    public Frame(int index) {
        this.indexFrame = index;
    }

    public int getIndex() {
        return indexFrame;
    }

    /*public boolean isAllocated() {
        return isAllocated;
    }*/

    /*public void setAllocated(boolean allocated) {
        isAllocated = allocated;
    }*/
}