package MMS.utils;

import java.util.Scanner;

public class ConsoleUI {
    private Scanner scanner = new Scanner(System.in);
    private int heapSizeKB;
    private int pageSizeB;
    private int quantity;
    private int minSizeB;
    private int maxSizeB;

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

    private void showMenuOfFiles() {
        System.out.println("\n=== Escolha o arquivo de requisições ===");
        System.out.println("1. requests_1000.txt (1000 requisições)");
        System.out.println("2. requests_10000.txt (10000 requisições)");
        System.out.println("3. requests_100000.txt (100000 requisições)");
    }

    public String selectRequestFile() {
        showMenuOfFiles();

        int fileOption;
        while (true) {
            fileOption = readInt("Digite o número do arquivo desejado: ");
            if (fileOption >= 1 && fileOption <= 3) {
                break;
            } else {
                System.out.println("Opção inválida. Digite 1, 2 ou 3.");
            }
            showMenuOfFiles();
        }

        switch (fileOption) {
            case 1:
                return "src/main/resources/requests-files/requests_1000.txt";
            case 2:
                return "src/main/resources/requests-files/requests_10000.txt";
            case 3:
                return "src/main/resources/requests-files/requests_100000.txt";
            default:
                return "";
        }
    }

    public void configureParameters() {
        heapSizeKB = readInt("Tamanho da Heap (KB): ");
        pageSizeB = readInt("Tamanho da Página (Bytes): ");
        quantity = readInt("Quantidade de requests: ");
        minSizeB = readInt("Tamanho mínimo das requests (Bytes): ");
        maxSizeB = readInt("Tamanho máximo das requests (Bytes): ");
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
        return minSizeB;
    }

    public int getMaxSize() {
        return maxSizeB;
    }

    public void setQuantity(int quantityInFile){
        quantity = quantityInFile;
    }

}
