package Services;

import Models.Perfume;
import Structures.Arvore_BPlus;
import java.io.*;

public class GerenciadorArquivos {
    private final Arvore_BPlus arvore;
    private final String caminhoArquivo;

    public GerenciadorArquivos(Arvore_BPlus arvore, String caminhoArquivo) {
        this.arvore = arvore;
        this.caminhoArquivo = caminhoArquivo;
    }

    // ---- Métodos Principais ---- //
    public void salvarPerfume(Perfume perfume) throws IOException {
        try (RandomAccessFile arquivo = new RandomAccessFile(caminhoArquivo, "rw")) {
            long posicao = arquivo.length();
            arquivo.seek(posicao);
            arquivo.writeInt(perfume.getId());
            arquivo.writeUTF(perfume.getNome());
            arvore.insertComPosicao(perfume, posicao);  // Novo método na árvore
        }
    }

    public Perfume buscarPerfume(int id) throws IOException {
        try (RandomAccessFile arquivo = new RandomAccessFile(caminhoArquivo, "r")) {
            long posicao = arvore.buscarPosicao(id);  // Você implementará isso
            arquivo.seek(posicao);
            return new Perfume(arquivo.readInt(), arquivo.readUTF());
        }
    }
}