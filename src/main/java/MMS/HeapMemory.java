package MMS;

public class HeapMemory {
    private int[] memory;
    private int sizeKB;

    private int sizeInInt;

    public HeapMemory(int sizeKB) {
        sizeInInt = (sizeKB * 1024) / 4;
        this.memory = new int[sizeInInt];
        this.sizeKB = sizeKB;
    }

    //aloca trecho na heap, marcando com o ID da variável
    public void allocateHeap(int startAddress, int size, int variableId) {//startAddress=p size=d
        for (int i = 0; i < size; i++) {
            memory[startAddress + i] = variableId;
        }
    }

    public int getSizeKB() {
        return sizeKB;
    }

    public void printHeap() {
        for (int i = 0; i < 20; i++) {//vinte para teste
            System.out.println("index: " + i + " | ID da variavel(0 se nao houver): " + memory[i]);
        }
    }

}