package Structures;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional; // Usar Optional para retornos que podem ser nulos

public class GerenciadorEspaco implements AutoCloseable { // Implementa AutoCloseable

    // --- Classe interna para representar um bloco de espaço livre ---
    public static class FreeBlock implements Serializable {
        private static final long serialVersionUID = 1L;
        private long offset;
        private int size;

        public FreeBlock(long offset, int size) {
            this.offset = offset;
            this.size = size;
        }

        public long getOffset() {
            return offset;
        }

        public int getSize() {
            return size;
        }

        @Override
        public String toString() {
            return "FreeBlock [offset=" + offset + ", size=" + size + "]";
        }
    }

    // --- Propriedades do Gerenciador de Espaço ---
    private List<FreeBlock> freeBlocks;
    private final String freeListFilePath;
    private final long dataStartOffset; // Novo campo: o offset inicial onde os dados começam (após o cabeçalho)

    // --- Construtor ---
    // Agora recebe o 'dataStartOffset'
    public GerenciadorEspaco(String freeListFilePath, long dataStartOffset) {
        this.freeListFilePath = Objects.requireNonNull(freeListFilePath, "O caminho do arquivo de lista livre não pode ser nulo.");
        this.dataStartOffset = dataStartOffset; // Inicializa o offset de início dos dados
        this.freeBlocks = new ArrayList<>(); // Inicializa a lista antes de carregar
        carregarListaLivre(); // Tenta carregar a lista ao iniciar
        System.out.println("GerenciadorEspaco: Inicializado. Offset de dados começando em: " + dataStartOffset);
    }

    /**
     * Adiciona um novo bloco de espaço livre à lista.
     * Os blocos são mantidos ordenados por offset.
     * Há uma verificação para garantir que o bloco não se sobreponha ao cabeçalho.
     *
     * @param offset Offset do espaço livre.
     * @param size   Tamanho do espaço livre.
     */
    public synchronized void addFreeBlock(long offset, int size) {
        // Ignora blocos que estão no espaço do cabeçalho do arquivo principal
        if (offset < dataStartOffset) {
            System.err.println("AVISO: GerenciadorEspaco: Tentativa de adicionar bloco livre no espaço do cabeçalho (" + offset + "). Ignorado.");
            return;
        }

        FreeBlock newBlock = new FreeBlock(offset, size);

        // Encontra a posição correta para manter a lista ordenada por offset
        int i = 0;
        while (i < freeBlocks.size() && freeBlocks.get(i).getOffset() < offset) {
            i++;
        }
        freeBlocks.add(i, newBlock);

        salvarListaLivre(); // Persiste a lista atualizada
        System.out.println("DEBUG: GerenciadorEspaco: Bloco livre adicionado: " + newBlock);
    }

    /**
     * Procura e aloca um bloco de espaço livre que possa acomodar o tamanho necessário.
     * Utiliza uma estratégia "first fit" (primeiro bloco que couber).
     *
     * @param requiredSize O tamanho em bytes necessário para a nova alocação.
     * @return Um {@code Optional<FreeBlock>} contendo o bloco adequado, ou vazio se nenhum bloco for encontrado.
     */
    public synchronized Optional<FreeBlock> getFreeBlock(int requiredSize) {
        for (int i = 0; i < freeBlocks.size(); i++) {
            FreeBlock block = freeBlocks.get(i);
            // Certifica-se de que o bloco livre não começa antes do offset de dados (redundante se addFreeBlock for correto, mas seguro)
            if (block.getOffset() < dataStartOffset) {
                System.err.println("AVISO: GerenciadorEspaco: Bloco livre inválido encontrado durante busca (sobreposto ao cabeçalho): " + block + ". Removendo.");
                freeBlocks.remove(i);
                i--; // Ajusta o índice após a remoção
                continue; // Tenta o próximo bloco
            }

            if (block.getSize() >= requiredSize) {
                // Encontrou um bloco que serve
                freeBlocks.remove(i); // Remove o bloco completo da lista

                if (block.getSize() > requiredSize) {
                    // Se o bloco é maior, adiciona o restante de volta à lista como um novo bloco livre
                    FreeBlock remainingBlock = new FreeBlock(block.getOffset() + requiredSize, block.getSize() - requiredSize);
                    addFreeBlock(remainingBlock.getOffset(), remainingBlock.getSize()); // Reutiliza addFreeBlock para manter a ordem
                    System.out.println("DEBUG: GerenciadorEspaco: Bloco livre dividido. Novo bloco restante: " + remainingBlock);
                }
                salvarListaLivre(); // Persiste a lista atualizada
                System.out.println("DEBUG: GerenciadorEspaco: Bloco livre alocado: " + block.getOffset() + ", tamanho alocado: " + requiredSize);
                return Optional.of(new FreeBlock(block.getOffset(), requiredSize)); // Retorna o bloco original (ou apenas o offset/size alocado)
            }
        }
        return Optional.empty(); // Nenhum bloco adequado encontrado
    }

