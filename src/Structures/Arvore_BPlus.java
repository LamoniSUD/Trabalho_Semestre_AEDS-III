package Structures;

import java.util.ArrayList;
import java.util.List;
import Models.Perfume;

class Node {
    boolean isLeaf;
    List<Perfume> perfumes;
    List<Node> children;

    public Node(boolean isLeaf) {
        this.isLeaf = isLeaf;
        this.perfumes = new ArrayList<>();
        this.children = new ArrayList<>();
    }
}

public class BPlusTree {
    private final int order;
    private Node root;

    public BPlusTree(int order) {
        this.order = order;
        root = new Node(true); // A árvore começa com um nó folha.
    }

    public void insert(Perfume perfume) {
        Node leaf = findLeaf(root, perfume.getId());
        insertIntoLeaf(leaf, perfume);

        if (leaf.perfumes.size() > order) {
            splitLeaf(leaf);
        }
    }

    private Node findLeaf(Node node, int id) {
        if (node.isLeaf) {
            return node;
        }

        for (Node child : node.children) {
            if (id < child.perfumes.get(0).getId()) {
                return findLeaf(child, id);
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

        // Divida o nó de folhas em dois
        newLeaf.perfumes = new ArrayList<>(leaf.perfumes.subList(mid, leaf.perfumes.size()));
        leaf.perfumes = new ArrayList<>(leaf.perfumes.subList(0, mid));

        if (leaf == root) {
            // Se o nó folha for a raiz, cria um novo nó raiz.
            Node newRoot = new Node(false);
            newRoot.perfumes.add(newLeaf.perfumes.get(0));  // A chave intermediária
            newRoot.children.add(leaf);
            newRoot.children.add(newLeaf);
            root = newRoot;
        } else {
            // Caso contrário, insere a chave intermediária no nó pai.
            insertInParent(leaf, newLeaf);
        }
    }

    private void insertInParent(Node oldLeaf, Node newLeaf) {
        Node parent = findParent(root, oldLeaf);
        parent.perfumes.add(newLeaf.perfumes.get(0));  // A chave intermediária
        parent.children.add(newLeaf);

        parent.perfumes.sort((p1, p2) -> Integer.compare(p1.getId(), p2.getId()));

        if (parent.perfumes.size() > order) {
            splitParent(parent);
        }
    }

    private void splitParent(Node parent) {
        Node newParent = new Node(false);
        int mid = parent.perfumes.size() / 2;

        // Divida o nó pai em dois
        newParent.perfumes = new ArrayList<>(parent.perfumes.subList(mid + 1, parent.perfumes.size()));
        parent.perfumes = new ArrayList<>(parent.perfumes.subList(0, mid));

        newParent.children = new ArrayList<>(parent.children.subList(mid + 1, parent.children.size()));
        parent.children = new ArrayList<>(parent.children.subList(0, mid + 1));

        if (parent == root) {
            // Se o nó pai for a raiz, cria um novo nó raiz.
            Node newRoot = new Node(false);
            newRoot.perfumes.add(parent.perfumes.get(mid));
            newRoot.children.add(parent);
            newRoot.children.add(newParent);
            root = newRoot;
        } else {
            insertInParent(parent, newParent);
        }
    }

    private Node findParent(Node node, Node child) {
        if (node.isLeaf) {
            return null;
        }

        for (int i = 0; i < node.children.size(); i++) {
            if (node.children.get(i) == child) {
                return node;
            } else {
                Node parent = findParent(node.children.get(i), child);
                if (parent != null) {
                    return parent;
                }
            }
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
}
