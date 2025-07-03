package Services;

import java.util.Arrays;
import java.util.Comparator;
import java.util.AbstractMap.SimpleEntry;
import java.util.ArrayList;
import java.util.List;

public class CriptografiaColunar {

    private final String chave;
    private static final int DESLOCAMENTO_CESAR = 3; // Deslocamento fixo para a Cifra de César

    public CriptografiaColunar(String chave) {
        if (chave == null || chave.isEmpty()) {
            throw new IllegalArgumentException("A chave não pode ser nula ou vazia.");
        }
        this.chave = chave.toUpperCase();
    }

    // Método auxiliar para aplicar a Cifra de César
    private char aplicarCifraCesar(char caractere, int deslocamento) {
        if (Character.isLetter(caractere)) {
            char base = Character.isUpperCase(caractere) ? 'A' : 'a';
            return (char) (((caractere - base + deslocamento) % 26) + base);
        }
        return caractere; // Retorna o caractere inalterado se não for uma letra
    }

    public String criptografar(String textoOriginal) {
        if (textoOriginal == null || textoOriginal.isEmpty()) {
            return "";
        }

        StringBuilder letrasParaCifrar = new StringBuilder();
        List<SimpleEntry<Integer, Character>> caracteresNaoLetras = new ArrayList<>();

        for (int i = 0; i < textoOriginal.length(); i++) {
            char caractere = textoOriginal.charAt(i);
            if (Character.isLetter(caractere)) {
                letrasParaCifrar.append(caractere);
            } else {
                caracteresNaoLetras.add(new SimpleEntry<>(i, caractere));
            }
        }

        String letrasNormalizadas = letrasParaCifrar.toString();
        if (letrasNormalizadas.isEmpty()) {
            return textoOriginal;
        }

        int numColunas = chave.length();
        int numLinhas = (int) Math.ceil((double) letrasNormalizadas.length() / numColunas);
        char[][] grade = new char[numLinhas][numColunas];

        int k = 0;
        for (int i = 0; i < numLinhas; i++) {
            for (int j = 0; j < numColunas; j++) {
                if (k < letrasNormalizadas.length()) {
                    grade[i][j] = letrasNormalizadas.charAt(k++);
                } else {
                    grade[i][j] = 'X';
                }
            }
        }

        SimpleEntry<Character, Integer>[] colunasOrdenadas = new SimpleEntry[numColunas];
        for (int i = 0; i < numColunas; i++) {
            colunasOrdenadas[i] = new SimpleEntry<>(chave.charAt(i), i);
        }
        Arrays.sort(colunasOrdenadas, Comparator.comparing(SimpleEntry::getKey));

        StringBuilder letrasCifradas = new StringBuilder();
        for (SimpleEntry<Character, Integer> entry : colunasOrdenadas) {
            int indiceColunaOriginal = entry.getValue();
            for (int i = 0; i < numLinhas; i++) {
                letrasCifradas.append(grade[i][indiceColunaOriginal]);
            }
        }

        // --- Aplicar Cifra de César +3 após a transposição colunar ---
        StringBuilder cifradoComCesar = new StringBuilder();
        for (char c : letrasCifradas.toString().toCharArray()) {
            cifradoComCesar.append(aplicarCifraCesar(c, DESLOCAMENTO_CESAR));
        }
        String cifradoTemporario = cifradoComCesar.toString();
        // --- Fim da aplicação da Cifra de César ---

        StringBuilder textoFinalCifrado = new StringBuilder();
        int indiceCifrado = 0;

        for (int i = 0; i < textoOriginal.length(); i++) {
            boolean isNonLetter = false;
            for(SimpleEntry<Integer, Character> nonLetter : caracteresNaoLetras) {
                if (nonLetter.getKey() == i) {
                    textoFinalCifrado.append(nonLetter.getValue());
                    isNonLetter = true;
                    break;
                }
            }
            if (!isNonLetter) {
                if (indiceCifrado < cifradoTemporario.length()) {
                    textoFinalCifrado.append(cifradoTemporario.charAt(indiceCifrado++));
                }
            }
        }
        while (indiceCifrado < cifradoTemporario.length()) {
            textoFinalCifrado.append(cifradoTemporario.charAt(indiceCifrado++));
        }

        return textoFinalCifrado.toString();
    }

