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

Class List{

    Celula first, last;

    List(Perfume P){
        first = new Celula(p);
        last = first;
    }

    private void insertionFirst(Perfume parfum) Throws Exception{
        Celula tmp = new Celula(parfum);
        tmp.right = first.right;
        tmp.left = first;
        first.right = tmp;
        if(first == last){
            ultimo = tmp;
        }else{
            tmp.right.left = tmp;
        }
        tmp = nul;
    }

    private void insertion(Perfume parfum) Throws Exception{
        last.right = new Celula(parfum);
        last.right.left = last;     
        last = last.right;
    }

    private Perfume search(String name) {
        Celula tmp = first;
        while (tmp.parfum.name != name && tmp != null) {
            tmp = tmp.right;
        }

        if (tmp.parfum.name == name) {
            return tmp.parfum;
        } else {
            System.err.println("Perfume não encontrado");
            return null;
        }
    }

    private Perfume remove(String name) {
        Celula tmp = first;
        while (tmp.parfum.name != name && tmp != null) {
            tmp = tmp.right;
        }
        if (tmp.parfum.name == name) {
            tmp.parfum.statusOFF();
            return tmp.parfum;
        } else {
            System.err.println("Perfume nao Encontrado");
            return null;
        }
    }

    private void show(){
        Celula tmp = first.right;
        while (tmp!=null) {
            System.err.println();
            
        }
    }


    }
Class Celula{

    Perfume parfum;
    Celula left, right;

    Celula (Parfum P){
        this.parfum = P;
        this.left = this.right = null;

    }
    public Perfume getPerfume() {return parfum;}
    public void setPerfume(Parfum parfum) {this.parfum = parfum;}
}

public class Perfume {
    int id;
    Boolean tombstone;
    String name;
    int value; // Valor escrito em centavos para melhores cálculos
    int stock;

    Perfume(int ID, String name, int value) {
        this.id = ID;
        this.name = name;
        this.value = value;
    }

    public int getid() {
        return id;
    }

    public void setid(int id) {
        this.id = id;
    }

    public boolean getTombstone() {
        return tombstone;
    }

    public void setTombstone(boolean tombstone) {
        this.tombstone = tombstone;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public int getValue() {
        return Value;
    }

    public void setValue(int value) {
        this.value = value;
    }

    public int getStock() {
        return stock;
    }

    public void setStock(int stock) {
        this.stock = stock;
    }

    void update(String name, int price) {
        this.name = name;
        this.value = price;
    }

    void statusOFF() {
        setTombstone(false);
    }

    void statusON() {
        setTombstone(true);
    }

    public byte[] toByteArray()throws IOException{
        ByteArrayOutputStream BAOS = new ByteArrayOutputStream();
        DataOutputStream DOS = new DataOutputStream(BAOS);
        DOS.writeInt(id);
        DOS.writeBoolean(Tombstone);
        DOS.writeUTF(name);
        DOS.writeInt(value);
        DOS.writeInt(stock);

        return BAOS.toByteArray();
    }

    public static Perfume fromByteArray(byte[] data) Throws IOException{
        ByteArrayInputStream BAIS = new ByteArrayInputStream(data);
        DataInputStream DIS = new DataInputStream(BAIS);
        int id = DIS.readInt();
        Boolean Tombstone = DIS.readBoolean();
        String name = DIS.readUTF();
        int value = DIS.readInt();
        int stock = DIS.readInt();

        return new Perfume(id, Tombstone, name, value, stock);
    }
    
    public String toString(Perfume parfume){
        
    }
    





}
