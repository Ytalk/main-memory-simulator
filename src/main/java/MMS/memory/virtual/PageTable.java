package MMS.memory.virtual;

import java.util.concurrent.ConcurrentLinkedQueue;

public class PageTable {
    private PageTableEntry[] entries;//index (página virtual) -> value (frame físico)
    private final ConcurrentLinkedQueue<Integer> freePagesQueue = new ConcurrentLinkedQueue<>();

    private final int pageSizeB;
    private final int pageSizeInt;
    private final int numPages;

    public PageTable(int heapSizeKB, int pageSizeB) {
        this.pageSizeB = pageSizeB;
        this.pageSizeInt = pageSizeB / 4;
        this.numPages = (heapSizeKB * 1024) / pageSizeB;
        this.entries = new PageTableEntry[numPages];
        //pageLocks = Striped.lock(numPages);

        initializeEntries();
    }

    private void initializeEntries() {
        for (int i = 0; i < numPages; i++) {
            entries[i] = new PageTableEntry();
            freePagesQueue.add(i);
        }
    }

    //mapeia = página -> frame
    public void map(int virtualPage, int physicalFrame) {//primeiro frame disponivel que encontrar
        entries[virtualPage] = new PageTableEntry(physicalFrame);//entries[virtualPage].setPhysicalFrame(physicalFrame);
    }

    public void unmap(int virtualPage) {
        entries[virtualPage].setPhysicalFrame(-1);
        freePagesQueue.add(virtualPage);
    }

    public boolean isMapped(int virtualPage) {
        return entries[virtualPage].getPhysicalFrame() != -1;
    }

    //retorna o frame associado com a página
    public int getPhysicalFrame(int virtualPage) {
        return entries[virtualPage].getPhysicalFrame();
    }

    //percorre a page table até obter a quantidade de pages livres necessárias, retorna uma lista com as paginas obtidas
    public int[] findFreeVirtualPages(int pagesNeeded) {
        int[] freePagesFound = new int[pagesNeeded];
        for (int i = 0; i < pagesNeeded; i++) {//freeFrames já faz trabalho de null
            freePagesFound[i] = freePagesQueue.poll();
        }
        return freePagesFound;
    }

    public int getPageSizeInt(){
        return pageSizeInt;
    }

    public int getPageSizeB(){
        return pageSizeB;
    }

    public void printPageTable() {
        for (int i = 0; i < numPages; i++) {
            System.out.println("index(page): " + i + " value(frame/-1 se nao mapeado): " + entries[i].getPhysicalFrame());
        }
    }
     public void reset(){
        freePagesQueue.clear();
        initializeEntries();
     }

}