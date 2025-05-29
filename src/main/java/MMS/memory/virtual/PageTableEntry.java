package MMS.memory.virtual;

public class PageTableEntry {
    private volatile int frame;//número do frame (-1 se não mapeado)
    private volatile boolean present;
    private volatile int protection;

    public PageTableEntry() {
        this(-1);
    }

    public PageTableEntry(int frame){
        this.frame = frame;
        this.present = (frame != -1);
    }

    public int getPhysicalFrame() {
        return frame;
    }

    public void setPhysicalFrame(int frame){
        this.frame = frame;
    }

}