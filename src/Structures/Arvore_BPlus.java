package Structures;

import Models.Perfume;
import java.io.*;
import java.nio.ByteBuffer;
import java.util.*;
import java.util.concurrent.locks.*;
import java.util.concurrent.atomic.*;

public class Arvore_BPlus {
    // Configurações
    private final int ordem;
    private Node raiz;
    private final ReadWriteLock arvoreLock = new ReentrantReadWriteLock(true);
    private final AtomicInteger contadorOperacoes = new AtomicInteger(0);
    private static final double FATOR_CARGA_MINIMO = 0.4;

    // Classe Node otimizada
    private class Node implements Serializable {
        boolean isFolha;
        ArrayList<Integer> chaves;
        ArrayList<Long> posicoes;  // Posições no arquivo
        ArrayList<Node> filhos;
        Node proximo;  // Encadeamento para folhas
        AtomicInteger contadorAcessos = new AtomicInteger(0);

        Node(boolean isFolha) {
            this.isFolha = isFolha;
            this.chaves = new ArrayList<>(ordem + 1);
            this.posicoes = new ArrayList<>(ordem + 1);
            this.filhos = isFolha ? null : new ArrayList<>(ordem + 2);
        }
    }

    // Construtor
    public Arvore_BPlus(int ordem) {
        if (ordem < 2) throw new IllegalArgumentException("Ordem mínima é 2");
        this.ordem = ordem;
        this.raiz = new Node(true);
    }

    // ---- Operações Principais ----
    public void insertComPosicao(Perfume perfume, long posicaoArquivo) {
        arvoreLock.writeLock().lock();
        try {
            Node folha = encontrarFolha(raiz, perfume.getId());
            inserirNaFolha(folha, perfume.getId(), posicaoArquivo);
            
            if (folha.chaves.size() > ordem) {
                dividirFolha(folha);
            }
            contadorOperacoes.incrementAndGet();
        } finally {
            arvoreLock.writeLock().unlock();
        }
    }

    public Long buscarPosicao(int id) {
        arvoreLock.readLock().lock();
        try {
            Node folha = encontrarFolha(raiz, id);
            if (folha == null) return null;
            
            int index = buscaBinaria(folha.chaves, id);
            if (index >= 0) {
                folha.contadorAcessos.incrementAndGet();
                return folha.posicoes.get(index);
            }
            return null;
        } finally {
            arvoreLock.readLock().unlock();
        }
    }

    public void atualizarPosicao(int id, long posicaoAntiga, long posicaoNova) {
        arvoreLock.writeLock().lock();
        try {
            Node folha = encontrarFolha(raiz, id);
            if (folha == null) return;
            
            int index = folha.posicoes.indexOf(posicaoAntiga);
            if (index != -1 && folha.chaves.get(index) == id) {
                folha.posicoes.set(index, posicaoNova);
                contadorOperacoes.incrementAndGet();
            }
        } finally {
            arvoreLock.writeLock().unlock();
        }
    }

    // ---- Métodos Auxiliares ----
    private Node encontrarFolha(Node no, int id) {
        if (no == null) return null;
        
        // Otimização: cache para nós frequentemente acessados
        if (no.contadorAcessos.get() > 1000 && !no.isFolha) {
            for (int i = 0; i < no.chaves.size(); i++) {
                if (id <= no.chaves.get(i)) {
                    return encontrarFolha(no.filhos.get(i), id);
                }
            }
            return encontrarFolha(no.filhos.get(no.filhos.size() - 1), id);
        }

        if (no.isFolha) return no;
        
        int i = 0;
        while (i < no.chaves.size() && id > no.chaves.get(i)) {
            i++;
        }
        
        return encontrarFolha(no.filhos.get(i), id);
    }

    private void inserirNaFolha(Node folha, int id, long posicao) {
        int index = buscaBinaria(folha.chaves, id);
        if (index >= 0) {
            folha.posicoes.set(index, posicao); // Atualiza se existir
        } else {
            index = -(index + 1);
            folha.chaves.add(index, id);
            folha.posicoes.add(index, posicao);
        }
    }

