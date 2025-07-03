package Structures;

import java.util.*;

public class Arvore_BPlus {
    private No raiz;
    private int ordem;

    public Arvore_BPlus(int ordem) {
        if (ordem < 2) {
            throw new IllegalArgumentException("A ordem da árvore B+ deve ser pelo menos 2.");
        }
        this.ordem = ordem;
        this.raiz = new No(ordem, true);
    }

    public void inserir(int id, long posicao) {
        if (id <= 0) {
            throw new IllegalArgumentException("ID deve ser um número positivo.");
        }

        if (raiz.numEntradas == ordem - 1) { 
            No s = new No(ordem, false);
            s.filhos[0] = raiz;          
            
            int chavePromovida = raiz.chaves[(ordem - 1) / 2];
            
            No novoIrmao = new No(ordem, raiz.isFolha);
            
            int j = 0;
            for (int i = (ordem - 1) / 2 + 1; i < ordem - 1; i++) {
                novoIrmao.chaves[j] = raiz.chaves[i];
                novoIrmao.valores[j] = raiz.valores[i];
                j++;
            }
            novoIrmao.numEntradas = j;

            if (!raiz.isFolha) {
                j = 0;
                for (int i = (ordem - 1) / 2 + 1; i <= ordem - 1; i++) { 
                    novoIrmao.filhos[j] = raiz.filhos[i];
                    raiz.filhos[i] = null;
                    j++;
                }
            }
            
            raiz.numEntradas = (ordem - 1) / 2;

            s.chaves[0] = chavePromovida;
            s.filhos[1] = novoIrmao;
            s.numEntradas++;
            
            this.raiz = s;
        }
        
        inserirEmNo(this.raiz, id, posicao);
    }

    private void inserirEmNo(No no, int id, long posicao) {
        int i = no.numEntradas - 1;
        
        if (no.isFolha) {
            while (i >= 0 && id < no.chaves[i]) {
                no.chaves[i + 1] = no.chaves[i];
                no.valores[i + 1] = no.valores[i];
                i--;
            }
            no.chaves[i + 1] = id;
            no.valores[i + 1] = posicao;
            no.numEntradas++;
        } else {
            while (i >= 0 && id < no.chaves[i]) {
                i--;
            }
            i++;

            No filho = no.filhos[i];

            if (filho.numEntradas == ordem - 1) {
                int chavePromovida = filho.chaves[(ordem - 1) / 2];
                
                No novoFilho = new No(ordem, filho.isFolha);
                
                int k = 0;
                for (int m = (ordem - 1) / 2 + 1; m < ordem - 1; m++) {
                    novoFilho.chaves[k] = filho.chaves[m];
                    novoFilho.valores[k] = filho.valores[m];
                    k++;
                }
                novoFilho.numEntradas = k;

                if (!filho.isFolha) {
                    k = 0;
                    for (int m = (ordem - 1) / 2 + 1; m <= ordem - 1; m++) { 
                        novoFilho.filhos[k] = filho.filhos[m];
                        filho.filhos[m] = null;
                        k++;
                    }
                }
                
                filho.numEntradas = (ordem - 1) / 2;

                for (int idx = no.numEntradas - 1; idx >= i; idx--) {
                    no.chaves[idx + 1] = no.chaves[idx];
                }
                
                for (int idx = no.numEntradas; idx >= i + 1; idx--) {
                    no.filhos[idx + 1] = no.filhos[idx];
                }

                no.chaves[i] = chavePromovida;
                no.filhos[i + 1] = novoFilho;
                no.numEntradas++;

                if (id >= no.chaves[i]) {
                    inserirEmNo(no.filhos[i + 1], id, posicao);
                } else {
                    inserirEmNo(no.filhos[i], id, posicao);
                }
            } else {
                inserirEmNo(filho, id, posicao);
            }
        }
    }

    public long buscar(int id) {
        No atual = raiz;
        while (!atual.isFolha) {
            int i = 0;
            while (i < atual.numEntradas && id >= atual.chaves[i]) {
                i++;
            }
            atual = atual.filhos[i];
        }

        for (int i = 0; i < atual.numEntradas; i++) {
            if (atual.chaves[i] == id) {
                return atual.valores[i];
            }
        }
        return -1;
    }
    
    public int buscarIdPorPosicao(long position) {
        return -1; 
    }

