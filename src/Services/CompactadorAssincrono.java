package Services;

import java.io.*;
import java.nio.file.*;

public class CompactadorAssincrono {
    private final GerenciadorPerfumes gerenciador;
    private volatile boolean emExecucao;

    public void compactarEmBackground() {
        if (emExecucao) return;
        emExecucao = true;
        
        new Thread(() -> {
            try {
                Path tempFile = Files.createTempFile("compactado_", ".tmp");
                compactarParaArquivoTemporario(tempFile);
                substituirArquivoPrincipal(tempFile);
            } catch (IOException e) {
                System.err.println("Falha na compactação: " + e.getMessage());
            } finally {
                emExecucao = false;
            }
        }).start();
    }

    private void compactarParaArquivoTemporario(Path tempFile) throws IOException {
        // Implementação similar à compactarAsync() do GerenciadorPerfumes
    }
}