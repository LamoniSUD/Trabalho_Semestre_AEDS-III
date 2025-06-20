package Models;

import java.nio.ByteBuffer;
// import Services.GerenciadorArquivos; // Não é necessário aqui, a menos que haja uso direto
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.zip.CRC32;

public class Perfume {
    // --- Constantes de Serialização ---
    public static final int RECORD_SIZE = 256; // Tamanho total do registro
    private static final int CHECKSUM_BYTES = Long.BYTES; // 8 bytes para o checksum CRC32

    private static final int MAX_NAME_BYTES = 80; // Espaço fixo para o nome
    private static final int MAX_BRAND_BYTES = 80; // Espaço fixo para a marca

    private int id;
    private String nome;
    private String marca;
    private int valor; // Em centavos, para evitar problemas de ponto flutuante
    private int estoque;
    private boolean ativo; // Para soft-delete
    private int version; // Controle de versão para atualizações

    public Perfume(int id, String nome, String marca, int valor, int estoque) {
        this.id = id;
        this.nome = Objects.requireNonNull(nome);
        this.marca = Objects.requireNonNull(marca);
        this.valor = valor;
        this.estoque = estoque;
        this.ativo = true; // Por padrão, um novo perfume está ativo
        this.version = 1; // Versão inicial
        validaPerfume(); // Chama a validação na criação
    }

    public Perfume(int id, String nome) {
    	this.id = id;
        this.nome = Objects.requireNonNull(nome);
        this.marca = "Marca Padrão"; // Valor padrão para marca
        this.valor = 1;             // Valor padrão para valor
        this.estoque = 0;           // Valor padrão para estoque
        this.ativo = false; // Este construtor cria um perfume inativo por padrão
        this.version = 1;
        // validaPerfume() não é estritamente necessário aqui se o objetivo é sempre inativo
        // mas pode ser chamado para manter a consistência da regra.
        validaPerfume(); 
    }

    // --- Getters e Setters ---
    public int getId() { return id; }
    public String getNome() { return nome; }
    public String getMarca() { return marca; }
    public int getValor() { return valor; }
    public int getEstoque() { return estoque; }
    public boolean isAtivo() { return ativo; }
    public int getVersion() { return version; }

    public void setNome(String nome) { this.nome = Objects.requireNonNull(nome); }
    public void setMarca(String marca) { this.marca = Objects.requireNonNull(marca); }
    
    public void setValor(int valor) { 
        this.valor = valor; 
        validaPerfume(); // Chama validação ao alterar o valor
    }
    
    public void setEstoque(int estoque) { 
        this.estoque = estoque; 
        validaPerfume(); // Chama validação ao alterar o estoque
    }
    
    public void setAtivo(boolean ativo) { this.ativo = ativo; }
    public void setVersion(int version) { this.version = version; }
    public void desative() { this.ativo = false; }
    
    // Método de validação atualizado
    public void validaPerfume() {
        this.ativo = (this.estoque > 0 && this.valor > 0);
    }

    @Override
    public String toString() {
        return "Perfume" +
               "\n id=" + id +
               "\n nome='" + nome +
               "\n marca='" + marca +
               "\n valor=" + String.format("%.2f", (double)valor / 100.0) +
               "\n estoque=" + estoque +
               "\n ativo=" + ativo +
               "\n version =" + version + "\n";
    }

