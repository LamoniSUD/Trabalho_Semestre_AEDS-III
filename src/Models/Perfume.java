package Models;
import java.io.*;
import java.time.LocalDate;
import java.util.Random;

public class Perfume {
    private int id;
    private boolean available;
    private String[] info;
    private int value;  // Valor em centavos
    private int stock;
    private LocalDate date;

    public Perfume() {
        this(-1, false, "", "", -1, 0, LocalDate.now());  // Valor padrão para stock
    }

    public Perfume(String name, String marca, int value, LocalDate date) {
        Random rand = new Random();
        int ano = LocalDate.now().getYear();
        int numeroAleatorio = rand.nextInt(9000) + 1000;
        this.id = Integer.parseInt(ano + "" + numeroAleatorio);

        this.value = value;
        this.available = true;
        this.info = new String[2];
        this.info[0] = name;
        this.info[1] = marca;
        this.date = date;
        this.stock = 0;  // Default stock as 0, will be set later
    }

    public Perfume(int id, boolean available, String name, String marca, int value, int stock, LocalDate date) {
        this.id = id;
        this.available = available;
        this.info = new String[2];
        this.info[0] = name;
        this.info[1] = marca;
        this.value = value;
        this.stock = stock;
        this.date = date;
    }

    public int getId() {
        return id;
    }

    public boolean isAvailable() {
        return available;
    }

    public String getName() {
        return info[0];
    }

    public void setName(String name) {
        this.info[0] = name;
    }

    public String getMarca() {
        return info[1];
    }

    public void setMarca(String marca) {
        this.info[1] = marca;
    }

    public float getValue() {
        return value / 100.0f;  // Convertendo de centavos para reais
    }

    public void setValue(int value) {
        this.value = value;
    }

    public int getStock() {
        return stock;
    }

    // Definir o estoque, ajustando a disponibilidade do perfume
    public void setStock(int stock) {
        if (stock < 0) {
            throw new IllegalArgumentException("Estoque não pode ser negativo");
        }
        this.stock = stock;
        this.available = stock > 0;
    }

    public LocalDate getDate() {
        return date;
    }

    public void setDate(LocalDate date) {
        this.date = date;
    }

    // Converte o objeto para um byte array
    public byte[] toByteArray() {
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
             DataOutputStream dos = new DataOutputStream(baos)) {
            dos.writeInt(this.id);
            dos.writeBoolean(this.available);
            dos.writeUTF(this.info[0]);
            dos.writeUTF(this.info[1]);
            dos.writeInt(this.value);
            dos.writeInt(this.stock);
            dos.writeLong(this.date.toEpochDay());
            return baos.toByteArray();
        } catch (IOException e) {
            System.out.println("Erro ao converter para byte array: " + e.getMessage());
        }
        return new byte[0];
    }

    // Converte um byte array de volta para um objeto Perfume
    public static Perfume fromByteArray(byte[] data) {
        try (ByteArrayInputStream bais = new ByteArrayInputStream(data);
             DataInputStream dis = new DataInputStream(bais)) {
            int id = dis.readInt();
            boolean available = dis.readBoolean();
            String name = dis.readUTF();
            String marca = dis.readUTF();
            int value = dis.readInt();
            int stock = dis.readInt();
            LocalDate date = LocalDate.ofEpochDay(dis.readLong());
            return new Perfume(id, available, name, marca, value, stock, date);
        } catch (IOException e) {
            System.out.println("Erro ao converter byte array para Perfume: " + e.getMessage());
        }
        return null;
    }

    @Override
    public String toString() {
        return "\nID........: " + this.id +
                "\nDisponível: " + this.available +
                "\nNome......: " + this.info[0] +
                "\nMarca.....: " + this.info[1] +
                "\nPreço.....: R$ " + String.format("%.2f", getValue()) +  // Formata o preço para exibição
                "\nEstoque...: " + this.stock +
                "\nData......: " + this.date;
    }
}
