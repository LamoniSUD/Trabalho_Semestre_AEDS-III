package Services;

import Models.Perfume;
import Structures.Arvore_BPlus;
import Services.BufferPool;
import java.io.*;
import java.nio.*;
import java.nio.channels.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.concurrent.locks.*;

public class GerenciadorPerfumes implements AutoCloseable {
    // Configurações
    private static final int RECORD_SIZE = 256;
    private static final int SEGMENT_COUNT = 32;
    private static final int BUFFER_POOL_SIZE = 100;
    
    // Componentes principais
    private final Arvore_BPlus arvore;
    private final FileChannel arquivoChannel;
    private final RandomAccessFile arquivoRAF;
    private final BufferPool bufferPool;
    
    // Controle de concorrência
    private final List<ReadWriteLock> segmentLocks;
    private final ExecutorService batchExecutor;
    private final ScheduledExecutorService maintenanceExecutor;
    
    // Monitoramento
    private final AtomicLong totalOperacoes = new AtomicLong(0);
    private final AtomicInteger registrosAtivos = new AtomicInteger(0);

    // Construtor
    public GerenciadorPerfumes(Arvore_BPlus arvore, String filePath) throws IOException {
        this.arvore = Objects.requireNonNull(arvore);
        this.bufferPool = new BufferPool(BUFFER_POOL_SIZE, RECORD_SIZE);
        
        // Inicialização do arquivo
        this.arquivoRAF = new RandomAccessFile(filePath, "rw");
        this.arquivoChannel = arquivoRAF.getChannel();
        inicializarArquivo();
        
        // Locks granulares
        this.segmentLocks = new ArrayList<>(SEGMENT_COUNT);
        for (int i = 0; i < SEGMENT_COUNT; i++) {
            segmentLocks.add(new ReentrantReadWriteLock(true));
        }
        
        // Thread pools
        this.batchExecutor = Executors.newWorkStealingPool();
        this.maintenanceExecutor = Executors.newSingleThreadScheduledExecutor();
        agendarManutencoes();
    }

    // ---- Operações CRUD ----
    public void criar(Perfume perfume) throws IOException {
        Transaction txn = new Transaction("CREATE", perfume.getId());
        try {
            ByteBuffer buffer = bufferPool.borrowBuffer();
            try {
                serializarParaBuffer(perfume, buffer);
                long posicao = alocarPosicao();
                
                getSegmentLock(posicao).writeLock().lock();
                try {
                    escreverBuffer(posicao, buffer);
                    arvore.insertComPosicao(perfume, posicao);
                    registrosAtivos.incrementAndGet();
                    txn.commit();
                } finally {
                    getSegmentLock(posicao).writeLock().unlock();
                }
            } finally {
                bufferPool.returnBuffer(buffer);
            }
        } catch (Exception e) {
            txn.rollback();
            throw e;
        }
    }

    public Optional<Perfume> buscar(int id) throws IOException {
        Long posicao = arvore.buscarPosicao(id);
        if (posicao == null) return Optional.empty();
        
        ByteBuffer buffer = bufferPool.borrowBuffer();
        try {
            getSegmentLock(posicao).readLock().lock();
            try {
                lerBuffer(posicao, buffer);
                Perfume perfume = desserializarBuffer(buffer);
                return Optional.ofNullable(perfume.getId() > 0 ? perfume : null);
            } finally {
                getSegmentLock(posicao).readLock().unlock();
            }
        } finally {
            bufferPool.returnBuffer(buffer);
        }
    }

    // ---- Operações em Lote ----
    public CompletableFuture<Void> processarLote(List<Perfume> perfumes) {
        return CompletableFuture.runAsync(() -> {
            Transaction txn = new Transaction("BATCH", -1);
            try {
                for (Perfume p : perfumes) {
                    ByteBuffer buffer = bufferPool.borrowBuffer();
                    try {
                        serializarParaBuffer(p, buffer);
                        long posicao = alocarPosicao();
                        
                        getSegmentLock(posicao).writeLock().lock();
                        try {
                            escreverBuffer(posicao, buffer);
                            arvore.insertComPosicao(p, posicao);
                            registrosAtivos.incrementAndGet();
                        } finally {
                            getSegmentLock(posicao).writeLock().unlock();
                        }
                    } finally {
                        bufferPool.returnBuffer(buffer);
                    }
                }
                txn.commit();
            } catch (Exception e) {
                txn.rollback();
                throw new CompletionException(e);
            }
        }, batchExecutor);
    }

    // ---- Compactação Inteligente ----
    private void compactar() throws IOException {
        Path tempFile = Files.createTempFile("compact", ".tmp");
        try (FileChannel tempChannel = FileChannel.open(tempFile, 
             StandardOpenOption.CREATE, StandardOpenOption.WRITE)) {
            
            // Fase 1: Coleta de registros ativos
            List<Long> posicoesAtivas = arvore.buscarTodosIds()
                .stream()
                .map(arvore::buscarPosicao)
                .filter(Objects::nonNull)
                .sorted()
                .toList();
            
            // Fase 2: Cópia seletiva
            ByteBuffer buffer = ByteBuffer.allocateDirect(RECORD_SIZE);
            long novaPosicao = 0;
            
            for (long pos : posicoesAtivas) {
                buffer.clear();
                arquivoChannel.read(buffer, pos);
                buffer.flip();
                
                tempChannel.write(buffer, novaPosicao);
                arvore.atualizarPosicao(extrairId(buffer), pos, novaPosicao);
                novaPosicao += RECORD_SIZE;
            }
            
            // Fase 3: Troca atômica
            substituirArquivo(tempFile);
            registrosAtivos.set(posicoesAtivas.size());
        }
    }

    // ---- Gerenciamento de Recursos ----
    @Override
    public void close() throws IOException {
        maintenanceExecutor.shutdown();
        batchExecutor.shutdown();
        bufferPool.close();
        arquivoChannel.close();
        arquivoRAF.close();
    }

    // ---- Classes Internas ----
    private class Transaction {
        private final String id;
        private final List<Runnable> rollbackActions = new ArrayList<>();
        
        Transaction(String tipo, int perfumeId) {
            this.id = tipo + "_" + perfumeId + "_" + System.nanoTime();
        }
        
        void commit() {
            totalOperacoes.incrementAndGet();
        }
        
        void rollback() {
            rollbackActions.forEach(Runnable::run);
        }
    }
}