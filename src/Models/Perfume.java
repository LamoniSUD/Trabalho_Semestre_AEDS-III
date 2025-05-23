package Models;

import java.io.*;
import java.nio.*;
import java.nio.charset.*;
import java.util.*;
import java.util.concurrent.atomic.*;

public class Perfume implements Serializable, Comparable<Perfume> {
    // Campos obrigatórios
    private final int id;
    private String nome;
    private String marca;
    private double valor;
    private int estoque;
    
    // Novos campos para controle
    private final AtomicInteger version = new AtomicInteger(0);
    private final long createdAt;
    private volatile long lastUpdated;
    
    // Valores padrão
    private static final int MAX_NOME = 100;
    private static final int MAX_MARCA = 50;
    private static final double VALOR_MINIMO = 0.01;

    // Construtores
    public Perfume(int id, String nome, String marca, double valor, int estoque) {
        validarCampos(id, nome, marca, valor, estoque);
        
        this.id = id;
        this.nome = nome.trim();            // Método "trim()" retira espaços da String, circulando erros
        this.marca = marca.trim();
        this.valor = valor;
        this.estoque = estoque;
        this.createdAt = System.currentTimeMillis();    // Atribuindo valor da data específica no momento de criação do Objeto
        this.lastUpdated = this.createdAt;
    }

    // Validação rigorosa
    // Verificação de parâmetros para criação do objeto sem falhas
    private void validarCampos(int id, String nome, String marca, double valor, int estoque) {
        if (id <= 0) throw new IllegalArgumentException("ID inválido");               
        if (nome == null || nome.trim().isEmpty() || nome.length() > MAX_NOME) {
            throw new IllegalArgumentException("Nome inválido");
        }
        if (marca == null || marca.trim().isEmpty() || marca.length() > MAX_MARCA) {
            throw new IllegalArgumentException("Marca inválida");
        }
        if (valor < VALOR_MINIMO) throw new IllegalArgumentException("Valor inválido");
        if (estoque < 0) throw new IllegalArgumentException("Estoque não pode ser negativo");
    }

    // ---- Métodos de Acesso ----
    public int getId() { return id; }
    
    public String getNome() { return nome; }
    public synchronized void setNome(String nome) {        // "Syncronized" Garante que o método será executado uma Thread por vez 
        validarNome(nome);
        this.nome = nome.trim();
        atualizar();
    }
    
    public String getMarca() { return marca; }
    public synchronized void setMarca(String marca) {
        validarMarca(marca);
        this.marca = marca.trim();
        atualizar();
    }
    
    public double getValor() { return valor; }
    public synchronized void setValor(double valor) {
        validarValor(valor);
        this.valor = valor;
        atualizar();
    }
    
    public int getEstoque() { return estoque; }
    public synchronized void setEstoque(int estoque) {
        validarEstoque(estoque);
        this.estoque = estoque;
        atualizar();
    }
    
    // ---- Controle de Versão ----
    public int getVersion() { return version.get(); }
    public synchronized void incrementVersion() { 
        version.incrementAndGet(); 
        lastUpdated = System.currentTimeMillis();
    }
    
    // ---- Serialização Otimizada ----
    public byte[] toByteArray() throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream(256);
        DataOutputStream dos = new DataOutputStream(baos);
        
        dos.writeInt(id);
        dos.writeUTF(nome);
        dos.writeUTF(marca);
        dos.writeDouble(valor);
        dos.writeInt(estoque);
        dos.writeInt(version.get());
        dos.writeLong(createdAt);
        dos.writeLong(lastUpdated);
        
        return baos.toByteArray();
    }
    
    public static Perfume fromByteArray(byte[] data) throws IOException {
        ByteArrayInputStream bais = new ByteArrayInputStream(data);
        DataInputStream dis = new DataInputStream(bais);
        
        int id = dis.readInt();
        String nome = dis.readUTF();
        String marca = dis.readUTF();
        double valor = dis.readDouble();
        int estoque = dis.readInt();
        int version = dis.readInt();
        long createdAt = dis.readLong();
        long lastUpdated = dis.readLong();
        
        Perfume p = new Perfume(id, nome, marca, valor, estoque);
        p.version.set(version);
        p.lastUpdated = lastUpdated;
        return p;
    }
    
    // ---- Métodos de Negócio ----
    public synchronized void aplicarDesconto(double percentual) {
        if (percentual <= 0 || percentual > 100) {
            throw new IllegalArgumentException("Percentual inválido");
        }
        this.valor *= (1 - (percentual/100));
        atualizar();
    }
    
    public synchronized boolean reservarEstoque(int quantidade) {
        if (quantidade <= 0 || quantidade > estoque) return false;
        estoque -= quantidade;
        atualizar();
        return true;
    }
    
    // ---- Controle de Tempo ----
    public long getCreatedAt() { return createdAt; }
    public long getLastUpdated() { return lastUpdated; }
    private void atualizar() { 
        lastUpdated = System.currentTimeMillis();
        incrementVersion();
    }
    
    // ---- Comparação e Identidade ----
    @Override   //Métodos Importados para comparativo
    public int compareTo(Perfume o) {
        return Integer.compare(this.id, o.id);
    }
    
    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof Perfume)) return false;
        return this.id == ((Perfume)obj).id;
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
    
    @Override
    public String toString() {
        return String.format(
            "Perfume[id=%d, nome='%s', marca='%s', valor=%.2f, estoque=%d, versão=%d]",
            id, nome, marca, valor, estoque, version.get()
        );
    }
    
    // ---- Validações Específicas ----
    private void validarNome(String nome) {
        if (nome == null || nome.trim().isEmpty()) {
            throw new IllegalArgumentException("Nome não pode ser vazio");
        }
        if (nome.length() > MAX_NOME) {
            throw new IllegalArgumentException("Nome muito longo");
        }
    }
    
    private void validarMarca(String marca) {
        if (marca == null || marca.trim().isEmpty()) {
            throw new IllegalArgumentException("Marca não pode ser vazia");
        }
        if (marca.length() > MAX_MARCA) {
            throw new IllegalArgumentException("Marca muito longa");
        }
    }
    
    private void validarValor(double valor) {
        if (valor < VALOR_MINIMO) {
            throw new IllegalArgumentException("Valor abaixo do mínimo permitido");
        }
    }
    
    private void validarEstoque(int estoque) {
        if (estoque < 0) {
            throw new IllegalArgumentException("Estoque não pode ser negativo");
        }
    }
}