    private void dividirFolha(Node folha) {
        Node novaFolha = new Node(true);
        int meio = folha.chaves.size() / 2;
        
        // Divide chaves e posições
        novaFolha.chaves = new ArrayList<>(folha.chaves.subList(meio, folha.chaves.size()));
        novaFolha.posicoes = new ArrayList<>(folha.posicoes.subList(meio, folha.posicoes.size()));
        
        folha.chaves = new ArrayList<>(folha.chaves.subList(0, meio));
        folha.posicoes = new ArrayList<>(folha.posicoes.subList(0, meio));
        
        // Atualiza encadeamento
        novaFolha.proximo = folha.proximo;
        folha.proximo = novaFolha;
        
        // Propaga para o pai
        if (folha == raiz) {
            Node novaRaiz = new Node(false);
            novaRaiz.chaves.add(novaFolha.chaves.get(0));
            novaRaiz.filhos.add(folha);
            novaRaiz.filhos.add(novaFolha);
            raiz = novaRaiz;
        } else {
            inserirNoPai(folha, novaFolha, novaFolha.chaves.get(0));
        }
    }

    // ---- Balanceamento Automático ----
    private void balancearArvore() {
        arvoreLock.writeLock().lock();
        try {
            if (raiz.chaves.size() == 0 && !raiz.isFolha) {
                raiz = raiz.filhos.get(0); // Reduz altura
            }
        } finally {
            arvoreLock.writeLock().unlock();
        }
    }

    // ---- Persistência Otimizada ----
    public void salvarParaArquivo(String caminho) throws IOException {
        arvoreLock.readLock().lock();
        try (ObjectOutputStream oos = new ObjectOutputStream(
             new BufferedOutputStream(new FileOutputStream(caminho)))) {
            oos.writeObject(this.raiz);
            oos.writeInt(this.ordem);
            oos.writeInt(this.contadorOperacoes.get());
        } finally {
            arvoreLock.readLock().unlock();
        }
    }

    public void carregarDeArquivo(String caminho) throws IOException, ClassNotFoundException {
        arvoreLock.writeLock().lock();
        try (ObjectInputStream ois = new ObjectInputStream(
             new BufferedInputStream(new FileInputStream(caminho)))) {
            this.raiz = (Node) ois.readObject();
            int ordemArquivo = ois.readInt();
            if (ordemArquivo != this.ordem) {
                throw new IOException("Ordem inconsistente");
            }
            this.contadorOperacoes.set(ois.readInt());
        } finally {
            arvoreLock.writeLock().unlock();
        }
    }

    // ---- Métodos de Busca Avançada ----
    public List<Long> buscarIntervalo(int inicio, int fim) {
        arvoreLock.readLock().lock();
        try {
            List<Long> resultados = new ArrayList<>();
            Node folha = encontrarFolha(raiz, inicio);
            
            while (folha != null) {
                for (int i = 0; i < folha.chaves.size(); i++) {
                    int chave = folha.chaves.get(i);
                    if (chave >= inicio && chave <= fim) {
                        resultados.add(folha.posicoes.get(i));
                    } else if (chave > fim) {
                        return resultados;
                    }
                }
                folha = folha.proximo;
            }
            return resultados;
        } finally {
            arvoreLock.readLock().unlock();
        }
    }

    // ---- Utilitários ----
    private int buscaBinaria(List<Integer> lista, int chave) {
        int low = 0;
        int high = lista.size() - 1;
        
        while (low <= high) {
            int mid = (low + high) >>> 1;
            int cmp = lista.get(mid).compareTo(chave);
            
            if (cmp < 0) {
                low = mid + 1;
            } else if (cmp > 0) {
                high = mid - 1;
            } else {
                return mid; // Encontrou
            }
        }
        return -(low + 1); // Não encontrado
    }

    // ---- Monitoramento ----
    public Estatisticas getEstatisticas() {
        return new Estatisticas(
            contadorOperacoes.get(),
            calcularProfundidade(raiz),
            contarNos(raiz)
        );
    }

    private int calcularProfundidade(Node no) {
        if (no.isFolha) return 1;
        return 1 + calcularProfundidade(no.filhos.get(0));
    }

    private int contarNos(Node no) {
        if (no.isFolha) return 1;
        int total = 1;
        for (Node filho : no.filhos) {
            total += contarNos(filho);
        }
        return total;
    }

    public record Estatisticas(
        int totalOperacoes,
        int profundidade,
        int totalNos
    ) {}
}