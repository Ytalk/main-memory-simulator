package MMS;


import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public class PageTable {
    private PageTableEntry[] entries;//index (página virtual) -> value (frame físico)
    private final ReadWriteLock rwLock = new ReentrantReadWriteLock(true);

    private final int pageSizeB;
    private final int pageSizeInt;
    private final int numPages;

    public PageTable(int heapSizeKB, int pageSizeB) {
        this.pageSizeB = pageSizeB;
        this.pageSizeInt = pageSizeB / 4;
        this.numPages = (heapSizeKB * 1024) / pageSizeB;
        this.entries = new PageTableEntry[numPages];
        initializeEntries();
    }

    private void initializeEntries() {
        for (int i = 0; i < numPages; i++) {
            Arrays.fill( entries, new PageTableEntry() );//-1 = não mapeado
        }
    }

    //mapeia = página -> frame
    public void map(int virtualPage, int physicalFrame) {//primeiro frame disponivel que encontrar
        rwLock.writeLock().lock();
        try {
            entries[virtualPage] = new PageTableEntry(physicalFrame);
        } finally {
            rwLock.writeLock().unlock();
        }
    }

    public void unmap(int virtualPage) {
        rwLock.writeLock().lock();
        try {
            entries[virtualPage].setPhysicalFrame(-1);
        } finally {
            rwLock.writeLock().unlock();
        }
    }

    public boolean isMapped(int virtualPage) {
        rwLock.writeLock().lock();
        try {
            return entries[virtualPage].getPhysicalFrame() != -1;
        } finally {
            rwLock.writeLock().unlock();
        }
    }

    //retorna o frame associado com a página
    public int getPhysicalFrame(int virtualPage) {
        rwLock.writeLock().lock();
        try {
            return entries[virtualPage].getPhysicalFrame();
        } finally {
            rwLock.writeLock().unlock();
        }
    }

    //percorre a page table até obter a quantidade de pages livres necessárias, retorna uma lista com as paginas obtidas
    public List<Integer> findFreeVirtualPages(int pagesNeeded) {
        List<Integer> freePages = new ArrayList<>();

        rwLock.writeLock().lock();
        try {
            for (int i = 0; i < numPages && freePages.size() < pagesNeeded; i++) {
                if (!isMapped(i))
                    freePages.add(i);
            }
        } finally {
            rwLock.writeLock().unlock();
        }

        return freePages;
    }

    public int getPageSizeInt(){
        return pageSizeInt;
    }

    public int getNumPages(){
        return numPages;
    }

    public void printPageTable() {
        for (int i = 0; i < numPages; i++) {
            System.out.println("index(page): " + i + " value(frame/-1 se nao mapeado): " + entries[i].getPhysicalFrame());
        }
    }

}