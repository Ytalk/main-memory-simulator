package MMS;

import java.util.Scanner;

public class ConsoleUI {
    private Scanner scanner = new Scanner(System.in);

    private int heapSizeKB;
    private int pageSizeB;
    private int quantity;
    private int minSize;
    private int maxSize;

    public void showMenu() {
        System.out.println("\n=== Menu do MemoryManager ===");
        System.out.println("1. Configurar parâmetros");
        System.out.println("2. Executar modo sequencial");
        System.out.println("3. Executar modo paralelo");
        System.out.println("4. Comparar sequencial vs paralelo e exportar gráfico");
        System.out.println("5. Executar modo sequencial random");
        System.out.println("6. Executar modo paralelo random");
        System.out.println("7. Sair");
    }

    public void configureParameters() {
        heapSizeKB = readInt("Tamanho da Heap (KB): ");
        pageSizeB = readInt("Tamanho da Página (Bytes): ");
        quantity = readInt("Quantidade de requests: ");
        minSize = readInt("Tamanho mínimo das requests (Bytes): ");
        maxSize = readInt("Tamanho máximo das requests (Bytes): ");
    }

    public int readInt(String prompt) {
        while (true) {
            System.out.print(prompt);
            try {
                return Integer.parseInt(scanner.nextLine().trim());
            } catch (NumberFormatException e) {
                System.out.println("Entrada inválida. Digite um número inteiro.");
            }
        }
    }

    public void closeScanner(){
        scanner.close();
    }

    public int getHeapSizeKB() {
        return heapSizeKB;
    }
    public int getPageSizeB() {
        return pageSizeB;
    }
    public int getQuantity() {
        return quantity;
    }
    public int getMinSize() {
        return minSize;
    }
    public int getMaxSize() {
        return maxSize;
    }

}
