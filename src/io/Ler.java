package io;
import java.io.*;

import Models.Perfume;

public class Ler {
    public void readIn() {
        try (RandomAccessFile raf = new RandomAccessFile("archive.bin", "r")) {
            long fileLength = raf.length();

            if (fileLength == 0) {
                System.out.println("O arquivo está vazio.");
                return;
            }

            System.out.println("Perfumes disponíveis:");
            while (raf.getFilePointer() < fileLength) {
                int size = raf.readInt();
                if (size <= 0) {
                    System.out.println("Erro ao ler o tamanho do perfume. Dados corrompidos?");
                    break;
                }
                byte[] data = new byte[size];
                raf.readFully(data);

                Perfume perfume = new Perfume();
                perfume = Perfume.fromByteArray(data);
                if (perfume != null && perfume.isAvailable()) {
                    System.out.println("--------------------");
                    System.out.println("ID: " + perfume.getId());
                    System.out.println("Nome: " + perfume.getName());
                    System.out.println("Marca: " + perfume.getMarca());
                    System.out.println("Valor: R$ " + perfume.getValue() / 100.0);
                    System.out.println("Estoque: " + perfume.getStock());
                    System.out.println("Data: " + perfume.getDate());
                }
            }
        } catch (IOException e) {
            System.out.println("Erro ao ler o arquivo: " + e.getMessage());
        }
    }

    public void readOut() {
        try (RandomAccessFile raf = new RandomAccessFile("archive.bin", "r")) {
            long fileLength = raf.length();

            System.out.println("Perfumes não disponíveis:");

            while (raf.getFilePointer() < fileLength) {
                int size = raf.readInt();
                byte[] data = new byte[size];
                raf.readFully(data);
                Perfume perfume = new Perfume();
                perfume = Perfume.fromByteArray(data);

                if (!perfume.isAvailable()) {
                    System.out.println("--------------------");
                    System.out.println("ID: " + perfume.getId());
                    System.out.println("Nome: " + perfume.getName());
                    System.out.println("Marca" + perfume.getMarca());
                    System.out.println("Valor: R$ " + perfume.getValue() / 100);
                    System.out.println("Estoque: " + perfume.getStock());
                    System.out.println("Em estoque: " + (perfume.isAvailable() ? "Sim" : "Não"));
                }
            }
        } catch (IOException e) {
            System.out.println("Erro ao ler o arquivo: " + e.getMessage());
        }
    }
}