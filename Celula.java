package Structures;
import Models.Perfume;

public class Celula {
    Perfume perfume;
    Celula left, right;

    public Celula(Perfume perfume) {
        this.perfume = perfume;
        this.left = this.right = null;
    }

    public Perfume getPerfume() {
        return perfume;
    }

    public void setPerfume(Perfume perfume) {
        this.perfume = perfume;
    }
}