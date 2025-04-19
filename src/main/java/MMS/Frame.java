package MMS;

public class Frame {
    private final int indexFrame;
    private boolean isAllocated;
    //armazenar id da requisição?

    public Frame(int index, boolean isAllocated){
        this.indexFrame = index;
        this.isAllocated = isAllocated;
    }

    public int getIndex() {
        return indexFrame;
    }

    public boolean isAllocated() {
        return isAllocated;
    }

    public void setAllocated(boolean allocated) {
        isAllocated = allocated;
    }
}