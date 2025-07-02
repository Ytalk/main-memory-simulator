package MMS.memory.virtual;

import com.google.common.util.concurrent.Striped;

import java.util.Arrays;
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

    /*private void initializeEntries() {
        for (int i = 0; i < numPages; i++) {
            Arrays.fill( entries, new PageTableEntry() );//-1 = não mapeado
            freePagesQueue.add(i);
        }
    }*/

    private void initializeEntries() {
        for (int i = 0; i < numPages; i++) {
            entries[i] = new PageTableEntry();
            freePagesQueue.add(i);
        }
    }

    //mapeia = página -> frame
    public void map(int virtualPage, int physicalFrame) {//primeiro frame disponivel que encontrar
        /*Lock lock = pageLocks.get(virtualPage);//write
        lock.lock();
        try {
            //entries[virtualPage].setPhysicalFrame(physicalFrame);
            entries[virtualPage] = new PageTableEntry(physicalFrame);
        } finally {
            lock.unlock();
        }*/

        entries[virtualPage] = new PageTableEntry(physicalFrame);
    }

    public void unmap(int virtualPage) {
        /*Lock lock = pageLocks.get(virtualPage);//write
        lock.lock();
        try {
            entries[virtualPage].setPhysicalFrame(-1);
            freePagesQueue.add(virtualPage);
        } finally {
            lock.unlock();
        }*/

        entries[virtualPage].setPhysicalFrame(-1);
        freePagesQueue.add(virtualPage);
    }

    public boolean isMapped(int virtualPage) {
        //Lock lock = pageLocks.get(virtualPage);//read
        //lock.lock();
        //try {
            return entries[virtualPage].getPhysicalFrame() != -1;
        //} finally { lock.unlock(); }
    }

    //retorna o frame associado com a página
    public int getPhysicalFrame(int virtualPage) {
        /*Lock lock = pageLocks.get(virtualPage);//read
        lock.lock();
        try {
            return entries[virtualPage].getPhysicalFrame();
        } finally {
            lock.unlock();
        }*/

        return entries[virtualPage].getPhysicalFrame();
    }

    //percorre a page table até obter a quantidade de pages livres necessárias, retorna uma lista com as paginas obtidas
    public int[] findFreeVirtualPages(int pagesNeeded) {
        int[] freePagesFound = new int[pagesNeeded];
        //lock.lock();
        //try {
            for (int i = 0; i < pagesNeeded; i++) {
                //Integer page = freePagesQueue.poll();
                //if (page == null) break; freeFrames já fez esse trabalho
                freePagesFound[i] = freePagesQueue.poll();
            }
            return freePagesFound;
        //} finally { lock.unlock(); }
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