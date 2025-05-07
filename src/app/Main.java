package app;

import java.io.*;
import java.util.Scanner;
import Models.Perfume;
import Services.GerenciadorPerfumes;
import Structures.Arvore_BPlus;

public class Main {
    private final Scanner scan = new Scanner(System.in);
    private final Arvore_BPlus arvore;
    private final GerenciadorPerfumes gerenciador;

    public Main() {
        this.arvore = new Arvore_BPlus(3); // Ordem 3
        this.gerenciador = new GerenciadorPerfumes(arvore, "perfumes.dat");
    }

    public static void main(String[] args) {
        new Main().menu();
    }

    public void menu() {
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
                case 1 -> adicionarPerfume();
                case 2 -> listarPerfumes();
                case 3 -> atualizarPerfume();
                case 4 -> removerPerfume();
                case 5 -> { 
                    System.out.println("Saindo...");
                    return;
                }
                default -> System.out.println("Opção inválida!");
            }
        }
    }

    private void adicionarPerfume() {
        try {
            System.out.print("ID: ");
            int id = scan.nextInt();
            scan.nextLine();
            
            System.out.print("Nome: ");
            String nome = scan.nextLine();
            
            gerenciador.criar(new Perfume(id, nome));
            System.out.println("Perfume adicionado!");
        } catch (IOException e) {
            System.err.println("Erro ao adicionar: " + e.getMessage());
        }
    }

    private void listarPerfumes() {
        try {
            System.out.println("\n=== PERFUMES ===");
            for (int id = 1; id <= 1000; id++) { // Ajuste o range conforme necessário
                Perfume p = gerenciador.buscar(id);
                if (p != null) System.out.println(p);
            }
        } catch (IOException e) {
            System.err.println("Erro ao listar: " + e.getMessage());
        }
    }

    private void atualizarPerfume() {
        try {
            System.out.print("ID para atualizar: ");
            int id = scan.nextInt();
            scan.nextLine();
            
            Perfume existente = gerenciador.buscar(id);
            if (existente == null) {
                System.out.println("Perfume não encontrado!");
                return;
            }
            
            System.out.print("Novo nome: ");
            String novoNome = scan.nextLine();
            
            gerenciador.atualizar(new Perfume(id, novoNome));
            System.out.println("Perfume atualizado!");
        } catch (IOException e) {
            System.err.println("Erro ao atualizar: " + e.getMessage());
        }
    }

    private void removerPerfume() {
        try {
            System.out.print("ID para remover: ");
            int id = scan.nextInt();
            scan.nextLine();
            
            gerenciador.deletar(id);
            System.out.println("Perfume removido!");
        } catch (IOException e) {
            System.err.println("Erro ao remover: " + e.getMessage());
        }
    }
}