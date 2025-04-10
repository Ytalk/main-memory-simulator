package MMS;

public class HeapMemory {
    private int[] memory;
    private int sizeKB;
    private int sizeInt;
    private int sizeB;
    private int nextFreeAddress;

    public HeapMemory(int sizeKB) {
        sizeB = sizeKB * 1024;
        sizeInt = sizeB / 4;
        this.memory = new int[sizeInt];
        this.sizeKB = sizeKB;
        nextFreeAddress = 0;
    }

    //aloca espaço e retorna o endereço inicial
    public int findFreeHeap(int requestSizeB) {
        if (nextFreeAddress + requestSizeB > sizeB) {
            return -1;//sem espaço
        }
        int allocatedAddress = nextFreeAddress;
        nextFreeAddress += requestSizeB;
        return allocatedAddress;
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

    public int getSizeInt(){
        return sizeInt;
    }

    public void printHeap() {
        for (int i = 0; i < sizeInt; i++) {//vinte para teste
            System.out.println("index: " + i + " | ID da variavel(0 se nao houver): " + memory[i]);
        }
    }

    public void freeHeap(int requestSizeInt, int requestStartAddress){
        if (requestStartAddress < 0 || requestStartAddress + requestSizeInt > memory.length) {
            throw new IllegalArgumentException("Endereço inválido");
        }

        for (int i = 0; i < requestSizeInt; i++) {
            memory[requestStartAddress + i] = 0;
        }
    }

}