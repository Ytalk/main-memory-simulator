package MMS;

public class PageTableEntry {
    private int physicalFrame;//número do frame (-1 se não mapeado)

    public PageTableEntry(int physicalFrame){
        this.physicalFrame = physicalFrame;
    }

    public int getPhysicalFrame() {
        return physicalFrame;
    }

    public void setPhysicalFrame(int frame){
        physicalFrame = frame;
    }

}