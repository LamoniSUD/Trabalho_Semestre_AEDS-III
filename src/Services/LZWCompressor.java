package Services;

import java.util.HashMap;
import java.util.Map;
import java.util.ArrayList;
import java.util.List;
import java.util.Arrays;

// Implementa o algoritmo de compressão LZW para arrays de bytes.
public class LZWCompressor {

    private static final int DICTIONARY_SIZE = 4096;

    // Comprime um array de bytes utilizando o algoritmo LZW.
    public static byte[] compress(byte[] uncompressedData) {
        if (uncompressedData == null || uncompressedData.length == 0) {
            return new byte[0];
        }

        Map<ByteArrayWrapper, Integer> dictionary = new HashMap<>();
        for (int i = 0; i < 256; i++) {
            dictionary.put(new ByteArrayWrapper(new byte[]{(byte) i}), i);
        }

        ByteArrayWrapper currentSequence = new ByteArrayWrapper(new byte[0]);
        List<Integer> compressedCodes = new ArrayList<>();
        int nextCode = 256;

        for (byte b : uncompressedData) {
            ByteArrayWrapper newSequence = currentSequence.append(b);

            if (dictionary.containsKey(newSequence)) {
                currentSequence = newSequence;
            } else {
                compressedCodes.add(dictionary.get(currentSequence));

                if (nextCode < DICTIONARY_SIZE) {
                    dictionary.put(newSequence, nextCode++);
                }
                currentSequence = new ByteArrayWrapper(new byte[]{b});
            }
        }

        if (currentSequence.length() > 0) {
            compressedCodes.add(dictionary.get(currentSequence));
        }

        byte[] outputBytes = new byte[compressedCodes.size() * 2];
        int byteIndex = 0;
        for (int code : compressedCodes) {
            outputBytes[byteIndex++] = (byte) (code >> 8);
            outputBytes[byteIndex++] = (byte) (code & 0xFF);
        }

        return outputBytes;
    }

    // Descomprime um array de bytes utilizando o algoritmo LZW.
    public static byte[] decompress(byte[] compressedData) {
        if (compressedData == null || compressedData.length == 0) {
            return new byte[0];
        }
        
        // A descompressão LZW espera um tamanho de dado par, pois os códigos são de 2 bytes
        if (compressedData.length % 2 != 0) {
            throw new IllegalArgumentException("Dados comprimidos inválidos: comprimento de dados ímpar.");
        }

        Map<Integer, ByteArrayWrapper> dictionary = new HashMap<>();
        for (int i = 0; i < 256; i++) {
            dictionary.put(i, new ByteArrayWrapper(new byte[]{(byte) i}));
        }

        int nextCode = 256;

        List<Byte> decompressedBytes = new ArrayList<>();

        // Lê o primeiro código (2 bytes)
        int previousCode = ((compressedData[0] & 0xFF) << 8) | (compressedData[1] & 0xFF);

        if (!dictionary.containsKey(previousCode)) {
            throw new IllegalArgumentException("Dados comprimidos inválidos: Primeiro código não está no dicionário inicial.");
        }

        ByteArrayWrapper previousSequence = dictionary.get(previousCode);
        for (byte b : previousSequence.getData()) {
            decompressedBytes.add(b);
        }

        // Processa o restante dos códigos
        for (int i = 2; i < compressedData.length; i += 2) {
            int currentCode = ((compressedData[i] & 0xFF) << 8) | (compressedData[i + 1] & 0xFF);

            ByteArrayWrapper currentSequence;

            // Caso 1: Código existe no dicionário
            if (dictionary.containsKey(currentCode)) {
                currentSequence = dictionary.get(currentCode);
            }
            // Caso 2: Código é o próximo a ser adicionado (nova sequência = P + P[0])
            else if (currentCode == nextCode && previousSequence != null) {
                currentSequence = previousSequence.append(previousSequence.getData()[0]);
            }
            // Caso 3: Código inválido
            else {
                throw new IllegalArgumentException("Dados comprimidos corrompidos ou código LZW inválido: " + currentCode + " na posição de byte " + i);
            }

            // Adiciona a sequência descompactada à lista de bytes
            for (byte b : currentSequence.getData()) {
                decompressedBytes.add(b);
            }

            // Adiciona nova entrada ao dicionário, se houver espaço
            if (previousSequence != null && nextCode < DICTIONARY_SIZE) {
                dictionary.put(nextCode++, previousSequence.append(currentSequence.getData()[0]));
            }

            // Atualiza a sequência anterior para a próxima iteração
            previousSequence = currentSequence;
        }

        // Converte a lista de Bytes em um array de bytes final
        byte[] result = new byte[decompressedBytes.size()];
        for (int i = 0; i < decompressedBytes.size(); i++) {
            result[i] = decompressedBytes.get(i);
        }
        return result;
    }

    // Classe auxiliar estática para usar arrays de bytes como chaves em HashMap.
    // Agora é uma classe interna estática, eliminando a necessidade de importação externa.
    public static class ByteArrayWrapper {
        private final byte[] data;
        private final int hashCode;

        // Construtor do ByteArrayWrapper.
        public ByteArrayWrapper(byte[] data) {
            this.data = data;
            this.hashCode = Arrays.hashCode(data);
        }

        // Retorna uma nova instância com o byte anexado à sequência atual.
        public ByteArrayWrapper append(byte b) {
            byte[] newData = new byte[this.data.length + 1];
            System.arraycopy(this.data, 0, newData, 0, this.data.length);
            newData[this.data.length] = b;
            return new ByteArrayWrapper(newData);
        }

        // Retorna o array de bytes encapsulado.
        public byte[] getData() {
            return data;
        }

        // Retorna o comprimento do array de bytes.
        public int length() {
            return data.length;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            ByteArrayWrapper that = (ByteArrayWrapper) o;
            return Arrays.equals(this.data, that.data);
        }

        @Override
        public int hashCode() {
            return hashCode;
        }
    }
}
