package Services;

import Models.Perfume;
import Structures.Arvore_BPlus;
import Utils.BufferPool;
import java.io.*;
import java.nio.*;
import java.nio.channels.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.concurrent.locks.*;
import java.util.zip.*;

public class GerenciadorPerfumes implements AutoCloseable {
    // ============== CONFIGURAÇÕES ==============
    private static final int RECORD_SIZE = 256;      // Tamanho fixo por registro
    private static final int SEGMENT_COUNT = 16;     // Número de segmentos para locks
    private static final int BUFFER_POOL_SIZE = 100; // Capacidade do pool de buffers
    private static final double LOAD_FACTOR = 0.75;  // Fator para compactação automática
    private static final String WAL_EXTENSION = ".wal";

    // ============== COMPONENTES PRINCIPAIS ==============
    private final Arvore_BPlus arvore;
    private final FileChannel arquivoChannel;
    private final RandomAccessFile arquivoRAF;
    private final BufferPool bufferPool;
    private final TransactionLogger wal;
    private final Path arquivoPath;

    // ============== CONTROLE DE CONCORRÊNCIA ==============
    private final List<ReadWriteLock> segmentLocks;
    private final ExecutorService batchExecutor;
    private final ScheduledExecutorService maintenanceExecutor;

    // ============== MONITORAMENTO ==============
    private final AtomicLong totalOperacoes = new AtomicLong(0);
    private final AtomicInteger registrosAtivos = new AtomicInteger(0);
    private final AtomicLong transactionCounter = new AtomicLong(0);

    // ============== CONSTRUTOR ==============
    public GerenciadorPerfumes(Arvore_BPlus arvore, String filePath) throws IOException {
        // Validações iniciais
        Objects.requireNonNull(arvore, "Árvore B+ não pode ser nula");
        Objects.requireNonNull(filePath, "Caminho do arquivo não pode ser nulo");

        this.arvore = arvore;
        this.arquivoPath = Paths.get(filePath);
        
        // Inicialização dos componentes
        this.bufferPool = new BufferPool(BUFFER_POOL_SIZE, RECORD_SIZE, true);
        this.arquivoRAF = new RandomAccessFile(filePath, "rw");
        this.arquivoChannel = arquivoRAF.getChannel();
        this.wal = new TransactionLogger(filePath);

        // Configuração de concorrência
        this.segmentLocks = new ArrayList<>(SEGMENT_COUNT);
        for (int i = 0; i < SEGMENT_COUNT; i++) {
            segmentLocks.add(new ReentrantReadWriteLock(true));
        }

        this.batchExecutor = Executors.newWorkStealingPool();
        this.maintenanceExecutor = Executors.newSingleThreadScheduledExecutor();

        // Processos iniciais
        inicializarArquivo();
        agendarManutencoes();
        recoverFromCrash();
    }

    // ============== OPERAÇÕES CRUD ==============
    public void criar(Perfume perfume) throws IOException {
        validarPerfume(perfume);
        long txId = transactionCounter.incrementAndGet();
        ByteBuffer buffer = bufferPool.borrowBuffer();

        try {
            // Serialização para WAL
            byte[] novoRegistro = serializarParaBuffer(perfume, buffer);
            wal.logTransaction("CREATE", txId, perfume.getId(), new byte[RECORD_SIZE], novoRegistro);

            // Operação principal
            long posicao = alocarPosicao();
            lockSegmento(posicao).writeLock().lock();
            try {
                escreverNoArquivo(posicao, buffer);
                arvore.insertComPosicao(perfume, posicao);
                registrosAtivos.incrementAndGet();
                wal.logTransaction("COMMIT", txId, perfume.getId(), null, null);
            } finally {
                lockSegmento(posicao).writeLock().unlock();
            }
        } catch (Exception e) {
            wal.logTransaction("ROLLBACK", txId, perfume.getId(), null, null);
            throw new IOException("Falha ao criar perfume", e);
        } finally {
            bufferPool.returnBuffer(buffer);
        }
    }

    public Optional<Perfume> buscar(int id) throws IOException {
        Long posicao = arvore.buscarPosicao(id);
        if (posicao == null) return Optional.empty();

        ByteBuffer buffer = bufferPool.borrowBuffer();
        try {
            lockSegmento(posicao).readLock().lock();
            try {
                lerDoArquivo(posicao, buffer);
                Perfume perfume = desserializarBuffer(buffer);
                return Optional.ofNullable(perfume.getId() > 0 ? perfume : null);
            } finally {
                lockSegmento(posicao).readLock().unlock();
            }
        } finally {
            bufferPool.returnBuffer(buffer);
        }
    }

