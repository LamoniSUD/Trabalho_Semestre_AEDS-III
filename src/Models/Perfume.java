package Models;
import java.io.*;
import java.time.LocalDate;
import java.util.Random;

public class Perfume {
    private int id;
    private boolean available;
    private String[] info;
    private int value;
    private int stock;
    private LocalDate date;

    public Perfume() {
        this(-1, false, "", "", -1, -1, LocalDate.now());
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
    }

    public Perfume(int i, boolean b, String inf1, String inf2, int V, int S, LocalDate D) {
        this.id = i;
        this.available = b;
        this.info = new String[2];
        this.info[0] = inf1;
        this.info[1] = inf2;
        this.value = V;
        this.stock = S;
        this.date = D;
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
        return (float) value;
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

    public LocalDate getDate() {
        return date;
    }

    public void setDate(LocalDate date) {
        this.date = date;
    }

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
                "\nAvaiable...:" + this.available +
                "\nNome.......: " + this.info[0] +
                "\nMarca......: " + this.info[1] +
                "\nPreço......: " + this.value +
                "\nEstoque....:" + this.stock +
                "\nData.......: " + this.date;
    }
}