    // --- Métodos de Serialização e Desserialização ---
    public byte[] toByteArray() {
        ByteBuffer buffer = ByteBuffer.allocate(RECORD_SIZE);
        buffer.position(CHECKSUM_BYTES); // Deixa espaço para o checksum

        // Escreve os dados do perfume
        buffer.putInt(this.id);
        
        byte[] nomeBytes = this.nome.getBytes(StandardCharsets.UTF_8);
        int nomeLen = Math.min(nomeBytes.length, MAX_NAME_BYTES);
        buffer.putInt(nomeLen); // Comprimento real do nome
        buffer.put(nomeBytes, 0, nomeLen); // Bytes do nome
        for (int i = 0; i < (MAX_NAME_BYTES - nomeLen); i++) { // Preenche com zeros
            buffer.put((byte) 0);
        }

        byte[] marcaBytes = this.marca.getBytes(StandardCharsets.UTF_8);
        int marcaLen = Math.min(marcaBytes.length, MAX_BRAND_BYTES);
        buffer.putInt(marcaLen); // Comprimento real da marca
        buffer.put(marcaBytes, 0, marcaLen); // Bytes da marca
        for (int i = 0; i < (MAX_BRAND_BYTES - marcaLen); i++) { // Preenche com zeros
            buffer.put((byte) 0);
        }

        buffer.putInt(this.valor);
        buffer.putInt(this.estoque);
        buffer.put(this.ativo ? (byte) 1 : (byte) 0);
        buffer.putInt(this.version);

        // Preenche o restante do buffer com zeros para atingir RECORD_SIZE
        while (buffer.hasRemaining()) {
            buffer.put((byte) 0);
        }

        // Calcula o checksum dos DADOS (do CHECKSUM_BYTES até o final do registro)
        byte[] recordDataWithoutChecksumSpace = new byte[RECORD_SIZE - CHECKSUM_BYTES];
        buffer.position(CHECKSUM_BYTES); // Posiciona para ler os dados
        buffer.get(recordDataWithoutChecksumSpace); // Copia os dados para cálculo

        CRC32 crc = new CRC32();
        crc.update(recordDataWithoutChecksumSpace, 0, recordDataWithoutChecksumSpace.length);
        long checksum = crc.getValue();

        // Volta ao início do buffer e escreve o checksum
        buffer.rewind();
        buffer.putLong(checksum);

        return buffer.array(); // Retorna o array de bytes completo (RECORD_SIZE)
    }

    public static Perfume fromByteArray(byte[] data) {
        if (data == null || data.length != RECORD_SIZE) {
            System.err.println("Erro: Array de bytes inválido para desserialização. Tamanho esperado: " + RECORD_SIZE + ", recebido: " + (data == null ? "null" : data.length));
            return null;
        }

        ByteBuffer buffer = ByteBuffer.wrap(data);
        
        // 1. Validar Checksum
        long storedChecksum = buffer.getLong(); // Lê o checksum armazenado
        
        byte[] recordDataWithoutChecksum = new byte[RECORD_SIZE - CHECKSUM_BYTES];
        buffer.get(recordDataWithoutChecksum); // Lê os dados após o checksum para calcular
        
        CRC32 crc = new CRC32();
        crc.update(recordDataWithoutChecksum, 0, recordDataWithoutChecksum.length);
        long calculatedChecksum = crc.getValue();

        if (storedChecksum != calculatedChecksum) {
            System.err.println("Erro de Checksum: Dados corrompidos! Armazenado: " + storedChecksum + ", Calculado: " + calculatedChecksum);
            return null; // Retorna nulo se o checksum falhar
        }

        // 2. Desserializar os dados (após pular o checksum ou recarregando o buffer a partir do CHECKSUM_BYTES)
        buffer.position(CHECKSUM_BYTES); // Garante que a leitura de dados RECOMECE após o checksum

        int id = buffer.getInt();
        
        int nomeLength = buffer.getInt();
        byte[] nomeBytesBuffer = new byte[MAX_NAME_BYTES];
        buffer.get(nomeBytesBuffer);
        String nome = new String(nomeBytesBuffer, 0, Math.min(nomeLength, MAX_NAME_BYTES), StandardCharsets.UTF_8);
        
        int marcaLength = buffer.getInt();
        byte[] marcaBytesBuffer = new byte[MAX_BRAND_BYTES];
        buffer.get(marcaBytesBuffer);
        String marca = new String(marcaBytesBuffer, 0, Math.min(marcaLength, MAX_BRAND_BYTES), StandardCharsets.UTF_8);

        int valor = buffer.getInt();
        int estoque = buffer.getInt();
        boolean ativo = buffer.get() == 1;
        int version = buffer.getInt();

        Perfume p = new Perfume(id, nome, marca, valor, estoque);
        p.setAtivo(ativo);
        p.setVersion(version);
        return p;
    }
}
