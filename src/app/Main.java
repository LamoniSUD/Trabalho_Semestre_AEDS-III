package app;

import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.Scanner;
import Models.Perfume;
import Services.GerenciadorArquivos;
import Structures.Arvore_BPlus;

public class Main {
    private final Scanner scan = new Scanner(System.in);
    private final Arvore_BPlus arvore;
    private final GerenciadorArquivos gerenciador;

    public Main() throws IOException, InterruptedException {
        this.arvore = new Arvore_BPlus(3); // Ordem 3
        this.gerenciador = new GerenciadorArquivos(arvore, "perfumes.dat");
    }

    public static void main(String[] args) {
        Main app = null;
        try {
            app = new Main();
            app.menu();
        } catch (Exception e) {
            System.err.println("Erro crítico na aplicação: " + e.getMessage());
            e.printStackTrace();
        } finally {
            if (app != null && app.gerenciador != null) {
                try {
                    app.gerenciador.close();
                } catch (IOException e) {
                    System.err.println("Erro ao fechar o gerenciador de arquivos: " + e.getMessage());
                }
            }
        }
    }

    public void menu() throws Exception {
        while (true) {
            System.out.println("\n=== MENU ===");
            System.out.println("1. Adicionar perfume");
            System.out.println("2. Listar perfumes");
            System.out.println("3. Atualizar perfume");
            System.out.println("4. Remover perfume");
            System.out.println("5. Sair");
            System.out.print("Opção: ");

            int opcao = scan.nextInt();
            scan.nextLine(); // Limpar buffer

            switch (opcao) {
                case 1:
                    adicionarPerfume();
                    break;
                case 2:
                    listarPerfumes();
                    break;
                case 3:
                    atualizarPerfume();
                    break;
                case 4:
                    removerPerfume();
                    break;
                case 5: {
                    System.out.println("Saindo...");
                    return;
                }
                default:
                    System.out.println("Opção inválida!");
            }
        }
    }

    private void adicionarPerfume() {
        try {
            /*System.out.print("ID: ");
            int id = scan.nextInt();
            scan.nextLine();*/
        	int novoID = gerenciador.novoID();

            System.out.print("Nome: ");
            String nome = scan.nextLine();
            
            System.out.print("Marca: ");
            String marca = scan.nextLine();
            
            System.out.print("Valor: ");
            int valor = scan.nextInt();
            
            System.out.print("Estoque: "); // Corrigido para "Estoque" (em vez de "Stoque")
            int estoque = scan.nextInt();
            scan.nextLine();
            
            gerenciador.criar(new Perfume(novoID, nome, marca, valor, estoque));
            System.out.println("Perfume adicionado!");
        } catch (IOException e) {
            System.err.println("Erro ao adicionar: " + e.getMessage());
            e.printStackTrace();
        } catch (Exception e) {
             System.err.println("Erro inesperado ao adicionar: " + e.getMessage());
             e.printStackTrace();
        }
    }

    private void listarPerfumes() {
        try {
            System.out.println("\n=== PERFUMES ===");
            List<Integer> idsAtivos = arvore.buscarTodosIds(); 

            if (idsAtivos.isEmpty()) {
                System.out.println("Nenhum perfume ativo encontrado.");
                return;
            }

            for (int id : idsAtivos) {
                Optional<Perfume> pOpt = gerenciador.buscar(id);

                if (pOpt.isPresent()) {
                    System.out.println(pOpt.get());
                } else {
                   System.err.println("AVISO: Perfume com ID " + id + " encontrado na árvore, mas falha ao carregar/descomprimir do arquivo.");
                }
            }
        } catch (IOException e) {
            System.err.println("Erro ao listar: " + e.getMessage());
            e.printStackTrace();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            System.err.println("Listagem interrompida: " + e.getMessage());
        } catch (Exception e) {
            System.err.println("Erro inesperado ao listar: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void atualizarPerfume() { // Removido throws Exception, tratada internamente
        try {
            System.out.print("ID para atualizar: ");
            int id = scan.nextInt();
            scan.nextLine();
            
            Optional<Perfume> existenteOpt = gerenciador.buscar(id);

            if (existenteOpt.isEmpty()) { 
                System.out.println("Perfume não encontrado!");
                return;
            }
            Perfume existente = existenteOpt.get();

            System.out.print("Novo nome (atual: " + existente.getNome() + "): ");
            String novoNome = scan.nextLine();
            existente.setNome(novoNome);

            System.out.print("Nova marca (atual: " + existente.getMarca() + "): ");
            String novaMarca = scan.nextLine();
            existente.setMarca(novaMarca);

            System.out.print("Novo valor (atual: " + String.format("%.2f", (double)existente.getValor() / 100.0) + "): ");
            int novoValor = scan.nextInt();
            existente.setValor(novoValor);

            System.out.print("Novo estoque (atual: " + existente.getEstoque() + "): ");
            int novoEstoque = scan.nextInt();
            scan.nextLine();
            existente.setEstoque(novoEstoque);
            existente.validaPerfume();
            
            gerenciador.atualizar(existente);
            System.out.println("Perfume atualizado!");
        } catch (IOException e) {
            System.err.println("Erro ao atualizar: " + e.getMessage());
            e.printStackTrace();
        } catch (Exception e) {
            System.err.println("Erro inesperado ao atualizar: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void removerPerfume() { // Removido throws Exception, tratada internamente
        try {
            System.out.print("ID para remover: ");
            int id = scan.nextInt();
            scan.nextLine();
            
            gerenciador.deletar(id);
            System.out.println("Perfume removido!");
        } catch (IOException e) {
            System.err.println("Erro ao remover: " + e.getMessage());
            e.printStackTrace();
        } catch (Exception e) {
            System.err.println("Erro inesperado ao remover: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
