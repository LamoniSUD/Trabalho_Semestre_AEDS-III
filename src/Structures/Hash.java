package Structures;
import java.io.*;

public class Hash {
    private class DataItem {
        private int data;
        public DataItem(int data) { this.data = data; }
        public int getData() { return data; }
        public void setData(int data) { this.data = data; }
    }

    private DataItem[] hashArray;
    private int arraySize;
    private DataItem nonItem;

    public Hash(int arraySize) {
        this.arraySize = arraySize;
        this.hashArray = new DataItem[arraySize];
        this.nonItem = new DataItem(-1);
    }

    public void displayTable() {
        System.out.println("Tabela:");
        for (int j = 0; j < arraySize; j++) {
            System.out.print(hashArray[j] != null ? hashArray[j].getData() + " " : "** ");
        }
        System.out.println();
    }

    public int hashingKey(int key) {
        return key % arraySize;
    }

    public void insert(DataItem item) {
        int key = item.getData();
        int hashVal = hashingKey(key);

        while (hashArray[hashVal] != null && hashArray[hashVal].getData() != -1) {
            ++hashVal;
            hashVal %= arraySize;
        }
        hashArray[hashVal] = item;
    }

    public DataItem delete(int key) {
        int hashVal = hashingKey(key);

        while (hashArray[hashVal] != null) {
            if (hashArray[hashVal].getData() == key) {
                DataItem temp = hashArray[hashVal];
                hashArray[hashVal] = nonItem;
                return temp;
            }
            ++hashVal;
            hashVal %= arraySize;
        }
        return null;
    }

    public DataItem find(int key) {
        int hashVal = hashingKey(key);

        while (hashArray[hashVal] != null) {
            if (hashArray[hashVal].getData() == key) {
                return hashArray[hashVal];
            }
            ++hashVal;
            hashVal %= arraySize;
        }
        return null;
    }
}
