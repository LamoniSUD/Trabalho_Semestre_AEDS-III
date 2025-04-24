import java.time.LocalDate;
import java.util.Scanner;
import Structures.BPlusTree;
import Models.Perfume;

public class Escrever {
    private BPlusTree arvore;

    public Escrever(int ordem) {
        arvore = new BPlusTree(); // Ordem ainda não está sendo usada
    }

    public void write(Scanner scan) {
        try {
            System.out.println("Digite o nome do perfume: ");
            String name = scan.nextLine();

            System.out.println("Digite a marca do perfume: ");
            String marca = scan.nextLine();

            System.out.println("Digite o valor do perfume (em centavos): ");
            int value = scan.nextInt();

            System.out.println("Digite o estoque do perfume: ");
            int stock = scan.nextInt();
            scan.nextLine();  // Limpar o buffer

            LocalDate date = LocalDate.now();

            Perfume perfume = new Perfume(name, marca, value, date);
            perfume.setStock(stock);

            inserirNaArvore(perfume);

            System.out.println("Perfume adicionado com sucesso!");
            System.out.println("Dados gravados: " + perfume.toString());
        } catch (Exception e) {
            System.out.println("Erro ao adicionar o perfume na árvore: " + e.getMessage());
        }
    }

    private void inserirNaArvore(Perfume perfume) {
        arvore.insert(perfume);
    }
}
