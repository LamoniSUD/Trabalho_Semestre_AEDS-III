package Services;

import Models.Perfume;
import Structures.Arvore_BPlus;
import java.io.*;

public class GerenciadorPerfumes {
    private final Arvore_BPlus arvore;
    private final String arquivoDados;

    public GerenciadorPerfumes(Arvore_BPlus arvore, String arquivoDados) {
        this.arvore = arvore;
        this.arquivoDados = arquivoDados;
    }

    public void criar(Perfume perfume) throws IOException {
        try (RandomAccessFile file = new RandomAccessFile(arquivoDados, "rw")) {
            long posicao = file.length();
            file.seek(posicao);
            file.writeInt(perfume.getId());
            file.writeUTF(perfume.getNome());
            arvore.insert(perfume, posicao);
        }
    }

    public Perfume buscar(int id) throws IOException {
        Long posicao = arvore.buscarPosicao(id);
        if (posicao == null) return null;

        try (RandomAccessFile file = new RandomAccessFile(arquivoDados, "r")) {
            file.seek(posicao);
            return new Perfume(file.readInt(), file.readUTF());
        }
    }

    public void atualizar(Perfume perfume) throws IOException {
        Long posicao = arvore.buscarPosicao(perfume.getId());
        if (posicao == null) throw new IOException("Perfume não encontrado");

        try (RandomAccessFile file = new RandomAccessFile(arquivoDados, "rw")) {
            file.seek(posicao);
            file.writeInt(perfume.getId());
            file.writeUTF(perfume.getNome());
        }
    }

    public void deletar(int id) throws IOException {
        Long posicao = arvore.buscarPosicao(id);
        if (posicao != null) {
            try (RandomAccessFile file = new RandomAccessFile(arquivoDados, "rw")) {
                file.seek(posicao);
                file.writeInt(-1); // Marca como excluído
            }
            arvore.delete(id);
        }
    }
}