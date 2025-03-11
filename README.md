import java.io.*;
import java.util.Scanner;

public class Main {
    private Scanner scan = new Scanner(System.in);
    private Escrever escrever = new Escrever();
    private Ler ler = new Ler();

    public void menu() {
        while (true) {
            System.out.println("\nMenu:");
            System.out.println("1. Add a Parfum in stock");
            System.out.println("2. Show Parfums in Stock");
            System.out.println("3. Show archive's size");
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
                    exibirTamanhoArquivo();
                    break;
                case 4:
                    deletarArquivo();
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

class Lista {
    private Celula first, last;

    public Lista() {
        first = last = null;
    }

    public void insertionFirst(Perfume perfume) {
        if (perfume == null) {
            throw new IllegalArgumentException("Perfume não pode ser nulo");
        }

        Celula tmp = new Celula(perfume);

        if (first == null) {
            first = last = tmp;
        } else {
            tmp.right = first;
            first.left = tmp;
            first = tmp;
        }
    }
}

class Celula {
    Perfume perfume;
    Celula left, right;

    public Celula(Perfume perfume) {
        this.perfume = perfume;
        this.left = this.right = null;
    }

    public Perfume getPerfume() {
        return perfume;
    }

    public void setPerfume(Perfume perfume) {
        this.perfume = perfume;
    }
}

class Perfume {
    private int id;
    private boolean available;
    private String[] info;
    private int value;
    private int stock;

    public Perfume(int id, String name, String marca, int value) {
        this.id = id;
        this.value = value;
        this.available = true;
        this.info = new String[2];
        this.info[0] = name;
        this.info[1] = marca;
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public boolean isAvailable() {
        return available;
    }

    public void setAvailable(boolean available) {
        this.available = available;
    }

    public String getName() {
        return info[0];
    }
    public void setName(String name){
        this.info[0] = name;
    }
    public String getMarca(){
        return info[1];
    }
    public void setMarca(String marca){
        this.info[1] = marca;
    }

    public void setinfo(String[] info) {
        this.info = info;
    }

    public int getValue() {
        return value;
    }

    public void setValue(int value) {
        this.value = value;
    }

    public int getStock() {
        return stock;
    }

    public void setStock(int stock) {
        this.stock = stock;
        this.available = stock > 0;
    }

    public void update(String name, String marca, int price) {
        this.info[0] = name;
        this.info[1] = marca;
        this.value = price;
    }

    public byte[] toByteArray() throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        DataOutputStream dos = new DataOutputStream(baos);
        dos.writeInt(id);
        dos.writeBoolean(available);
        dos.writeUTF(info[0]);
        dos.writeUTF(info[1]);
        dos.writeInt(value);
        dos.writeInt(stock);
        return baos.toByteArray();
    }

    public static Perfume fromByteArray(byte[] data) throws IOException {
        ByteArrayInputStream bais = new ByteArrayInputStream(data);
        DataInputStream dis = new DataInputStream(bais);
        int id = dis.readInt();
        boolean available = dis.readBoolean();
        String name = dis.readUTF();
        String marca = dis.readUTF();
        int value = dis.readInt();
        int stock = dis.readInt();

        Perfume perfume = new Perfume(id, name, marca, value);
        perfume.setStock(stock);

        return perfume;
    }
}

class Ler {
    public void readIn() {
        File file = new File("archive.bin");

        if (!file.exists()) {
            System.out.println("O arquivo não existe.");
            return;
        }

        try (FileInputStream fis = new FileInputStream(file);
                DataInputStream dis = new DataInputStream(fis)) {

            System.out.println("Perfumes disponíveis:");

            while (dis.available() > 0) {
                int size = dis.readInt();
                byte[] data = new byte[size];
                dis.readFully(data);
                Perfume perfume = Perfume.fromByteArray(data);

                if (perfume.isAvailable()) {
                    System.out.println("--------------------");
                    System.out.println("ID: " + perfume.getId());
                    System.out.println("Nome: " + perfume.getName());
                    System.out.println("Marca" + perfume.getMarca());
                    System.out.println("Valor: R$ " + perfume.getValue() / 100.0);
                    System.out.println("Estoque: " + perfume.getStock());
                    System.out.println("Em estoque: " + (perfume.isAvailable() ? "Sim" : "Não"));
                }
            }

        } catch (IOException e) {
            System.out.println("Erro ao ler o arquivo: " + e.getMessage());
        }
    }

    public void readOut() {
        File file = new File("archive.bin");

        if (!file.exists()) {
            System.out.println("O arquivo não existe.");
            return;
        }

        try (FileInputStream fis = new FileInputStream(file);
                DataInputStream dis = new DataInputStream(fis)) {

            System.out.println("Perfumes não disponíveis:");

            while (dis.available() > 0) {
                int size = dis.readInt();
                byte[] data = new byte[size];
                dis.readFully(data);
                Perfume perfume = Perfume.fromByteArray(data);

                if (!perfume.isAvailable()) {
                    System.out.println("--------------------");
                    System.out.println("ID: " + perfume.getId());
                    System.out.println("Nome: " + perfume.getName());
                    System.out.println("Valor: R$ " + perfume.getValue() / 100.0);
                    System.out.println("Estoque: " + perfume.getStock());
                    System.out.println("Em estoque: " + (perfume.isAvailable() ? "Sim" : "Não"));
                }
            }

        } catch (IOException e) {
            System.out.println("Erro ao ler o arquivo: " + e.getMessage());
        }
    }
}

class Escrever {
    public void write(Scanner scan) {
        try (FileOutputStream fos = new FileOutputStream("archive.bin", true); // "true" para append
                DataOutputStream dos = new DataOutputStream(fos)) {

            System.out.println("Digite o ID do perfume: ");
            int id = scan.nextInt();
            scan.nextLine();

            System.out.println("Digite o nome do perfume: ");
            String name = scan.nextLine();

            System.out.println("Digite a marca do perfume: ");
            String marca = scan.nextLine();

            System.out.println("Digite o valor do perfume (em centavos): ");
            int value = scan.nextInt();

            System.out.println("Digite o estoque do perfume: ");
            int stock = scan.nextInt();

            Perfume perfume = new Perfume(id, name, marca, value);
            perfume.setStock(stock);

            byte[] perfumeBytes = perfume.toByteArray();

            dos.writeInt(perfumeBytes.length);
            dos.write(perfumeBytes); // Depois, escreve o objeto serializado

            System.out.println("Perfume adicionado com sucesso!");

        } catch (IOException e) {
            System.out.println("Erro ao escrever no arquivo: " + e.getMessage());
        }
    }
}
