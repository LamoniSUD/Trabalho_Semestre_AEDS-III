/*package io;

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
import java.util.zip.CRC32; // Para checksum

public class GerenciadorPerfumes implements AutoCloseable {
    // ============== CONFIGURAÇÕES ==============
    private static final int RECORD_SIZE = 256;       // Tamanho fixo por registro (bytes)
    private static final int SEGMENT_COUNT = 16;      // Número de segmentos para locks
    private static final int BUFFER_POOL_SIZE = 100;  // Capacidade do pool de buffers
    private static final double LOAD_FACTOR = 0.75;   // Fator para compactação automática
    private static final String WAL_EXTENSION = ".wal";
    private static final int ID_SIZE = Integer.BYTES; // Tamanho do ID
    private static final int NAME_LENGTH_SIZE = Integer.BYTES; // Tamanho para armazenar o comprimento do nome
    private static final int CHECKSUM_SIZE = Long.BYTES; // Tamanho do checksum CRC32

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
    private final ReentrantReadWriteLock globalFileLock = new ReentrantReadWriteLock(true); // Lock para operações de arquivo globais (ex: compactação)

    // ============== MONITORAMENTO ==============
    private final AtomicLong totalOperacoes = new AtomicLong(0);
    private final AtomicInteger registrosAtivos = new AtomicInteger(0);
    private final AtomicLong transactionCounter = new AtomicLong(0);

    // ============== CONSTRUTOR ==============
    public GerenciadorPerfumes(Arvore_BPlus arvore, String filePath) throws IOException {
        Objects.requireNonNull(arvore, "Árvore B+ não pode ser nula");
        Objects.requireNonNull(filePath, "Caminho do arquivo não pode ser nulo");

        this.arvore = arvore;
        this.arquivoPath = Paths.get(filePath);
        
        this.bufferPool = new BufferPool(BUFFER_POOL_SIZE, RECORD_SIZE, true);
        this.arquivoRAF = new RandomAccessFile(filePath, "rw");
        this.arquivoChannel = arquivoRAF.getChannel();
        this.wal = new TransactionLogger(arquivoPath.toString() + WAL_EXTENSION);

        this.segmentLocks = new ArrayList<>(SEGMENT_COUNT);
        for (int i = 0; i < SEGMENT_COUNT; i++) {
            segmentLocks.add(new ReentrantReadWriteLock(true));
        }

        this.batchExecutor = Executors.newWorkStealingPool();
        this.maintenanceExecutor = Executors.newSingleThreadScheduledExecutor();

        inicializarArquivo();
        recoverFromCrash();
        //agendarManutencoes();
    }

    // ============== OPERAÇÕES CRUD ==============
    public void criar(Perfume perfume) throws IOException {
        validarPerfume(perfume);
        long txId = transactionCounter.incrementAndGet();
        ByteBuffer buffer = bufferPool.borrowBuffer();

        globalFileLock.readLock().lock(); // Bloqueia escrita durante a compactação
        try {
            byte[] novoRegistroBytes = serializarParaBuffer(perfume, buffer);
            wal.logTransaction("CREATE", txId, perfume.getId(), new byte[RECORD_SIZE], novoRegistroBytes);

            long posicao = alocarPosicao();
            lockSegmento(posicao).writeLock().lock();
            try {
                escreverNoArquivo(posicao, buffer);
                arvore.insertComPosicao(perfume, posicao);
                registrosAtivos.incrementAndGet();
                wal.logTransaction("COMMIT", txId, perfume.getId(), null, null);
                totalOperacoes.incrementAndGet();
            } finally {
                lockSegmento(posicao).writeLock().unlock();
            }
        } catch (Exception e) {
            wal.logTransaction("ROLLBACK", txId, perfume.getId(), null, null);
            throw new IOException("Falha ao criar perfume: " + e.getMessage(), e);
        } finally {
            bufferPool.returnBuffer(buffer);
            globalFileLock.readLock().unlock();
        }
    }

    public Optional<Perfume> buscar(int id) throws IOException {
        globalFileLock.readLock().lock();
        try {
            Long posicao = arvore.buscarPosicao(id);
            if (posicao == null) {
                return Optional.empty();
            }

            ByteBuffer buffer = bufferPool.borrowBuffer();
            try {
                lockSegmento(posicao).readLock().lock();
                try {
                    lerDoArquivo(posicao, buffer);
                    if (!validarChecksum(buffer)) {
                        System.err.println("Erro de checksum ao ler perfume ID: " + id + " na posição: " + posicao);
                        return Optional.empty(); // Dados corrompidos
                    }
                    Perfume perfume = desserializarBuffer(buffer);
                    return Optional.ofNullable(perfume.getId() == id ? perfume : null);
                } finally {
                    lockSegmento(posicao).readLock().unlock();
                }
            } finally {
                bufferPool.returnBuffer(buffer);
            }
        } finally {
            globalFileLock.readLock().unlock();
        }
    }

    public void atualizar(Perfume perfume) throws IOException {
        validarPerfume(perfume);
        long txId = transactionCounter.incrementAndGet();
        ByteBuffer bufferNovo = bufferPool.borrowBuffer();
        ByteBuffer bufferAntigo = bufferPool.borrowBuffer();

        globalFileLock.readLock().lock();
        try {
            Long posicaoExistente = arvore.buscarPosicao(perfume.getId());
            if (posicaoExistente == null) {
                throw new IOException("Perfume com ID " + perfume.getId() + " não encontrado para atualização.");
            }

            lockSegmento(posicaoExistente).writeLock().lock();
            try {
                lerDoArquivo(posicaoExistente, bufferAntigo); // Lê estado antigo para WAL
                byte[] oldBytes = bufferAntigo.array(); // Copia os bytes antes de usar o buffer novamente

                byte[] novosBytes = serializarParaBuffer(perfume, bufferNovo); // Prepara novos bytes
                wal.logTransaction("UPDATE", txId, perfume.getId(), oldBytes, novosBytes);

                escreverNoArquivo(posicaoExistente, bufferNovo);
                wal.logTransaction("COMMIT", txId, perfume.getId(), null, null);
                totalOperacoes.incrementAndGet();
            } catch (Exception e) {
                wal.logTransaction("ROLLBACK", txId, perfume.getId(), null, null);
                throw new IOException("Falha ao atualizar perfume", e);
            } finally {
                lockSegmento(posicaoExistente).writeLock().unlock();
            }
        } finally {
            bufferPool.returnBuffer(bufferNovo);
            bufferPool.returnBuffer(bufferAntigo);
            globalFileLock.readLock().unlock();
        }
    }

    public void deletar(int id) throws IOException {
        long txId = transactionCounter.incrementAndGet();
        
        globalFileLock.readLock().lock();
        try {
            Long posicaoExistente = arvore.buscarPosicao(id);
            if (posicaoExistente == null) {
                throw new IOException("Perfume com ID " + id + " não encontrado para remoção.");
            }

            ByteBuffer bufferAntigo = bufferPool.borrowBuffer();
            lockSegmento(posicaoExistente).writeLock().lock();
            try {
                lerDoArquivo(posicaoExistente, bufferAntigo);
                byte[] oldBytes = bufferAntigo.array();
                
                wal.logTransaction("DELETE", txId, id, oldBytes, new byte[RECORD_SIZE]); // Loga o estado antigo
                
                boolean removidoDaArvore = arvore.remover(id); // Remove da árvore B+ (remoção lógica)

                if (!removidoDaArvore) { // Deveria ser true se posicaoExistente não era nula
                    throw new IOException("Erro interno: Perfume ID " + id + " não removido da árvore.");
                }

                registrosAtivos.decrementAndGet();
                wal.logTransaction("COMMIT", txId, id, null, null);
                totalOperacoes.incrementAndGet();
            } catch (Exception e) {
                wal.logTransaction("ROLLBACK", txId, id, null, null);
                throw new IOException("Falha ao deletar perfume", e);
            } finally {
                lockSegmento(posicaoExistente).writeLock().unlock();
                bufferPool.returnBuffer(bufferAntigo);
            }
        } finally {
            globalFileLock.readLock().unlock();
        }
    }

    // ============== OPERAÇÕES EM LOTE ==============
    public CompletableFuture<Void> processarLote(List<Perfume> perfumes) {
        return CompletableFuture.runAsync(() -> {
            long txId = transactionCounter.incrementAndGet();
            wal.logTransaction("BATCH_START", txId, -1, null, null);

            globalFileLock.readLock().lock();
            try {
                for (Perfume p : perfumes) {
                    try {
                        validarPerfume(p);
                        ByteBuffer buffer = bufferPool.borrowBuffer();
                        try {
                            byte[] dados = serializarParaBuffer(p, buffer);
                            long posicao = alocarPosicao();
                            
                            lockSegmento(posicao).writeLock().lock();
                            try {
                                wal.logTransaction("BATCH_ITEM_CREATE", txId, p.getId(), new byte[RECORD_SIZE], dados);
                                escreverNoArquivo(posicao, buffer);
                                arvore.insertComPosicao(p, posicao);
                                registrosAtivos.incrementAndGet();
                            } finally {
                                lockSegmento(posicao).writeLock().unlock();
                            }
                        } finally {
                            bufferPool.returnBuffer(buffer);
                        }
                    } catch (Exception e) {
                        System.err.println("Erro ao processar perfume " + p.getId() + " no lote: " + e.getMessage());
                        wal.logTransaction("BATCH_ITEM_FAIL", txId, p.getId(), null, null);
                        // Continua processando outros itens, mas o lote será marcado como falho se um COMMIT não for emitido.
                    }
                }
                wal.logTransaction("BATCH_COMMIT", txId, -1, null, null);
                totalOperacoes.incrementAndGet(); // Contabiliza o lote como uma operação
            } catch (Exception e) {
                wal.logTransaction("BATCH_ROLLBACK", txId, -1, null, null);
                throw new CompletionException(e);
            } finally {
                globalFileLock.readLock().unlock();
            }
        }, batchExecutor);
    }

    // ============== COMPACTAÇÃO ==============
    private synchronized void compactar() throws IOException {
        if (registrosAtivos.get() == 0 && arquivoChannel.size() < (RECORD_SIZE + CHECKSUM_SIZE + ID_SIZE)) {
            System.out.println("Arquivo de dados vazio ou quase vazio. Pulando compactação.");
            return;
        }

        long txId = transactionCounter.incrementAndGet();
        wal.logTransaction("COMPACT_START", txId, -1, null, null);

        globalFileLock.writeLock().lock(); // Bloqueia todas as operações de arquivo durante a compactação
        Path tempFile = null;
        try {
            tempFile = Files.createTempFile(arquivoPath.getParent(), "compact_", ".tmp");
            try (FileChannel tempChannel = FileChannel.open(tempFile, StandardOpenOption.WRITE)) {
                List<Integer> todosIds = arvore.buscarTodosIds();
                
                ByteBuffer buffer = bufferPool.borrowBuffer();
                long novaPosicao = 0;
                int novosRegistrosAtivosCount = 0;

                try {
                    for (int id : todosIds) {
                        Long posAntiga = arvore.buscarPosicao(id);
                        if (posAntiga == null) {
                            continue; // Registro foi deletado da árvore entre a coleta e agora
                        }

                        buffer.clear();
                        try {
                            // Usar o canal principal diretamente, pois o globalFileLock já o protege
                            lerDoArquivoInterno(posAntiga, buffer); 
                            if (!validarChecksum(buffer)) {
                                System.err.println("Checksum inválido para ID " + id + " na posição " + posAntiga + " durante compactação. Pulando registro.");
                                continue; // Pula registro corrompido
                            }
                            // O buffer já está com dados válidos e com flip() feito pelo lerDoArquivoInterno
                            
                            // AQUI: Atualiza a posição na árvore B+ antes de escrever no arquivo temporário
                            // Isso é importante para que, se houver um crash, a árvore aponte para o arquivo temporário.
                            arvore.atualizarPosicao(id, posAntiga, novaPosicao);

                            buffer.rewind(); // Prepara o buffer para escrita no canal temporário
                            tempChannel.write(buffer, novaPosicao);
                            novaPosicao += RECORD_SIZE;
                            novosRegistrosAtivosCount++;
                        } catch (IOException e) {
                            System.err.println("Erro ao ler registro ID " + id + " na posição " + posAntiga + " para compactação: " + e.getMessage());
                            // Continua para o próximo registro
                        }
                    }
                } finally {
                    bufferPool.returnBuffer(buffer);
                }

                tempChannel.force(true); // Garante que todos os dados compactados estejam no disco
                substituirArquivo(tempFile); // Troca atômica do arquivo principal
                registrosAtivos.set(novosRegistrosAtivosCount); // Atualiza o contador
                wal.logTransaction("COMPACT_SUCCESS", txId, -1, null, null);
                totalOperacoes.incrementAndGet();
                System.out.println("Compactação concluída. Registros ativos: " + registrosAtivos.get());
            } finally {
                // Tenta excluir o arquivo temporário de qualquer forma
                Files.deleteIfExists(tempFile);
            }
        } catch (Exception e) {
            wal.logTransaction("COMPACT_FAIL", txId, -1, null, null);
            System.err.println("Falha fatal na compactação: " + e.getMessage());
            throw new IOException("Falha na compactação", e);
        } finally {
            globalFileLock.writeLock().unlock(); // Libera o lock global
        }
    }

    // ============== RECUPERAÇÃO ==============
    private void recoverFromCrash() throws IOException {
        System.out.println("Iniciando recuperação de crash...");
        if (!Files.exists(arquivoPath.resolveSibling(arquivoPath.getFileName() + WAL_EXTENSION))) {
            System.out.println("Nenhum arquivo WAL encontrado. Recuperação não necessária.");
            return;
        }

        List<TransactionLogger.Transacao> transacoesPendentes = wal.getTransacoesPendentes();
        if (transacoesPendentes.isEmpty()) {
            System.out.println("Arquivo WAL existe, mas não há transações pendentes para recuperar.");
            wal.limparTransacoesProcessadas(); // Limpa WAL vazio
            return;
        }

        System.out.println("Recuperando " + transacoesPendentes.size() + " transações pendentes...");
        for (TransactionLogger.Transacao tx : transacoesPendentes) {
            try {
                switch (tx.operacao()) {
                    case "CREATE":
                        // Se a transação CREATE não foi COMMITADA, remove da árvore (se já inserida) ou ignora
                        // Se o item já foi escrito, o compactador o removerá na próxima vez se não estiver na árvore.
                        if (tx.status().equals("COMMIT") && !arvore.contem(tx.id())) {
                            reaplicarOperacao(tx);
                        } else if (tx.status().equals("ROLLBACK") || !tx.status().equals("COMMIT")) {
                            arvore.remover(tx.id()); // Remove qualquer resquício
                        }
                        break;
                    case "UPDATE":
                        // Se a transação UPDATE não foi COMMITADA, reverte para o estado antigo
                        if (tx.status().equals("COMMIT")) {
                            reaplicarOperacao(tx);
                        } else if (tx.status().equals("ROLLBACK") || !tx.status().equals("COMMIT")) {
                            // Reverte para o estado antigo. Pega o buffer, escreve o oldData.
                            ByteBuffer buffer = bufferPool.borrowBuffer();
                            try {
                                buffer.put(tx.dadosAntigos());
                                buffer.flip();
                                escreverNoArquivo(arvore.buscarPosicao(tx.id()), buffer); // Assume que a posição está correta na árvore
                            } finally {
                                bufferPool.returnBuffer(buffer);
                            }
                        }
                        break;
                    case "DELETE":
                        // Se a transação DELETE não foi COMMITADA, insere de volta na árvore
                        if (tx.status().equals("COMMIT") && arvore.contem(tx.id())) {
                            // Já removido, não precisa fazer nada no arquivo (o compactador irá limpar)
                        } else if (tx.status().equals("ROLLBACK") || !tx.status().equals("COMMIT")) {
                            // Se a deleção não commitou, o registro deve ser mantido na árvore e no arquivo.
                            // Assume que o oldData contém o registro completo para reinserção.
                            ByteBuffer buffer = bufferPool.borrowBuffer();
                            try {
                                buffer.put(tx.dadosAntigos());
                                buffer.flip();
                                Perfume p = desserializarBuffer(buffer);
                                if (p != null) {
                                    arvore.insertComPosicao(p, arvore.buscarPosicao(p.getId())); // Reinsere na posição original
                                    registrosAtivos.incrementAndGet();
                                }
                            } finally {
                                bufferPool.returnBuffer(buffer);
                            }
                        }
                        break;
                    case "BATCH_START":
                    case "BATCH_ITEM_CREATE":
                    case "BATCH_ITEM_FAIL":
                        // Para transações em lote não commitadas, assumimos rollback total.
                        // Limpeza mais complexa pode ser necessária, mas para este exemplo,
                        // itens não confirmados serão considerados lixo e limpos na compactação.
                        // Se houver "BATCH_ITEM_CREATE" sem "BATCH_COMMIT", a árvore pode ter itens inválidos.
                        // O melhor é re-varrer a árvore para ver o que realmente está lá.
                        if (!tx.status().equals("BATCH_COMMIT")) {
                            System.err.println("Transação de lote " + tx.txId() + " não commitada. Revertendo itens conhecidos.");
                            // Esta parte é heurística: se você não tem um log de todas as operações,
                            // o melhor é depender da compactação para limpar dados órfãos.
                        }
                        break;
                    case "COMPACT_START":
                    case "COMPACT_FAIL":
                        // Se a compactação falhou ou não commitou, o arquivo temporário (se existir) deve ser ignorado/excluído.
                        // O arquivo principal original ainda deve estar válido.
                        System.err.println("Compactação TxId " + tx.txId() + " não commitada. Verifique integridade do arquivo principal.");
                        break;
                    // Ignora BATCH_COMMIT, COMPACT_SUCCESS, COMMIT, ROLLBACK de itens individuais
                    default:
                        break;
                }
            } catch (Exception e) {
                System.err.println("Falha ao recuperar transação " + tx.txId() + " (" + tx.operacao() + "): " + e.getMessage());
            }
        }
        
        wal.limparTransacoesProcessadas(); // Remove transações do WAL que foram processadas
        System.out.println("Recuperação de crash concluída.");
        // Após recuperação, é prudente forçar uma compactação para limpar quaisquer inconsistências.
        compactar(); 
    }

    private void reaplicarOperacao(TransactionLogger.Transacao tx) throws IOException {
        ByteBuffer buffer = bufferPool.borrowBuffer();
        try {
            switch (tx.operacao()) {
                case "CREATE":
                    if (tx.novoDado() != null && tx.novoDado().length > 0) {
                        buffer.put(tx.novoDado());
                        buffer.flip();
                        Perfume p = desserializarBuffer(buffer);
                        if (p != null) {
                             // Re-criar (assumindo que a posição original foi perdida ou é irrelevante para re-criação)
                            long novaPos = alocarPosicao(); 
                            escreverNoArquivo(novaPos, buffer.rewind());
                            arvore.insertComPosicao(p, novaPos);
                            registrosAtivos.incrementAndGet();
                        }
                    }
                    break;
                case "UPDATE":
                    if (tx.novoDado() != null && tx.novoDado().length > 0) {
                        buffer.put(tx.novoDado());
                        buffer.flip();
                        // Assume que a posição do item está na árvore para o ID
                        escreverNoArquivo(arvore.buscarPosicao(tx.id()), buffer.rewind());
                    }
                    break;
                case "DELETE":
                    // O delete já é lógico. Se o COMMIT não foi para o WAL, a remoção da árvore não ocorreu.
                    // Se o COMMIT foi para o WAL, o item já foi logicamente removido.
                    // Não há necessidade de re-aplicar o delete no arquivo, a compactação limpará.
                    break;
                default:
                    System.err.println("Operação de recuperação não suportada: " + tx.operacao());
            }
        } finally {
            bufferPool.returnBuffer(buffer);
        }
    }


    // ============== MÉTODOS AUXILIARES ==============
    private ReadWriteLock lockSegmento(long posicao) {
        int segmentIndex = (int)((posicao / RECORD_SIZE) % SEGMENT_COUNT);
        if (segmentIndex < 0) segmentIndex = 0;
        if (segmentIndex >= SEGMENT_COUNT) segmentIndex = SEGMENT_COUNT - 1; // Sanity check
        return segmentLocks.get(segmentIndex);
    }

    private byte[] serializarParaBuffer(Perfume perfume, ByteBuffer buffer) {
        buffer.clear();
        int dataStart = CHECKSUM_SIZE; // Onde os dados reais começam
        buffer.position(dataStart); // Pula o espaço do checksum
        
        buffer.putInt(perfume.getId());
        
        byte[] nomeBytes = perfume.getNome().getBytes(java.nio.charset.StandardCharsets.UTF_8);
        
        int maxNomeLength = RECORD_SIZE - ID_SIZE - NAME_LENGTH_SIZE - CHECKSUM_SIZE;
        if (nomeBytes.length > maxNomeLength) {
            nomeBytes = Arrays.copyOf(nomeBytes, maxNomeLength);
        }
        
        buffer.putInt(nomeBytes.length);
        buffer.put(nomeBytes);
        
        while (buffer.hasRemaining()) {
            buffer.put((byte) 0);
        }
        
        // Calcula e insere o checksum no início do buffer
        long checksum = calcularChecksum(buffer.array(), dataStart, RECORD_SIZE - dataStart);
        buffer.rewind(); // Volta ao início para escrever o checksum
        buffer.putLong(checksum);
        
        buffer.flip(); // Prepara para leitura pelo canal
        
        byte[] serializedData = new byte[RECORD_SIZE];
        buffer.get(serializedData); // Copia para um array de bytes para o WAL
        buffer.rewind(); // Reinicia o buffer para que possa ser usado pelo caller
        return serializedData;
    }

    private Perfume desserializarBuffer(ByteBuffer buffer) {
        buffer.flip(); // Prepara para leitura

        if (buffer.remaining() < RECORD_SIZE) {
            System.err.println("Buffer insuficiente para desserialização completa.");
            return null;
        }

        // Pula o checksum para desserializar os dados
        buffer.position(CHECKSUM_SIZE); 

        int id = buffer.getInt();
        int nomeLength = buffer.getInt();
        
        if (nomeLength < 0 || nomeLength > buffer.remaining()) {
            System.err.println("NomeLength inválido (" + nomeLength + ") ou buffer insuficiente. ID: " + id);
            return null;
        }

        byte[] nomeBytes = new byte[nomeLength];
        buffer.get(nomeBytes);
        String nome = new String(nomeBytes, java.nio.charset.StandardCharsets.UTF_8);
        return new Perfume(id, nome);
    }

    private boolean validarChecksum(ByteBuffer buffer) {
        if (buffer.remaining() < RECORD_SIZE) { // Garante que o buffer tem tamanho suficiente
            return false;
        }
        ByteBuffer temp = buffer.duplicate(); // Duplica para não alterar a posição original
        temp.rewind();
        long storedChecksum = temp.getLong(); // Lê o checksum armazenado
        
        byte[] data = new byte[RECORD_SIZE - CHECKSUM_SIZE];
        temp.get(data); // Lê os dados após o checksum

        long calculatedChecksum = calcularChecksum(data, 0, data.length);
        return storedChecksum == calculatedChecksum;
    }

    private long calcularChecksum(byte[] data, int offset, int length) {
        CRC32 crc = new CRC32();
        crc.update(data, offset, length);
        return crc.getValue();
    }

    private void escreverNoArquivo(long posicao, ByteBuffer buffer) throws IOException {
        buffer.rewind();
        arquivoChannel.write(buffer, posicao);
        arquivoChannel.force(true); // Garante que os dados sejam gravados no disco
    }

    // Leitura interna, sem validação de lock de segmento (assumindo lock global)
    private void lerDoArquivoInterno(long posicao, ByteBuffer buffer) throws IOException {
        buffer.clear();
        int bytesRead = arquivoChannel.read(buffer, posicao);
        if (bytesRead == -1 || bytesRead < RECORD_SIZE) {
            throw new EOFException("Fim de arquivo inesperado ou registro parcial na posição " + posicao);
        }
        buffer.flip();
    }

    private void lerDoArquivo(long posicao, ByteBuffer buffer) throws IOException {
        buffer.clear();
        int bytesRead = arquivoChannel.read(buffer, posicao);
        if (bytesRead == -1 || bytesRead < RECORD_SIZE) {
            throw new EOFException("Fim de arquivo inesperado ou registro parcial na posição " + posicao);
        }
        buffer.flip();
    }

    private long alocarPosicao() throws IOException {
        // Para simplicidade, aloca no final. Em produção, usaria um gerenciador de espaços livres.
        return arquivoChannel.size();
    }

    private void inicializarArquivo() throws IOException {
        if (arquivoChannel.size() == 0) {
            // Pode escrever um cabeçalho inicial para o arquivo de dados.
            // Ex: um "magic number" para identificar o formato do arquivo.
            ByteBuffer header = ByteBuffer.allocate(8); // Exemplo de 8 bytes de cabeçalho
            header.putLong(0xDEADBEEFL); // Magic number
            header.flip();
            arquivoChannel.write(header, 0);
            arquivoChannel.force(true);
        } else {
            // Se o arquivo existe, pode-se ler o cabeçalho e verificar integridade
            // ou carregar metadados.
        }
        registrosAtivos.set(arvore.buscarTodosIds().size()); // Atualiza o contador de ativos com base na árvore
    }

    private void substituirArquivo(Path tempFile) throws IOException {
        // Fechar os recursos do arquivo principal ANTES de tentar mover/renomear
        arquivoChannel.close();
        arquivoRAF.close();

        Files.move(tempFile, arquivoPath, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);

        // Reabrir os recursos para o (novo) arquivo principal
        this.arquivoRAF = new RandomAccessFile(arquivoPath.toString(), "rw");
        this.arquivoChannel = arquivoRAF.getChannel();
    }

    private void validarPerfume(Perfume perfume) {
        Objects.requireNonNull(perfume, "Perfume não pode ser nulo.");
        if (perfume.getId() <= 0) {
            throw new IllegalArgumentException("ID do perfume deve ser positivo.");
        }
        Objects.requireNonNull(perfume.getNome(), "Nome do perfume não pode ser nulo.");
        if (perfume.getNome().trim().isEmpty()) {
            throw new IllegalArgumentException("Nome do perfume não pode ser vazio.");
        }
    }

    // ============== GERENCIAMENTO DE RECURSOS ==============
    @Override
    public void close() throws IOException {
        System.out.println("GerenciadorPerfumes: Iniciando desligamento...");
        
        maintenanceExecutor.shutdown();
        batchExecutor.shutdown();
        
        try {
            if (!maintenanceExecutor.awaitTermination(10, TimeUnit.SECONDS)) {
                maintenanceExecutor.shutdownNow();
            }
            if (!batchExecutor.awaitTermination(10, TimeUnit.SECONDS)) {
                batchExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            System.err.println("GerenciadorPerfumes: Desligamento interrompido.");
        }
        
        bufferPool.close();
        wal.close();
        
        if (arquivoChannel != null && arquivoChannel.isOpen()) {
            arquivoChannel.close();
        }
        if (arquivoRAF != null) {
            arquivoRAF.close();
        }
        System.out.println("GerenciadorPerfumes: Desligamento concluído.");
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

    private double calcularTaxaFragmentacao() {
        try {
            long tamanhoTotalArquivo = arquivoChannel.size();
            long espacoAtivo = (long) registrosAtivos.get() * RECORD_SIZE;
            if (tamanhoTotalArquivo == 0) return 0.0;
            // Considerando o cabeçalho no tamanho total, mas não no espaço ativo
            long espacoUtil = espacoAtivo + (tamanhoTotalArquivo > 8 ? 8 : tamanhoTotalArquivo); // Adiciona o cabeçalho ao espaço útil
            return (double) (tamanhoTotalArquivo - espacoUtil) / tamanhoTotalArquivo;
        } catch (IOException e) {
            System.err.println("Erro ao calcular fragmentação: " + e.getMessage());
            return -1.0; // Indica erro
        }
    }

    public record Estatisticas(
        long totalOperacoes,
        int registrosAtivos,
        int buffersDisponiveis,
        double taxaFragmentacao
    ) {}
}*/