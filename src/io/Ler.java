package io;

import Models.Perfume;
import Structures.Arvore_BPlus;

import java.time.LocalDate;
import java.util.List;

public class Ler {
    private Arvore_BPlus tree;

    public Ler() {
        tree = new Arvore_BPlus(); // ordem não está sendo usada no construtor
    }

    // Método para carregar os perfumes de uma fonte externa, como arquivo ou banco de dados
    private List<Perfume> carregarPerfumes() {
        // Aqui você pode implementar a lógica para carregar os perfumes
        // Exemplo de perfumes estáticos para teste:
        Perfume p1 = new Perfume("Perfume 1", "Marca 1", 100, LocalDate.now());
        Perfume p2 = new Perfume("Perfume 2", "Marca 2", 200, LocalDate.now());
        tree.insert(p1);
        tree.insert(p2);
        return tree.search(-1); // Substitua com um método que retorna todos os perfumes, se disponível
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
        for (Perfume perfume : tree.search(-1)) {  // Substitua com um método que retorna todos os perfumes
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
