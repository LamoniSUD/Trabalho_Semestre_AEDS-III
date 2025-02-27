# Trabalho_Semestre_AEDS-III
Treinando Versionamento de Código Git &amp; Github. Aulas do DIO.me
import java.io.File;
import java.util.*;

public class Main {
    private Scanner scan = new Scanner(System.in);
    private Escrever escrever = new Escrever();
    private Ler ler = new Ler();

    public void menu() {
        while (true) {
            System.out.println("\nMenu:");
            System.out.println("1. Write on the archive");
            System.out.println("2. Read the Archive");
            System.out.println("3. Show archive's size");
            System.out.println("4. Delete the archive");
            System.out.println("5. Add on the archive");
            System.out.println("6. Quit");
            System.out.print("Choose your option: ");
            int opcao = scan.nextInt();
            scan.nextLine();
            
            switch (opcao) {
                case 1:
                    escrever.write(scan);
                    break;
                case 2:
                    ler.read();
                    break;
                case 3:
                    exibirTamanhoArquivo();
                    break;
                case 4:
                    deletarArquivo();
                    break;
                case 6:
                	System.out.println("Saindo...");
                	scan.close();
                	return;
                default:
                    System.out.println("Opção inválida. Tente novamente.");
            }
        }
    }

    private void exibirTamanhoArquivo() {
        File arquivo = new File("archive.bin");
        if (arquivo.exists()) {
            System.out.println("O arquivo contém " + arquivo.length() + " bytes.");
        } else {
            System.out.println("O arquivo ainda não foi criado.");
        }
    }

    private void deletarArquivo() {
        File arquivo = new File("archive.bin");
        if (arquivo.exists()) {
            if (arquivo.delete()) {
                System.out.println("Arquivo deletado com sucesso.");
            } else {
                System.out.println("Falha ao deletar o arquivo.");
            }
        } else {
            System.out.println("O arquivo não existe.");
        }
    }

    public static void main(String[] args) {
        Main main = new Main();
        main.menu();
    }
}
