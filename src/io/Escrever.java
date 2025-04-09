package io;
import java.io.*;
import java.time.LocalDate;
import java.util.Scanner;

import Models.Perfume;

public class Escrever {
    public void write(Scanner scan) {
        try (RandomAccessFile raf = new RandomAccessFile("archive.bin", "rw")) {
            System.out.println("Digite o nome do perfume: ");
            String name = scan.nextLine();

            System.out.println("Digite a marca do perfume: ");
            String marca = scan.nextLine();

            System.out.println("Digite o valor do perfume (em centavos): ");
            int value = scan.nextInt();

            System.out.println("Digite o estoque do perfume: ");
            int stock = scan.nextInt();

            LocalDate date = LocalDate.now();

            Perfume perfume = new Perfume(name, marca, value, date);
            perfume.setStock(stock);

            byte[] perfumeBytes = perfume.toByteArray();

            raf.seek(raf.length());
            raf.writeInt(perfumeBytes.length);
            raf.write(perfumeBytes);

            System.out.println("Perfume adicionado com sucesso!");
            System.out.println("Dados gravados: " + perfume.toString());
        } catch (IOException e) {
            System.out.println("Erro ao escrever no arquivo: " + e.getMessage());
        }
    }
}