package io;

import Models.Perfume;
import java.util.List;

public class Ler {
    private BPlusTree tree;

    public Ler() {
        tree = new BPlusTree();
    }

    // Método para carregar os perfumes de uma fonte externa, como arquivo ou banco de dados
    private List<Perfume> carregarPerfumes() {
        // Aqui você pode implementar a lógica para carregar os perfumes
        // Exemplo de perfumes estáticos para teste:
        Perfume p1 = new Perfume("Perfume 1", "Marca 1", 100, LocalDate.now());
        Perfume p2 = new Perfume("Perfume 2", "Marca 2", 200, LocalDate.now());
        tree.insert(p1);
        tree.insert(p2);
        return tree.getAllPerfumes();  // Retorna todos os perfumes armazenados na árvore
    }

    // Método para exibir perfumes disponíveis
    public void readIn() {
        List<Perfume> perfumes = carregarPerfumes();  // Carregar perfumes
        System.out.println("Perfumes disponíveis:");
        for (Perfume perfume : perfumes) {
            if (perfume.isAvailable()) {
                System.out.println("--------------------");
                System.out.println("ID: " + perfume.getId());
                System.out.println("Nome: " + perfume.getName());
                System.out.println("Marca: " + perfume.getMarca());
                System.out.printf("Valor: R$ %.2f%n", perfume.getValue() / 100.0);  // Exibe valor formatado
                System.out.println("Estoque: " + perfume.getStock());
                System.out.println("Data: " + perfume.getDate());
            }
        }
    }

    // Método para exibir perfumes não disponíveis
    public void readOut() {
        System.out.println("Perfumes não disponíveis:");
        for (Perfume perfume : tree.getAllPerfumes()) {  // Recupera todos os perfumes da árvore
            if (perfume != null && !perfume.isAvailable()) {
                System.out.println("--------------------");
                System.out.println("ID: " + perfume.getId());
                System.out.println("Nome: " + perfume.getName());
                System.out.println("Marca: " + perfume.getMarca());
                System.out.printf("Valor: R$ %.2f%n", perfume.getValue() / 100.0);  // Exibe valor formatado
                System.out.println("Estoque: " + perfume.getStock());
                System.out.println("Em estoque: " + (perfume.isAvailable() ? "Sim" : "Não"));
            }
        }
    }
}
