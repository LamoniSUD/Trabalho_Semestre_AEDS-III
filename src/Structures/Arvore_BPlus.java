package Structures;

import java.io.*;
import java.util.ArrayList;
import java.util.List;
import Models.Perfume;

class Node implements Serializable {
    boolean isLeaf;
    List<Long> posicoesArquivo = new ArrayList<>();
    List<Perfume> perfumes;
    List<Node> children;
    Node next; // Ligação entre folhas

    public Node(boolean isLeaf) {
        this.isLeaf = isLeaf;
        this.perfumes = new ArrayList<>();
        this.children = new ArrayList<>();
        this.next = null;
    }
}

public class Arvore_BPlus {
    private final int order;
    private Node root;
    private static final int DEFAULT_ORDER = 4;

    public Arvore_BPlus(int order) {
        this.order = order;
        root = new Node(true);
    }

    public Arvore_BPlus() {
        this(DEFAULT_ORDER);
    }

    public void insert(Perfume perfume) {
        Node leaf = findLeaf(root, perfume.getId());
        insertIntoLeaf(leaf, perfume);

        if (leaf.perfumes.size() > order) {
            splitLeaf(leaf);
        }
    }

    public Node findLeaf(Node node, int id) {
        if (node.isLeaf) return node;
        for (int i = 0; i < node.perfumes.size(); i++) {
            if (id < node.perfumes.get(i).getId()) {
                return findLeaf(node.children.get(i), id);
            }
        }
        return findLeaf(node.children.get(node.children.size() - 1), id);
    }

    private void insertIntoLeaf(Node leaf, Perfume perfume) {
        leaf.perfumes.add(perfume);
        leaf.perfumes.sort((p1, p2) -> Integer.compare(p1.getId(), p2.getId()));
    }

    private void splitLeaf(Node leaf) {
        Node newLeaf = new Node(true);
        int mid = leaf.perfumes.size() / 2;

        newLeaf.perfumes = new ArrayList<>(leaf.perfumes.subList(mid, leaf.perfumes.size()));
        leaf.perfumes = new ArrayList<>(leaf.perfumes.subList(0, mid));

        newLeaf.next = leaf.next;
        leaf.next = newLeaf;

        if (leaf == root) {
            Node newRoot = new Node(false);
            newRoot.perfumes.add(newLeaf.perfumes.get(0));
            newRoot.children.add(leaf);
            newRoot.children.add(newLeaf);
            root = newRoot;
        } else {
            insertInParent(leaf, newLeaf);
        }
    }

    private void insertInParent(Node oldLeaf, Node newLeaf) {
        Node parent = findParent(root, oldLeaf);
        if (parent == null) return;

        parent.perfumes.add(newLeaf.perfumes.get(0));
        parent.children.add(parent.children.indexOf(oldLeaf) + 1, newLeaf);
        parent.perfumes.sort((p1, p2) -> Integer.compare(p1.getId(), p2.getId()));

        if (parent.perfumes.size() > order) {
            splitParent(parent);
        }
    }

    private void splitParent(Node parent) {
        Node newParent = new Node(false);
        int mid = parent.perfumes.size() / 2;

        Perfume upKey = parent.perfumes.get(mid);

        newParent.perfumes = new ArrayList<>(parent.perfumes.subList(mid + 1, parent.perfumes.size()));
        newParent.children = new ArrayList<>(parent.children.subList(mid + 1, parent.children.size()));

        parent.perfumes = new ArrayList<>(parent.perfumes.subList(0, mid));
        parent.children = new ArrayList<>(parent.children.subList(0, mid + 1));

        if (parent == root) {
            Node newRoot = new Node(false);
            newRoot.perfumes.add(upKey);
            newRoot.children.add(parent);
            newRoot.children.add(newParent);
            root = newRoot;
        } else {
            insertInParent(parent, newParent);
        }
    }

    private Node findParent(Node node, Node child) {
        if (node.isLeaf || node.children.isEmpty()) return null;

        for (Node c : node.children) {
            if (c == child) return node;
            Node parent = findParent(c, child);
            if (parent != null) return parent;
        }
        return null;
    }

    public List<Perfume> search(int id) {
        Node leaf = findLeaf(root, id);
        List<Perfume> result = new ArrayList<>();
        for (Perfume p : leaf.perfumes) {
            if (p.getId() == id) {
                result.add(p);
            }
        }
        return result;
    }

    public void delete(int id) {
        Node leaf = findLeaf(root, id);
        if (leaf == null) return;

        leaf.perfumes.removeIf(p -> p.getId() == id);
        rebalanceLeaf(leaf);
    }

    private void rebalanceLeaf(Node leaf) {
        int minKeys = (int) Math.ceil(order / 2.0);
        if (leaf.perfumes.size() >= minKeys || leaf == root) return;

        Node left = findLeft(leaf);
        Node right = leaf.next;
        Node parent = findParent(root, leaf);
        int index = parent.children.indexOf(leaf);

        if (left != null && left.perfumes.size() > minKeys) {
            Perfume borrowed = left.perfumes.remove(left.perfumes.size() - 1);
            leaf.perfumes.add(0, borrowed);
            parent.perfumes.set(index - 1, leaf.perfumes.get(0));
            return;
        }

        if (right != null && right.perfumes.size() > minKeys) {
            Perfume borrowed = right.perfumes.remove(0);
            leaf.perfumes.add(borrowed);
            parent.perfumes.set(index, right.perfumes.get(0));
            return;
        }

        if (left != null) {
            left.perfumes.addAll(leaf.perfumes);
            left.next = leaf.next;
            parent.children.remove(leaf);
            if (index > 0) parent.perfumes.remove(index - 1);
        } else if (right != null) {
            leaf.perfumes.addAll(right.perfumes);
            leaf.next = right.next;
            parent.children.remove(right);
            if (index < parent.perfumes.size()) parent.perfumes.remove(index);
        }
    }

    private Node findLeft(Node leaf) {
        Node parent = findParent(root, leaf);
        if (parent == null) return null;

        int index = parent.children.indexOf(leaf);
        if (index > 0) {
            return parent.children.get(index - 1);
        }
        return null;
    }

    public void atualizar(int id, Perfume novoPerfume) {
        Node leaf = findLeafWithId(id);
        if (leaf == null) return;

        for (int i = 0; i < leaf.perfumes.size(); i++) {
            if (leaf.perfumes.get(i).getId() == id) {
                leaf.perfumes.set(i, novoPerfume);
                break;
            }
        }
    }

    private Node findLeafWithId(int id) {
        return findLeaf(root, id);
    }

    public void salvarParaArquivo(String caminhoArquivo) throws IOException {
        try (ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream(caminhoArquivo))) {
            oos.writeObject(this.root);
        } catch (IOException e) {
            System.err.println("Erro ao salvar a árvore no arquivo: " + e.getMessage());
            throw e;
        }
    }

    public void carregarDeArquivo(String caminhoArquivo) throws IOException, ClassNotFoundException {
        try (ObjectInputStream ois = new ObjectInputStream(new FileInputStream(caminhoArquivo))) {
            this.root = (Node) ois.readObject();
        } catch (IOException | ClassNotFoundException e) {
            System.err.println("Erro ao carregar a árvore: " + e.getMessage());
            throw e;
        }
    }

    public long buscarPosicao(int id) {
        Node folha = findLeaf(root, id);
        for (int i = 0; i < folha.perfumes.size(); i++) {
            if (folha.perfumes.get(i).getId() == id) {
                return folha.posicoesArquivo.get(i);
            }
        }
        return -1;
    }

    public Node getRoot() {
        return this.root;
    }
}