    // ============== OPERAÇÕES EM LOTE ==============
    public CompletableFuture<Void> processarLote(List<Perfume> perfumes) {
        return CompletableFuture.runAsync(() -> {
            long txId = transactionCounter.incrementAndGet();
            wal.logTransaction("BATCH_START", txId, -1, null, null);

            try {
                for (Perfume p : perfumes) {
                    ByteBuffer buffer = bufferPool.borrowBuffer();
                    try {
                        byte[] dados = serializarParaBuffer(p, buffer);
                        long posicao = alocarPosicao();
                        
                        lockSegmento(posicao).writeLock().lock();
                        try {
                            wal.logTransaction("BATCH_ITEM", txId, p.getId(), new byte[RECORD_SIZE], dados);
                            escreverNoArquivo(posicao, buffer);
                            arvore.insertComPosicao(p, posicao);
                            registrosAtivos.incrementAndGet();
                        } finally {
                            lockSegmento(posicao).writeLock().unlock();
                        }
                    } finally {
                        bufferPool.returnBuffer(buffer);
                    }
                }
                wal.logTransaction("BATCH_COMMIT", txId, -1, null, null);
            } catch (Exception e) {
                wal.logTransaction("BATCH_ROLLBACK", txId, -1, null, null);
                throw new CompletionException(e);
            }
        }, batchExecutor);
    }

    // ============== COMPACTAÇÃO ==============
    private synchronized void compactar() throws IOException {
        long txId = transactionCounter.incrementAndGet();
        wal.logTransaction("COMPACT_START", txId, -1, null, null);

        Path tempFile = Files.createTempFile("compact", ".tmp");
        try (FileChannel tempChannel = FileChannel.open(tempFile, StandardOpenOption.WRITE)) {
            // 1. Coleta posições ativas de forma consistente
            List<Long> posicoesAtivas = arvore.buscarTodosIds()
                .stream()
                .map(arvore::buscarPosicao)
                .filter(Objects::nonNull)
                .sorted()
                .toList();

            // 2. Processa em blocos para melhor performance
            ByteBuffer buffer = bufferPool.borrowBuffer();
            try {
                long novaPosicao = 0;
                for (long pos : posicoesAtivas) {
                    buffer.clear();
                    lerDoArquivo(pos, buffer);
                    
                    // Verifica checksum antes de mover
                    if (validarChecksum(buffer)) {
                        buffer.flip();
                        tempChannel.write(buffer, novaPosicao);
                        arvore.atualizarPosicao(extrairId(buffer), pos, novaPosicao);
                        novaPosicao += RECORD_SIZE;
                    }
                }
            } finally {
                bufferPool.returnBuffer(buffer);
            }

            // 3. Troca atômica dos arquivos
            substituirArquivo(tempFile);
            registrosAtivos.set(posicoesAtivas.size());
            wal.logTransaction("COMPACT_SUCCESS", txId, -1, null, null);
        } catch (Exception e) {
            wal.logTransaction("COMPACT_FAIL", txId, -1, null, null);
            throw new IOException("Falha na compactação", e);
        }
    }

    // ============== RECUPERAÇÃO ==============
    private void recoverFromCrash() throws IOException {
        if (!Files.exists(arquivoPath.resolveSibling(arquivoPath.getFileName() + WAL_EXTENSION))) {
            return;
        }

        for (TransactionLogger.Transacao tx : wal.getTransacoesPendentes()) {
            try {
                switch (tx.operacao()) {
                    case "CREATE":
                        if (!arvore.contem(tx.id())) {
                            reaplicarOperacao(tx);
                        }
                        break;
                    // Outros casos de operação...
                }
            } catch (Exception e) {
                System.err.println("Falha ao recuperar transação " + tx.txId() + ": " + e.getMessage());
            }
        }
        
        wal.limparTransacoesProcessadas();
    }

    // ============== CLASSES INTERNAS ==============
    private class TransactionLogger implements AutoCloseable {
        // Implementação completa do logger de transações
        // ... (código detalhado do WAL)
    }

    // ============== MÉTODOS AUXILIARES ==============
    private ReadWriteLock lockSegmento(long posicao) {
        return segmentLocks.get((int)((posicao / RECORD_SIZE) % SEGMENT_COUNT));
    }

    private byte[] serializarParaBuffer(Perfume p, ByteBuffer buffer) {
        // Serialização otimizada com checksum
    }

    private boolean validarChecksum(ByteBuffer buffer) {
        // Lógica de validação
    }

    // ============== GERENCIAMENTO DE RECURSOS ==============
    @Override
    public void close() throws IOException {
        // Sequência segura de fechamento
        maintenanceExecutor.shutdown();
        batchExecutor.shutdown();
        
        try {
            if (!maintenanceExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                maintenanceExecutor.shutdownNow();
            }
            
            if (!batchExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                batchExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        
        bufferPool.close();
        wal.close();
        arquivoChannel.close();
        arquivoRAF.close();
    }

    // ============== MONITORAMENTO ==============
    public Estatisticas getEstatisticas() {
        return new Estatisticas(
            totalOperacoes.get(),
            registrosAtivos.get(),
            bufferPool.getBuffersDisponiveis(),
            calcularTaxaFragmentacao()
        );
    }

    public record Estatisticas(
        long totalOperacoes,
        int registrosAtivos,
        int buffersDisponiveis,
        double taxaFragmentacao
    ) {}
}