    public void atualizarPosicao(int id, long novaPosicao) {
        No atual = raiz;
        while (!atual.isFolha) {
            int i = 0;
            while (i < atual.numEntradas && id >= atual.chaves[i]) {
                i++;
            }
            atual = atual.filhos[i];
        }

        for (int i = 0; i < atual.numEntradas; i++) {
            if (atual.chaves[i] == id) {
                atual.valores[i] = novaPosicao;
                return;
            }
        }
        System.err.println("Aviso: ID " + id + " não encontrado na árvore para atualização de posição.");
    }

    public boolean remover(int id) {
        No folha = buscarFolhaParaInsercao(raiz, id);
        for (int i = 0; i < folha.numEntradas; i++) {
            if (folha.chaves[i] == id) {
                for (int j = i; j < folha.numEntradas - 1; j++) {
                    folha.chaves[j] = folha.chaves[j + 1];
                    folha.valores[j] = folha.valores[j + 1];
                }
                folha.numEntradas--;
                return true;
            }
        }
        return false;
    }

    public void limpar() {
        this.raiz = new No(ordem, true);
    }

    public List<Integer> buscarTodosIds() {
        List<Integer> ids = new ArrayList<>();
        Stack<No> stack = new Stack<>();
        stack.push(raiz);

        List<Integer> tempIds = new ArrayList<>(); 

        while (!stack.isEmpty()) {
            No atual = stack.pop();
            if (atual.isFolha) {
                for (int i = 0; i < atual.numEntradas; i++) {
                    tempIds.add(atual.chaves[i]);
                }
            } else {
                for (int i = atual.numEntradas; i >= 0; i--) {
                    if (atual.filhos[i] != null) {
                        stack.push(atual.filhos[i]);
                    }
                }
            }
        }
        Collections.sort(tempIds); 
        ids.addAll(tempIds);
        return ids;
    }

    private No buscarFolhaParaInsercao(No no, int id) {
        while (!no.isFolha) {
            int i = 0;
            while (i < no.numEntradas && id >= no.chaves[i]) {
                i++;
            }
            no = no.filhos[i];
        }
        return no;
    }

    private void dividirFilho(No pai, int indiceFilho, No filho) {
        No novoFilho = new No(ordem, filho.isFolha);
        
        int chavePromovida = filho.chaves[(ordem - 1) / 2];
        
        int j = 0;
        for (int i = (ordem - 1) / 2 + 1; i < ordem - 1; i++) {
            novoFilho.chaves[j] = filho.chaves[i];
            novoFilho.valores[j] = filho.valores[i];
            j++;
        }
        novoFilho.numEntradas = j;

        if (!filho.isFolha) {
            j = 0;
            for (int i = (ordem - 1) / 2 + 1; i <= ordem - 1; i++) { 
                novoFilho.filhos[j] = filho.filhos[i];
                filho.filhos[i] = null;
                j++;
            }
        }
        
        filho.numEntradas = (ordem - 1) / 2;

        for (int k = pai.numEntradas - 1; k >= indiceFilho; k--) {
            pai.chaves[k + 1] = pai.chaves[k];
        }
        
        for (int k = pai.numEntradas; k >= indiceFilho + 1; k--) {
            pai.filhos[k + 1] = pai.filhos[k];
        }

        pai.chaves[indiceFilho] = chavePromovida;
        pai.filhos[indiceFilho + 1] = novoFilho;
        pai.numEntradas++;
    }
    
    private No encontrarPai(No raizBusca, No filho) {
        if (raizBusca.isFolha || raizBusca.filhos[0].isFolha) {
            return null;
        }
        for (int i = 0; i <= raizBusca.numEntradas; i++) {
            if (raizBusca.filhos[i] == filho) {
                return raizBusca;
            } else if (!raizBusca.filhos[i].isFolha) {
                No pai = encontrarPai(raizBusca.filhos[i], filho);
                if (pai != null) {
                    return pai;
                }
            }
        }
        return null;
    }

    private static class No {
        int[] chaves;
        long[] valores;
        No[] filhos;
        int numEntradas;
        boolean isFolha;

        No(int ordem, boolean isFolha) {
            this.chaves = new int[ordem - 1];
            this.valores = new long[ordem - 1];
            this.filhos = new No[ordem];
            this.numEntradas = 0;
            this.isFolha = isFolha;
        }
    }
}