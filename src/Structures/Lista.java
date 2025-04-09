package Structures;
import Models.Perfume;

public class Lista {
    private Celula first, last;

    public Lista() {
        first = last = null;
    }

    public void insertionFirst(Perfume perfume) {
        if (perfume == null) {
            throw new IllegalArgumentException("Perfume não pode ser nulo");
        }

        Celula tmp = new Celula(perfume);

        if (first == null) {
            first = last = tmp;
        } else {
            tmp.right = first;
            first.left = tmp;
            first = tmp;
        }
    }
}