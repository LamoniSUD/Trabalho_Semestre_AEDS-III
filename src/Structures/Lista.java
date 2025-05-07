package Structures;
import Models.Perfume;

public class Lista {
    private Celula first, last;

    public Lista() {
        first = setLast(null);
    }

    public void insertionFirst(Perfume perfume) {
        if (perfume == null) {
            throw new IllegalArgumentException("Perfume não pode ser nulo");
        }

        Celula tmp = new Celula(perfume);

        if (first == null) {
            first = setLast(tmp);
        } else {
            tmp.right = first;
            first.left = tmp;
            first = tmp;
        }
    }

	public Celula getLast() {
		return last;
	}

	public Celula setLast(Celula last) {
		this.last = last;
		return last;
	}
}