    public String descriptografar(String textoCifrado) {
        if (textoCifrado == null || textoCifrado.isEmpty()) {
            return "";
        }

        StringBuilder letrasCifradasParaDescript = new StringBuilder();
        List<SimpleEntry<Integer, Character>> caracteresNaoLetras = new ArrayList<>();

        for (int i = 0; i < textoCifrado.length(); i++) {
            char caractere = textoCifrado.charAt(i);
            if (Character.isLetter(caractere)) {
                letrasCifradasParaDescript.append(caractere);
            } else {
                caracteresNaoLetras.add(new SimpleEntry<>(i, caractere));
            }
        }

        String cifradoNormalizado = letrasCifradasParaDescript.toString();
        if (cifradoNormalizado.isEmpty()) {
            return textoCifrado;
        }

        // --- Desfazer Cifra de César -3 antes da transposição colunar ---
        StringBuilder decifradoCesar = new StringBuilder();
        for (char c : cifradoNormalizado.toCharArray()) {
            decifradoCesar.append(aplicarCifraCesar(c, -DESLOCAMENTO_CESAR)); // Deslocamento negativo para reverter
        }
        cifradoNormalizado = decifradoCesar.toString();
        // --- Fim da reversão da Cifra de César ---

        int numColunas = chave.length();
        int numLinhas = (int) Math.ceil((double) cifradoNormalizado.length() / numColunas);

        if (numLinhas == 0 && cifradoNormalizado.length() > 0) {
            numLinhas = 1;
        }

        char[][] grade = new char[numLinhas][numColunas];

        SimpleEntry<Character, Integer>[] colunasOrdenadas = new SimpleEntry[numColunas];
        for (int i = 0; i < numColunas; i++) {
            colunasOrdenadas[i] = new SimpleEntry<>(chave.charAt(i), i);
        }
        Arrays.sort(colunasOrdenadas, Comparator.comparing(SimpleEntry::getKey));

        int k = 0;
        for (SimpleEntry<Character, Integer> entry : colunasOrdenadas) {
            int indiceColunaOriginal = entry.getValue();
            for (int i = 0; i < numLinhas; i++) {
                if (k < cifradoNormalizado.length()) {
                    grade[i][indiceColunaOriginal] = cifradoNormalizado.charAt(k++);
                } else {
                    grade[i][indiceColunaOriginal] = 'X'; 
                }
            }
        }

        StringBuilder letrasDescriptografadas = new StringBuilder();
        for (int i = 0; i < numLinhas; i++) {
            for (int j = 0; j < numColunas; j++) {
                letrasDescriptografadas.append(grade[i][j]);
            }
        }

        String letrasDescript = letrasDescriptografadas.toString();
        while (letrasDescript.length() > 0 && letrasDescript.endsWith("X")) {
            letrasDescript = letrasDescript.substring(0, letrasDescript.length() - 1);
        }
        while (letrasDescript.length() > 0 && letrasDescript.endsWith(" ")) {
            letrasDescript = letrasDescript.substring(0, letrasDescript.length() - 1);
        }

        StringBuilder textoOriginalRestaurado = new StringBuilder();
        int indiceLetrasDescript = 0;
        int proximoNaoLetra = 0;

        for (int i = 0; i < textoCifrado.length(); i++) {
            if (proximoNaoLetra < caracteresNaoLetras.size() && caracteresNaoLetras.get(proximoNaoLetra).getKey() == i) {
                textoOriginalRestaurado.append(caracteresNaoLetras.get(proximoNaoLetra).getValue());
                proximoNaoLetra++;
            } else {
                if (indiceLetrasDescript < letrasDescript.length()) {
                    textoOriginalRestaurado.append(letrasDescript.charAt(indiceLetrasDescript++));
                }
            }
        }
        return textoOriginalRestaurado.toString();
    }
}