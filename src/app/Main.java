package app;
import java.io.*;
import java.util.Scanner;

import Models.Perfume;
import io.Escrever;
import io.Ler;

public class Main {
    private Scanner scan = new Scanner(System.in);
    private Escrever escrever = new Escrever();
    private Ler ler = new Ler();

    public void menu() {
        while (true) {
            System.out.println("\nMenu:");
            System.out.println("1. Add a Parfum in stock");
            System.out.println("2. Show Parfums in Stock");
            System.out.println("3. Update perfume");
            System.out.println("4. Delete the archive");
            System.out.println("5. Show Out of Stock Parfums");
            System.out.println("6. Quit");
            System.out.print("Choose your option: ");

            int opcao = scan.nextInt();
            scan.nextLine();

            switch (opcao) {
                case 1:
                    escrever.write(scan);
                    break;
                case 2:
                    ler.readIn();
                    break;
                case 3:
                    System.out.println("Escreva o Código ou nome do Produto");
                    String term = scan.nextLine();
                    Perfume parfum = searchPerfume(term);

                    if (parfum != null) {
                        System.out.println(parfum.toString());
                        update(term);
                    } else {
                        System.out.println("Perfume não encontrado.");
                    }
                    break;
                case 4:
                    System.out.println("Deseja Deletar arquivo completamente? sim/nao");
                    String choice = scan.nextLine();
                    if (choice.equalsIgnoreCase("sim")) {
                        deletarArquivo();
                    }
                    break;
                case 5:
                    ler.readOut();
                    break;
                case 6:
                    System.out.println("Saindo...");
                    return;
                default:
                    System.out.println("Opção inválida. Tente novamente.");
            }
        }
    }

    private void deletarArquivo() {
        File arquivo = new File("archive.bin");
        if (arquivo.exists()) {
            System.out.println("Arquivo Existe");
        } else {
            System.out.println("O arquivo não existe.");
        }
        if (arquivo.delete()) {
            System.out.println("Arquivo Deletado com Sucesso");
        } else {
            System.out.println("Falha ao deletar o arquivo.");
        }
    }

    public Perfume searchPerfume(String searchTerm) {
        try (RandomAccessFile raf = new RandomAccessFile("archive.bin", "rw")) {
            long fileLength = raf.length();

            while (raf.getFilePointer() < fileLength) {
                int size = raf.readInt();
                byte[] data = new byte[size];
                raf.readFully(data);
                Perfume perfume = new Perfume();
                perfume = Perfume.fromByteArray(data);

                if (searchTerm.equalsIgnoreCase(perfume.getName())
                        || searchTerm.equals(String.valueOf(perfume.getId()))) {
                    return perfume;
                }
            }
        } catch (IOException e) {
            System.out.println("Erro ao ler o arquivo: " + e.getMessage());
        }
        return null;
    }

    public void update(String term) {
        try (RandomAccessFile raf = new RandomAccessFile("archive.bin", "rw")) {
            long fileLength = raf.length();

            while (raf.getFilePointer() < fileLength) {
                int size = raf.readInt();
                byte[] data = new byte[size];
                raf.readFully(data);
                Perfume perfume = new Perfume();
                perfume = Perfume.fromByteArray(data);

                if (term.equalsIgnoreCase(perfume.getName()) || term.equals(String.valueOf(perfume.getId()))) {
                    System.out.println("O que deseja alterar?\n1- Nome\n2- Marca\n3- Estoque\n4- Valor");
                    int opcao = scan.nextInt();
                    scan.nextLine();

                    switch (opcao) {
                        case 1:
                            System.out.println("Escreva o novo Nome: ");
                            perfume.setName(scan.nextLine());
                            break;
                        case 2:
                            System.out.println("Escreva a nova Marca: ");
                            perfume.setMarca(scan.nextLine());
                            break;
                        case 3:
                            System.out.println("Escreva a nova quantidade em Estoque: ");
                            perfume.setStock(scan.nextInt());
                            break;
                        case 4:
                            System.out.println("Escreva o novo valor: ");
                            perfume.setValue(scan.nextInt());
                            break;
                        default:
                            System.out.println("Opção inválida.");
                            return;
                    }

                    raf.seek(raf.getFilePointer() - size - 4);
                    byte[] updatedData = perfume.toByteArray();
                    raf.writeInt(updatedData.length);
                    raf.write(updatedData);

                    System.out.println("Perfume atualizado com sucesso!");
                    return;
                }
            }
        } catch (IOException e) {
            System.out.println("Erro ao ler o arquivo: " + e.getMessage());
        }
        System.out.println("Perfume não encontrado.");
    }

    public static void main(String[] args) {
        Main main = new Main();
        main.menu();
    }
}