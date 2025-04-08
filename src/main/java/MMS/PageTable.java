package MMS;

public class PageTable {
    private PageTableEntry[] entries;//index (página virtual) -> value (frame físico)
    private final int pageSizeB;
    private final int numPages;
    private int pageSizeInt;


    public PageTable(int heapSizeKB, int pageSizeB) {
        this.pageSizeB = pageSizeB;
        this.pageSizeInt = pageSizeB / 4;
        this.numPages = (heapSizeKB * 1024) / pageSizeB;
        this.entries = new PageTableEntry[numPages];
        initializeEntries();
    }

    //mapeia - página -> frame
    public void map(int virtualPage, int physicalFrame) {//primeiro frame disponivel que encontrar
        entries[virtualPage] = new PageTableEntry(physicalFrame);
    }

    public void unmap(int virtualPage) {
        entries[virtualPage].setPhysicalFrame(-1);
    }

    public boolean isMapped(int virtualPage) {
        return entries[virtualPage] != null;
    }

    //retorna o frame associado com a página
    public int getPhysicalFrame(int virtualPage) {
        return entries[virtualPage].getPhysicalFrame();
    }

    private void initializeEntries() {
        for (int i = 0; i < numPages; i++) {
            entries[i] = new PageTableEntry(-1);//-1 = não mapeado
        }
    }

    public int getPageSizeB() {
        return pageSizeB;
    }

    public void printPageTable() {
        for (int i = 0; i < numPages; i++) {
            System.out.println("index(page): " + i + " value(frame/-1 se nao mapeado): " + entries[i].getPhysicalFrame());
        }
    }

    public int getPageSizeInt(){
        return pageSizeInt;
    }

}