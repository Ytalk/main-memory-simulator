package MMS;

public class MemoryManager {
    private PhysicalMemory physicalMemory;
    private PageTable pageTable;
    private HeapMemory heap;

    public MemoryManager(int heapSizeKB, int pageSizeKB) {
        this.physicalMemory = new PhysicalMemory(heapSizeKB, pageSizeKB);
        this.pageTable = new PageTable(heapSizeKB, pageSizeKB);
        this.heap = new HeapMemory(heapSizeKB);
    }


    public void allocateVariable(int variableId, int sizeBytes) {
        int startAddress = 0;//test

        //calcula quantas páginas são necessárias para a requisição
        int pagesRequired = (int) Math.ceil( (double) sizeBytes / (pageTable.getPageSizeKB() * 1024) );
        System.out.println("precisara da seguinte quantidade de paginas: " + pagesRequired);

        //atualiza pageTable (mapeia páginas -> frames)
        for (int i = 0; i < pagesRequired; i++) {
            int virtualPage = (startAddress / ( (pageTable.getPageSizeKB() * 1024) / 4 )) + i;//páginas contíguas
            int physicalFrame = physicalMemory.allocateFrame();//primeiro frame livre encontrado
            pageTable.map(virtualPage, physicalFrame);
        }

        //marca o espaço no heap com o ID
        int d = sizeBytes / 4;
        heap.allocateHeap(startAddress, d, variableId);


        heap.printHeap();
        pageTable.printPageTable();
    }


    public static void main(String[] args) {//test
        MemoryManager simulator = new MemoryManager(2, 1);
        simulator.allocateVariable(5, 64);
    }
}