    /**
     * Carrega a lista de espaços livres de um arquivo persistente.
     * Blocos que se sobrepõem ao cabeçalho são ignorados durante a carga.
     */
    @SuppressWarnings("unchecked") // Suprime o aviso de cast de readObject
    private void carregarListaLivre() {
        try (ObjectInputStream ois = new ObjectInputStream(new FileInputStream(freeListFilePath))) {
            Object obj = ois.readObject();
            if (obj instanceof List) {
                this.freeBlocks = (List<FreeBlock>) obj;
                // Filtrar blocos inválidos que podem ter sido persistidos no passado
                this.freeBlocks.removeIf(block -> block.getOffset() < dataStartOffset);
                // Re-ordenar a lista após a filtragem para garantir consistência
                this.freeBlocks.sort((b1, b2) -> Long.compare(b1.getOffset(), b2.getOffset()));
                System.out.println("GerenciadorEspaco: Lista de espaço livre carregada de: " + freeListFilePath + ". Total de blocos: " + freeBlocks.size());
            } else {
                System.err.println("Erro: Conteúdo do arquivo da lista de espaço livre é inválido.");
                this.freeBlocks = new ArrayList<>();
            }
        } catch (FileNotFoundException e) {
            System.out.println("GerenciadorEspaco: Arquivo da lista de espaço livre não encontrado. Criando um novo.");
            this.freeBlocks = new ArrayList<>();
        } catch (IOException | ClassNotFoundException e) {
            System.err.println("ERRO: GerenciadorEspaco: Falha ao carregar a lista de espaço livre: " + e.getMessage());
            e.printStackTrace(); // Para depuração
            this.freeBlocks = new ArrayList<>(); // Reseta a lista em caso de erro
        }
    }

    /**
     * Salva a lista de espaços livres em um arquivo persistente.
     */
    private void salvarListaLivre() {
        try (ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream(freeListFilePath))) {
            oos.writeObject(freeBlocks);
            // System.out.println("DEBUG: GerenciadorEspaco: Lista de espaço livre salva.");
        } catch (IOException e) {
            System.err.println("ERRO: GerenciadorEspaco: Falha ao salvar a lista de espaço livre: " + e.getMessage());
            e.printStackTrace(); // Para depuração
        }
    }

    /**
     * Limpa a lista de blocos livres em memória e no arquivo persistente.
     */
    public synchronized void clearFreeList() {
        freeBlocks.clear();
        salvarListaLivre(); // Salva a lista vazia
        System.out.println("GerenciadorEspaco: Lista de espaço livre limpa.");
    }

    /**
     * Retorna informações dos blocos livres para depuração.
     * @return Uma lista de strings com a representação de cada bloco livre.
     */
    public synchronized List<String> getFreeBlocksInfo() {
        List<String> info = new ArrayList<>();
        for (FreeBlock block : freeBlocks) {
            info.add(block.toString());
        }
        return info;
    }

    /**
     * Implementação de AutoCloseable para garantir que a lista seja salva ao fechar.
     */
    @Override
    public void close() throws IOException { // Adicionado throws IOException para consistência
        System.out.println("GerenciadorEspaco: Fechando e salvando lista de espaço livre...");
        salvarListaLivre(); // Garante que a lista é salva ao fechar
        System.out.println("GerenciadorEspaco: Fechado.");
    }
}