package Services;

import Models.Perfume;
import Structures.Arvore_BPlus;
import Structures.GerenciadorEspaco;
import Services.CriptografiaColunar;
import java.io.EOFException;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public class GerenciadorArquivos implements AutoCloseable {

    // Constantes 
    private static final int ESTIMATED_MAX_RECORD_SIZE = 1024;
    private static final int SEGMENT_COUNT = 32;
    private static final int BUFFER_POOL_SIZE = 100;
    private final CriptografiaColunar criptografador; 
    // Constantes do Cabeçalho do Arquivo 
    private static final int FILE_HEADER_VALID_BYTE_SIZE = 1;
    private static final int FILE_HEADER_FILE_SIZE_LONG_SIZE = Long.BYTES;
    private static final int FILE_HEADER_LAST_ID_INT_SIZE = Integer.BYTES;
    public static final int FILE_HEADER_TOTAL_SIZE =
        FILE_HEADER_VALID_BYTE_SIZE +
        FILE_HEADER_FILE_SIZE_LONG_SIZE +
        FILE_HEADER_LAST_ID_INT_SIZE;

    // Recursos do Arquivo 
    private final Arvore_BPlus arvore;
    private FileChannel arquivoChannel;
    private RandomAccessFile arquivoRAF;
    private final BufferPool bufferPool;
    private final GerenciadorEspaco gerenciadorEspaco;

    // Gerenciamento de Concorrência e Executores 
    private final ReadWriteLock gerenciadorLock = new ReentrantReadWriteLock(true);
    private final List<ReadWriteLock> segmentLocks;
    private final ExecutorService batchExecutor;
    private final ScheduledExecutorService maintenanceExecutor;

    //  Etado do Gerenciador 
    private final AtomicLong totalOperacoes = new AtomicLong(0);
    private final AtomicInteger registrosAtivos = new AtomicInteger(0);
    private final String filePath;

    // Propriedades do Cabeçalho em Memória 
    private boolean arquivoValido = false;
    private long tamanhoDoArquivo = 0L;
    private int ultimoId = 0;

    // Construtor 
    public GerenciadorArquivos(Arvore_BPlus arvore, String filePath) throws IOException, InterruptedException {
        // Inicializa o gerenciador de arquivos
        this.arvore = Objects.requireNonNull(arvore, "A Árvore B+ não pode ser nula.");
        this.filePath = Objects.requireNonNull(filePath, "O caminho do arquivo não pode ser nulo.");
        this.bufferPool = new BufferPool(BUFFER_POOL_SIZE, ESTIMATED_MAX_RECORD_SIZE);

        this.gerenciadorEspaco = new GerenciadorEspaco(filePath + ".freelist", FILE_HEADER_TOTAL_SIZE);

        this.arquivoRAF = new RandomAccessFile(filePath, "rw");
        this.arquivoChannel = arquivoRAF.getChannel();
        // Criando Chave de Criptografia de Colunas
        this.criptografador = new CriptografiaColunar("PERFUMEKEY"); 
        inicializarArquivo();
        lerCabecalhoDoArquivo();

        this.segmentLocks = new ArrayList<>(SEGMENT_COUNT);
        for (int i = 0; i < SEGMENT_COUNT; i++) {
            segmentLocks.add(new ReentrantReadWriteLock(true));
        }

        this.batchExecutor = Executors.newWorkStealingPool();
        this.maintenanceExecutor = Executors.newSingleThreadScheduledExecutor();

        recoverAndRebuildTree();
        agendarManutencoes();
    }

    // Métodos de Gerenciamento do Cabeçalho do Arquivo 

    //Inicializa o arquivo de dados, criando o cabeçalho se o arquivo estiver vazio.
    private void inicializarArquivo() throws IOException {
        if (arquivoChannel.size() == 0) {
            ByteBuffer header = ByteBuffer.allocate(FILE_HEADER_TOTAL_SIZE);
            header.put((byte) 1);
            header.putLong(FILE_HEADER_TOTAL_SIZE);
            header.putInt(0);
            header.flip();
            arquivoChannel.write(header, 0);
            arquivoChannel.force(true);
        }
    }

    //Lê o cabeçalho do arquivo para carregar o estado em memória.
    private void lerCabecalhoDoArquivo() throws IOException {
        if (arquivoChannel.size() < FILE_HEADER_TOTAL_SIZE) {
            this.arquivoValido = false;
            this.tamanhoDoArquivo = arquivoChannel.size();
            this.ultimoId = 0;
            return;
        }

        ByteBuffer headerBuffer = ByteBuffer.allocate(FILE_HEADER_TOTAL_SIZE);
        arquivoChannel.read(headerBuffer, 0);
        headerBuffer.flip();

        this.arquivoValido = headerBuffer.get() == 1;
        this.tamanhoDoArquivo = headerBuffer.getLong();
        this.ultimoId = headerBuffer.getInt();

        if (arquivoChannel.size() != this.tamanhoDoArquivo) {
            System.err.println("AVISO: Tamanho do arquivo real (" + arquivoChannel.size() + ") difere do tamanho registrado no cabeçalho (" + this.tamanhoDoArquivo + "). Isso pode indicar corrupção ou desligamento abrupto. A recuperação tentará corrigir.");
        }
    }
    
   // Escreve o estado do cabeçalho do arquivo no disco.
    private void escreverCabecalhoNoArquivo() throws IOException {
        gerenciadorLock.writeLock().lock();
        try {
            ByteBuffer headerBuffer = ByteBuffer.allocate(FILE_HEADER_TOTAL_SIZE);
            headerBuffer.put(this.arquivoValido ? (byte) 1 : (byte) 0);
            headerBuffer.putLong(this.tamanhoDoArquivo);
            headerBuffer.putInt(this.ultimoId);
            headerBuffer.flip();
            arquivoChannel.write(headerBuffer, 0);
            arquivoChannel.force(true);
        } finally {
            gerenciadorLock.writeLock().unlock();
        }
    }

    // Gera e retorna um novo ID sequencial.
    public synchronized int novoID() {
        return ++ultimoId;
    }

    // Métodos de CRUD 

    // Cria um novo registro de perfume no arquivo.
    public void criar(Perfume perfume) throws Exception {
        Transaction txn = new Transaction("CREATE", perfume.getId());
        try {
        	Perfume perfumeCriptografado = criptografarPerfume(perfume);
            byte[] dadosPerfumeBrutos = perfume.toByteArray();
            long posicao;
            Optional<GerenciadorEspaco.FreeBlock> freeBlockOpt = gerenciadorEspaco.getFreeBlock(dadosPerfumeBrutos.length + Integer.BYTES);
            if (freeBlockOpt.isPresent()) {
                posicao = freeBlockOpt.get().getOffset();
            } else {
                posicao = arquivoChannel.size();
            }

            getSegmentLock(posicao).writeLock().lock();
            try {
                escreverRegistro(posicao, dadosPerfumeBrutos, arquivoChannel);

                arvore.inserir(perfume.getId(), posicao);
                registrosAtivos.incrementAndGet();

                if (perfume.getId() > this.ultimoId) {
                    this.ultimoId = perfume.getId();
                }
                this.tamanhoDoArquivo = Math.max(this.tamanhoDoArquivo, posicao + dadosPerfumeBrutos.length + Integer.BYTES);
                escreverCabecalhoNoArquivo();

                txn.commit();
            } finally {
                getSegmentLock(posicao).writeLock().unlock();
            }
        } catch (Exception e) {
            txn.rollback();
            throw new IOException("Falha ao criar perfume: " + e.getMessage(), e);
        }
    }

    // Busca um registro de perfume pelo ID.
    public Optional<Perfume> buscar(int id) throws IOException, InterruptedException {
        gerenciadorLock.readLock().lock();
        try {
            long posicao = arvore.buscar(id);
            if (posicao == -1) {
                return Optional.empty();
            }

            getSegmentLock(posicao).readLock().lock();
            try {
                byte[] dadosBrutos = lerRegistro(posicao, arquivoChannel);
                Perfume perfume = Perfume.fromByteArray(dadosBrutos);

                if (perfume == null || perfume.getId() != id || !perfume.isAtivo()) {
                    System.err.println("Alerta: Registro na posição " + posicao + " não corresponde ao ID " + id + " ou está inativo/corrompido. Ignorando.");
                    return Optional.empty();
                }
                Perfume perfumeDescriptografado = descriptografarPerfume(perfume);
                return Optional.of(perfume);
            } finally {
                getSegmentLock(posicao).readLock().unlock();
            }
        } finally {
            gerenciadorLock.readLock().unlock();
        }
    }

    // Atualiza um registro de perfume existente.
     
    public void atualizar(Perfume perfume) throws Exception {
        gerenciadorLock.readLock().lock();
        Transaction txn = new Transaction("UPDATE", perfume.getId());
        try {
            long posicaoExistente = arvore.buscar(perfume.getId());
            if (posicaoExistente == -1) {
                throw new IOException("Perfume com ID " + perfume.getId() + " não encontrado para atualização.");
            }

            getSegmentLock(posicaoExistente).readLock().lock();
            byte[] dadosBrutosExistente;
            Perfume perfumeExistente;
            int tamanhoRegistroAntigo;
            try {
                dadosBrutosExistente = lerRegistro(posicaoExistente, arquivoChannel);
                perfumeExistente = Perfume.fromByteArray(dadosBrutosExistente);
                Perfume perfumeDescriptografado = descriptografarPerfume(perfume);
                tamanhoRegistroAntigo = dadosBrutosExistente.length + Integer.BYTES;

                if (perfumeExistente == null || perfumeExistente.getId() != perfume.getId() || !perfumeExistente.isAtivo()) {
                    throw new IOException("Registro na posição " + posicaoExistente + " não corresponde ao ID " + perfume.getId() + " ou está inativo/corrompido. Não será atualizado.");
                }
                perfume.setVersion(perfumeExistente.getVersion() + 1);
            } finally {
                getSegmentLock(posicaoExistente).readLock().unlock();
            }

            byte[] dadosAtualizadosBrutos = perfume.toByteArray();
            int tamanhoNovoRegistro = dadosAtualizadosBrutos.length + Integer.BYTES;

            if (tamanhoNovoRegistro <= tamanhoRegistroAntigo) {
                getSegmentLock(posicaoExistente).writeLock().lock();
                try {
                    escreverRegistro(posicaoExistente, dadosAtualizadosBrutos, arquivoChannel);
                    if (tamanhoNovoRegistro < tamanhoRegistroAntigo) {
                        gerenciadorEspaco.addFreeBlock(posicaoExistente + tamanhoNovoRegistro, tamanhoRegistroAntigo - tamanhoNovoRegistro);
                    }
                    txn.commit();
                } finally {
                    getSegmentLock(posicaoExistente).writeLock().unlock();
                }
            } else {
                getSegmentLock(posicaoExistente).writeLock().lock();
                try {
                    perfumeExistente.desative();
                    perfumeExistente.setVersion(perfumeExistente.getVersion() + 1);
                    byte[] dadosInativosBrutos = perfumeExistente.toByteArray();
                    escreverRegistro(posicaoExistente, dadosInativosBrutos, arquivoChannel);
                    gerenciadorEspaco.addFreeBlock(posicaoExistente, tamanhoRegistroAntigo);
                } finally {
                    getSegmentLock(posicaoExistente).writeLock().unlock();
                }
                criar(perfume);
                txn.commit();
            }
        } catch (Exception e) {
            txn.rollback();
            throw e;
        } finally {
            gerenciadorLock.readLock().unlock();
        }
    }

   // Marca um registro de perfume como inativo e o remove da árvore B+.
     
    public void deletar(int id) throws Exception {
        gerenciadorLock.readLock().lock();
        Transaction txn = new Transaction("DELETE", id);
        try {
            long posicao = arvore.buscar(id);
            if (posicao == -1) {
                throw new IOException("Perfume com ID " + id + " não encontrado para remoção.");
            }

            getSegmentLock(posicao).writeLock().lock();
            try {
                byte[] dadosAtuaisBrutos = lerRegistro(posicao, arquivoChannel);
                Perfume perfumeParaDesativar = Perfume.fromByteArray(dadosAtuaisBrutos);

                if (perfumeParaDesativar == null || perfumeParaDesativar.getId() != id || !perfumeParaDesativar.isAtivo()) {
                    throw new IOException("Registro na posição " + posicao + " não corresponde ao ID " + id + " ou já está inativo/corrompido. Não será deletado.");
                }

                perfumeParaDesativar.desative();
                perfumeParaDesativar.setVersion(perfumeParaDesativar.getVersion() + 1);

                byte[] dadosDesativadosBrutos = perfumeParaDesativar.toByteArray();
                escreverRegistro(posicao, dadosDesativadosBrutos, arquivoChannel);

                boolean removidoDaArvore = arvore.remover(id);

                if (!removidoDaArvore) {
                    throw new IOException("Falha ao remover ID " + id + " da árvore B+, mesmo após marcar no arquivo.");
                }

                gerenciadorEspaco.addFreeBlock(posicao, dadosAtuaisBrutos.length + Integer.BYTES);
                registrosAtivos.decrementAndGet();
                txn.commit();

            } finally {
                getSegmentLock(posicao).writeLock().unlock();
            }
        } catch (Exception e) {
            txn.rollback();
            throw e;
        } finally {
            gerenciadorLock.readLock().unlock();
        }
    }

    // Processa uma lista de perfumes em lote (criação)
     
    public CompletableFuture<Void> processarLote(List<Perfume> perfumes) {
        return CompletableFuture.runAsync(() -> {
            gerenciadorLock.readLock().lock();
            Transaction txn = new Transaction("BATCH", -1);
            try {
                for (Perfume p : perfumes) {
                    try {
                        byte[] dadosPerfumeBrutos = p.toByteArray();
                        int tamanhoRealRegistro = dadosPerfumeBrutos.length + Integer.BYTES;

                        long posicao;
                        Optional<GerenciadorEspaco.FreeBlock> freeBlockOpt = gerenciadorEspaco.getFreeBlock(tamanhoRealRegistro);
                        if (freeBlockOpt.isPresent()) {
                            posicao = freeBlockOpt.get().getOffset();
                        } else {
                            posicao = arquivoChannel.size();
                        }

                        getSegmentLock(posicao).writeLock().lock();
                        try {
                            escreverRegistro(posicao, dadosPerfumeBrutos, arquivoChannel);
                            arvore.inserir(p.getId(), posicao);
                            registrosAtivos.incrementAndGet();

                            if (p.getId() > this.ultimoId) {
                                this.ultimoId = p.getId();
                            }
                            this.tamanhoDoArquivo = Math.max(this.tamanhoDoArquivo, posicao + tamanhoRealRegistro);
                            escreverCabecalhoNoArquivo();

                        } finally {
                            getSegmentLock(posicao).writeLock().unlock();
                        }
                    } catch (Exception e) {
                        System.err.println("Erro ao processar perfume " + p.getId() + " no lote: " + e.getMessage());
                    }
                }
                txn.commit();
            } catch (Exception e) {
                txn.rollback();
                throw new CompletionException(e);
            } finally {
                gerenciadorLock.readLock().unlock();
            }
        }, batchExecutor);
    }

    // Compacta o arquivo de dados, reescrevendo apenas registros ativos e aplicando compactação LZW
    public void compactar(String outputFilePath) throws IOException, InterruptedException {
        gerenciadorLock.writeLock().lock();
        Path outputFile = null;
        boolean isExportingNewFile = (outputFilePath != null && !outputFilePath.isEmpty());

        try {
            if (isExportingNewFile) {
                outputFile = Paths.get(outputFilePath);
                Files.createDirectories(outputFile.getParent());
            } else {
                outputFile = Files.createTempFile("compact", ".dat");
            }

            try (FileChannel outputChannel = FileChannel.open(outputFile, StandardOpenOption.CREATE, StandardOpenOption.WRITE)) {
                ByteBuffer tempHeader = ByteBuffer.allocate(FILE_HEADER_TOTAL_SIZE);
                tempHeader.put(this.arquivoValido ? (byte) 1 : (byte) 0);
                tempHeader.putLong(0L);
                tempHeader.putInt(this.ultimoId);
                tempHeader.flip();
                outputChannel.write(tempHeader, 0);

                List<Integer> todosIds = arvore.buscarTodosIds();

                long novaPosicao = FILE_HEADER_TOTAL_SIZE;
                int novosRegistrosAtivosCount = 0;

                for (int id : todosIds) {
                    long posAntiga = arvore.buscar(id);

                    if (posAntiga != -1) {
                        getSegmentLock(posAntiga).readLock().lock();
                        try {
                            byte[] dadosBrutosDoOriginal = lerRegistro(posAntiga, arquivoChannel);
                            Perfume perfume = Perfume.fromByteArray(dadosBrutosDoOriginal);

                            if (perfume != null && perfume.getId() == id && perfume.isAtivo()) {
                                byte[] dadosRecomprimidos = LZWCompressor.compress(dadosBrutosDoOriginal);
                                escreverRegistroComprimido(novaPosicao, dadosRecomprimidos, outputChannel);

                                if (!isExportingNewFile) {
                                    arvore.atualizarPosicao(id, novaPosicao);
                                }
                                novaPosicao += (dadosRecomprimidos.length + Integer.BYTES);
                                novosRegistrosAtivosCount++;
                            } else {
                                System.err.println("Aviso: Registro inativo/inválido (ID: " + id + ") na posição " + posAntiga + " durante compactação. Será ignorado na saída.");
                            }
                        } finally {
                            getSegmentLock(posAntiga).readLock().unlock();
                        }
                    } else {
                         System.err.println("Aviso: ID " + id + " não encontrado na árvore B+ durante compactação (pode ter sido removido por outra thread ou ser um índice órfão). Será ignorado na saída.");
                    }
                }

                long finalOutputFileSize = outputChannel.size();
                ByteBuffer finalHeaderUpdate = ByteBuffer.allocate(FILE_HEADER_TOTAL_SIZE);
                finalHeaderUpdate.put(this.arquivoValido ? (byte) 1 : (byte) 0);
                finalHeaderUpdate.putLong(finalOutputFileSize);
                finalHeaderUpdate.putInt(this.ultimoId);
                finalHeaderUpdate.flip();
                outputChannel.write(finalHeaderUpdate, 0);
                outputChannel.force(true);

                if (!isExportingNewFile) {
                     // Se for "in-place", substituir o arquivo original pelo compactado
                    this.tamanhoDoArquivo = finalOutputFileSize;
                    substituirArquivo(outputFile); // Reinsere o método para 'in-place' se essa for a intenção
                    registrosAtivos.set(novosRegistrosAtivosCount);
                    gerenciadorEspaco.clearFreeList();
                    System.out.println("Compactação 'in-place' concluída. Novo tamanho do arquivo original: " + finalOutputFileSize + " bytes. Registros ativos: " + registrosAtivos.get());
                } else {
                    System.out.println("Compactação para novo arquivo concluída com sucesso. Arquivo gerado: " + outputFile.toAbsolutePath());
                }

            } finally {
                if (!isExportingNewFile) {
                    Files.deleteIfExists(outputFile);
                }
            }
        } finally {
            gerenciadorLock.writeLock().unlock();
        }
    }

    // Fecha todos os recursos do gerenciador de arquivos
    @Override
    public void close() throws IOException {
        maintenanceExecutor.shutdown();
        batchExecutor.shutdown();

        try {
            if (!maintenanceExecutor.awaitTermination(10, TimeUnit.SECONDS)) {
                maintenanceExecutor.shutdownNow();
            }
            if (!batchExecutor.awaitTermination(10, TimeUnit.SECONDS)) {
                batchExecutor.shutdownNow();
            }
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
        }

        this.tamanhoDoArquivo = arquivoChannel.size();
        escreverCabecalhoNoArquivo();

        if (bufferPool != null) {
            bufferPool.close();
        }
        if (arquivoChannel != null && arquivoChannel.isOpen()) {
            arquivoChannel.close();
        }
        if (arquivoRAF != null) {
            arquivoRAF.close();
        }
        if (gerenciadorEspaco != null) {
            gerenciadorEspaco.close();
        }
    }

    // Métodos Auxiliares de Leitura/Escrita
    private Perfume criptografarPerfume(Perfume perfume) {
        if (perfume == null) return null;
        // Criptografa o nome e a marca
        String nomeCriptografado = criptografador.criptografar(perfume.getNome());
        String marcaCriptografada = criptografador.criptografar(perfume.getMarca());

        // Cria um novo objeto Perfume com os campos criptografados
        // ou modifica o objeto existente se seus setters permitirem.
        // Assumo que Perfume tem setters. Se não, você pode criar uma cópia ou outra abordagem.
        perfume.setNome(nomeCriptografado);
        perfume.setMarca(marcaCriptografada);
        return perfume;
    }

    private Perfume descriptografarPerfume(Perfume perfume) {
        if (perfume == null) return null;
        // Descriptografa o nome e a marca
        String nomeDescriptografado = criptografador.descriptografar(perfume.getNome());
        String marcaDescriptografada = criptografador.descriptografar(perfume.getMarca());

        // Atualiza o objeto Perfume com os campos descriptografados
        perfume.setNome(nomeDescriptografado);
        perfume.setMarca(marcaDescriptografada);
        return perfume;
    }
    // Escreve um array de bytes brutos no FileChannel, prefixando-o com seu tamanho.
    private void escreverRegistro(long posicao, byte[] dadosBrutos, FileChannel channel) throws IOException, InterruptedException {
        int tamanhoDados = dadosBrutos.length;
        ByteBuffer buffer = bufferPool.borrowBuffer();
        try {
            buffer.clear();
            buffer.putInt(tamanhoDados);
            buffer.put(dadosBrutos);
            buffer.flip();

            int bytesWritten = channel.write(buffer, posicao);
            if (bytesWritten != (tamanhoDados + Integer.BYTES)) {
                throw new IOException("Erro ao escrever no arquivo: esperado " + (tamanhoDados + Integer.BYTES) + " bytes, escrito " + bytesWritten);
            }
            channel.force(true);
            totalOperacoes.incrementAndGet();
        } finally {
            bufferPool.returnBuffer(buffer);
        }
    }

    // Lê um array de bytes brutos de um FileChannel, lendo primeiro seu tamanho.
    private byte[] lerRegistro(long posicao, FileChannel channel) throws IOException {
        ByteBuffer sizeBuffer = ByteBuffer.allocate(Integer.BYTES);
        int bytesReadSize = channel.read(sizeBuffer, posicao);
        if (bytesReadSize == -1) {
            throw new EOFException("Fim inesperado do arquivo ao tentar ler tamanho na posição: " + posicao);
        }
        if (bytesReadSize < Integer.BYTES) {
            throw new IOException("Dados de tamanho incompletos na posição: " + posicao);
        }
        sizeBuffer.flip();
        int tamanhoDadosBrutos = sizeBuffer.getInt();

        if (tamanhoDadosBrutos < 0 || tamanhoDadosBrutos > (channel.size() - (posicao + Integer.BYTES))) {
             throw new IOException("Tamanho de dados brutos inválido/corrompido: " + tamanhoDadosBrutos + " na posição: " + posicao);
        }

        ByteBuffer dataBuffer = null;
        try {
            if (bufferPool.availableBuffers() > 0) {
                ByteBuffer borrowed = bufferPool.borrowBuffer();
                if (borrowed.capacity() >= tamanhoDadosBrutos) {
                    dataBuffer = borrowed;
                    dataBuffer.clear();
                } else {
                    bufferPool.returnBuffer(borrowed);
                    dataBuffer = ByteBuffer.allocate(tamanhoDadosBrutos);
                }
            } else {
                dataBuffer = ByteBuffer.allocate(tamanhoDadosBrutos);
            }

            int bytesReadData = channel.read(dataBuffer, posicao + Integer.BYTES);
            if (bytesReadData == -1) {
                throw new EOFException("Fim inesperado do arquivo ao tentar ler dados na posição: " + (posicao + Integer.BYTES));
            }
            if (bytesReadData < tamanhoDadosBrutos) {
                throw new IOException("Dados brutos incompletos na posição: " + (posicao + Integer.BYTES) + ". Esperado: " + tamanhoDadosBrutos + ", Lido: " + bytesReadData);
            }
            dataBuffer.flip();
            byte[] dados = new byte[tamanhoDadosBrutos];
            dataBuffer.get(dados);
            return dados;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Leitura do buffer interrompida", e);
        } finally {
            if (dataBuffer != null && bufferPool.contains(dataBuffer)) { // Verifica se o buffer pertence ao pool antes de retornar
                bufferPool.returnBuffer(dataBuffer);
            }
        }
    }

 
    private void escreverRegistroComprimido(long posicao, byte[] dadosComprimidos, FileChannel channel) throws IOException, InterruptedException {
        int tamanhoDados = dadosComprimidos.length;
        ByteBuffer buffer = bufferPool.borrowBuffer();
        try {
            buffer.clear();
            buffer.putInt(tamanhoDados);
            buffer.put(dadosComprimidos);
            buffer.flip();

            int bytesWritten = channel.write(buffer, posicao);
            if (bytesWritten != (tamanhoDados + Integer.BYTES)) {
                throw new IOException("Erro ao escrever no arquivo compactado: esperado " + (tamanhoDados + Integer.BYTES) + " bytes, escrito " + bytesWritten);
            }
            channel.force(true);
        } finally {
            bufferPool.returnBuffer(buffer);
        }
    }

    // Obtém o tamanho total de um registro (tamanho dos dados + 4 bytes do inteiro de tamanho).
    private int obterTamanhoRegistro(long posicao) throws IOException {
        ByteBuffer sizeBuffer = ByteBuffer.allocate(Integer.BYTES);
        int bytesReadSize = arquivoChannel.read(sizeBuffer, posicao);
        if (bytesReadSize == -1) {
            throw new EOFException("Fim inesperado do arquivo ao tentar ler tamanho na posição: " + posicao);
        }
        if (bytesReadSize < Integer.BYTES) {
            throw new IOException("Dados de tamanho incompletos na posição: " + posicao);
        }
        sizeBuffer.flip();
        int tamanhoDados = sizeBuffer.getInt();
        if (tamanhoDados < 0) {
            throw new IOException("Tamanho de registro inválido (negativo) na posição: " + posicao + " - " + tamanhoDados);
        }
        return tamanhoDados + Integer.BYTES;
    }

    // Substitui o arquivo de dados original por um novo arquivo (geralmente compactado).
    private void substituirArquivo(Path tempFile) throws IOException {
        Path originalPath = Paths.get(filePath);

        if (arquivoChannel != null && arquivoChannel.isOpen()) {
            arquivoChannel.close();
        }
        if (arquivoRAF != null) {
            arquivoRAF.close();
        }

        Files.move(tempFile, originalPath, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);

        this.arquivoRAF = new RandomAccessFile(originalPath.toString(), "rw");
        this.arquivoChannel = arquivoRAF.getChannel();
    }

    // Recupera o estado do arquivo e reconstrói a árvore B+.
    private void recoverAndRebuildTree() throws IOException, InterruptedException {
        arvore.limpar();
        registrosAtivos.set(0);

        long currentFilePosition = FILE_HEADER_TOTAL_SIZE;
        long actualFileSize = arquivoChannel.size();

        if (actualFileSize > FILE_HEADER_TOTAL_SIZE) {
            while (currentFilePosition < actualFileSize) {
                int tamanhoRegistroTotal;
                byte[] dadosBrutos;
                Perfume p = null;
                try {
                    getSegmentLock(currentFilePosition).readLock().lock();
                    try {
                        tamanhoRegistroTotal = obterTamanhoRegistro(currentFilePosition);
                        dadosBrutos = lerRegistro(currentFilePosition, arquivoChannel);

                        p = Perfume.fromByteArray(dadosBrutos);

                        if (p != null && p.isAtivo()) {
                            arvore.inserir(p.getId(), currentFilePosition);
                            registrosAtivos.incrementAndGet();
                            if (p.getId() > this.ultimoId) {
                                this.ultimoId = p.getId();
                            }
                        } else {
                            gerenciadorEspaco.addFreeBlock(currentFilePosition, tamanhoRegistroTotal);
                        }
                    } finally {
                        getSegmentLock(currentFilePosition).readLock().unlock();
                    }
                } catch (EOFException e) {
                    gerenciadorEspaco.addFreeBlock(currentFilePosition, (int)(actualFileSize - currentFilePosition));
                    break;
                } catch (IOException e) {
                    currentFilePosition += ESTIMATED_MAX_RECORD_SIZE + Integer.BYTES;
                    continue;
                }
                currentFilePosition += tamanhoRegistroTotal;
            }
        }
        this.tamanhoDoArquivo = actualFileSize;
        escreverCabecalhoNoArquivo();
    }

    // Agenda tarefas de manutenção, como a compactação do arquivo.
    private void agendarManutencoes() {
        maintenanceExecutor.scheduleAtFixedRate(() -> {
            try {
                Path pathOriginal = Paths.get(this.filePath);
                String dirOriginal = "";
                if (pathOriginal.getParent() != null) {
                    dirOriginal = pathOriginal.getParent().toString();
                } else {
                    dirOriginal = Paths.get("").toAbsolutePath().toString();
                }
                String nomeArquivoCompactado = "PerfumesCompact.dat";
                String caminhoCompletoCompactado = Paths.get(dirOriginal, nomeArquivoCompactado).toString();
                compactar(caminhoCompletoCompactado);
            } catch (IOException e) {
                System.err.println("GerenciadorArquivos: Erro durante a compactação agendada: " + e.getMessage());
                e.printStackTrace();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                System.err.println("GerenciadorArquivos: Compactação agendada interrompida: " + e.getMessage());
            }
        }, 120, 120, TimeUnit.MINUTES);
    }

    // Obtém o ReadWriteLock de segmento apropriado para uma dada posição no arquivo.
    private ReadWriteLock getSegmentLock(long posicao) {
        if (posicao < FILE_HEADER_TOTAL_SIZE) {
            return gerenciadorLock; // Usa o lock global para o cabeçalho
        }
        int segmentIndex = (int) (((posicao - FILE_HEADER_TOTAL_SIZE) / ESTIMATED_MAX_RECORD_SIZE) % SEGMENT_COUNT);
        return segmentLocks.get(segmentIndex);
    }

    // Classe Interna para Transações
    private class Transaction {
        private final String id;
        private final String type;
        private final int perfumeId;

        /**
         * Construtor para uma nova transação.
         */
        Transaction(String type, int perfumeId) {
            this.id = type + "_" + perfumeId + "_" + System.nanoTime();
            this.type = type;
            this.perfumeId = perfumeId;
        }

        /**
         * Confirma a transação.
         */
        void commit() {
            totalOperacoes.incrementAndGet();
        }

        /**
         * Reverte a transação.
         */
        void rollback() {
            // Lógica de rollback mais complexa seria necessária para um sistema robusto
        }
    }
}
