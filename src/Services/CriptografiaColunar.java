package Services;

import java.util.Arrays;
import java.util.Comparator;
import java.util.AbstractMap.SimpleEntry;
import java.util.ArrayList; // Para armazenar o mapeamento de caracteres
import java.util.List;     // Para armazenar o mapeamento de caracteres

public class CriptografiaColunar {

    private final String chave; // A palavra-chave que define a ordem das colunas

    /**
     * Construtor para a Cifra de Transposição Colunar.
     * @param chave A palavra-chave usada para reordenar as colunas.
     * Caracteres duplicados na chave serão tratados como únicos.
     */
    public CriptografiaColunar(String chave) {
        if (chave == null || chave.isEmpty()) {
            throw new IllegalArgumentException("A chave não pode ser nula ou vazia.");
        }
        this.chave = chave.toUpperCase(); // A chave em si ainda é convertida para maiúsculas para ordenação
    }

    /**
     * Criptografa uma mensagem usando a cifra de transposição colunar,
     * preservando espaços e caracteres especiais e o case original.
     * @param textoOriginal O texto a ser criptografado.
     * @return O texto criptografado.
     */
    public String criptografar(String textoOriginal) {
        if (textoOriginal == null || textoOriginal.isEmpty()) {
            return "";
        }

        // 1. Extrair apenas as letras e mapear caracteres não-letras
        StringBuilder letrasParaCifrar = new StringBuilder();
        // SimpleEntry<ÍndiceOriginal, CaractereOriginal>
        List<SimpleEntry<Integer, Character>> caracteresNaoLetras = new ArrayList<>();

        for (int i = 0; i < textoOriginal.length(); i++) {
            char caractere = textoOriginal.charAt(i);
            if (Character.isLetter(caractere)) {
                letrasParaCifrar.append(caractere);
            } else {
                caracteresNaoLetras.add(new SimpleEntry<>(i, caractere));
            }
        }

        String letrasNormalizadas = letrasParaCifrar.toString(); // As letras extraídas, mantendo o case
        if (letrasNormalizadas.isEmpty()) {
            return textoOriginal; // Se não houver letras, retorna o original (não há o que cifrar)
        }

        // 2. Aplicar a cifra de transposição apenas às letras
        int numColunas = chave.length();
        int numLinhas = (int) Math.ceil((double) letrasNormalizadas.length() / numColunas);
        char[][] grade = new char[numLinhas][numColunas];

        // Preencher a grade com letras (upper case para a transposição, mas armazenar o case original)
        int k = 0;
        for (int i = 0; i < numLinhas; i++) {
            for (int j = 0; j < numColunas; j++) {
                if (k < letrasNormalizadas.length()) {
                    grade[i][j] = letrasNormalizadas.charAt(k++);
                } else {
                    grade[i][j] = 'X'; // Caractere de preenchimento (padding)
                }
            }
        }

        // Determinar a ordem de leitura das colunas com base na chave (chave em maiúsculas)
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

        // 3. Reinserir caracteres não-letras e manter o tamanho original
        StringBuilder textoFinalCifrado = new StringBuilder();
        String cifradoTemporario = letrasCifradas.toString();
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
        // Se ainda houver caracteres cifrados não usados (por conta do padding), adicione-os no final.
        // Isso é crucial para a descriptografia saber o tamanho real do bloco cifrado de letras.
        while (indiceCifrado < cifradoTemporario.length()) {
            textoFinalCifrado.append(cifradoTemporario.charAt(indiceCifrado++));
        }


        return textoFinalCifrado.toString();
    }

    /**
     * Descriptografa uma mensagem usando a cifra de transposição colunar,
     * restaurando espaços e caracteres especiais e o case original.
     * @param textoCifrado O texto a ser descriptografado.
     * @return O texto original.
     */
    public String descriptografar(String textoCifrado) {
        if (textoCifrado == null || textoCifrado.isEmpty()) {
            return "";
        }

        // 1. Separar o texto cifrado em letras cifradas e caracteres não-letras
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
            return textoCifrado; // Se não houver letras, retorna o original (não há o que decifrar)
        }

        // 2. Descriptografar apenas as letras cifradas
        int numColunas = chave.length();
        int numLinhas = (int) Math.ceil((double) cifradoNormalizado.length() / numColunas);

        // Se o texto cifrado é menor que o esperado para preencher a grade mínima,
        // pode indicar corrupção ou chave errada.
        if (cifradoNormalizado.length() < numColunas && cifradoNormalizado.length() > 0) {
             // Isso pode acontecer se a chave for muito grande para um texto muito pequeno
             // ou se o padding foi removido incorretamente antes.
             // Para simplificar, vamos ajustar numLinhas se houver apenas uma linha parcial.
             if (numLinhas == 0) numLinhas = 1; // Garante pelo menos uma linha para textos curtos
        }


        char[][] grade = new char[numLinhas][numColunas];

        // Determinar a ordem de escrita das colunas (inversa da leitura)
        SimpleEntry<Character, Integer>[] colunasOrdenadas = new SimpleEntry[numColunas];
        for (int i = 0; i < numColunas; i++) {
            colunasOrdenadas[i] = new SimpleEntry<>(chave.charAt(i), i);
        }
        Arrays.sort(colunasOrdenadas, Comparator.comparing(SimpleEntry::getKey));

        // Preencher a grade na ordem de leitura inversa
        int k = 0;
        for (SimpleEntry<Character, Integer> entry : colunasOrdenadas) {
            int indiceColunaOriginal = entry.getValue();
            for (int i = 0; i < numLinhas; i++) {
                if (k < cifradoNormalizado.length()) { // Garante que não ultrapasse o comprimento
                    grade[i][indiceColunaOriginal] = cifradoNormalizado.charAt(k++);
                } else {
                    grade[i][indiceColunaOriginal] = ' '; // Preenche com espaço se não houver mais caracteres (pode ser 'X' ou outro padding)
                }
            }
        }

        StringBuilder letrasDescriptografadas = new StringBuilder();
        // Ler a grade na ordem original para reconstruir o texto de letras
        for (int i = 0; i < numLinhas; i++) {
            for (int j = 0; j < numColunas; j++) {
                letrasDescriptografadas.append(grade[i][j]);
            }
        }

        // Remover caracteres de preenchimento (padding) do final das letras descriptografadas
        String letrasDescript = letrasDescriptografadas.toString();
        while (letrasDescript.length() > 0 && letrasDescript.endsWith("X")) { // O 'X' é o nosso padding
            letrasDescript = letrasDescript.substring(0, letrasDescript.length() - 1);
        }
        // Também remove espaços extras do padding que podem ter sido adicionados
        while (letrasDescript.length() > 0 && letrasDescript.endsWith(" ")) { // Se o padding era espaço, remova
             letrasDescript = letrasDescript.substring(0, letrasDescript.length() - 1);
        }


        // 3. Reinserir caracteres não-letras nas posições originais
        StringBuilder textoOriginalRestaurado = new StringBuilder();
        int indiceLetrasDescript = 0;
        int proximoNaoLetra = 0;

        for (int i = 0; i < textoCifrado.length(); i++) { // Itera pelo comprimento ORIGINAL esperado
            if (proximoNaoLetra < caracteresNaoLetras.size() && caracteresNaoLetras.get(proximoNaoLetra).getKey() == i) {
                textoOriginalRestaurado.append(caracteresNaoLetras.get(proximoNaoLetra).getValue());
                proximoNaoLetra++;
            } else {
                if (indiceLetrasDescript < letrasDescript.length()) {
                    textoOriginalRestaurado.append(letrasDescript.charAt(indiceLetrasDescript++));
                }
                // Se a string de letras descriptografadas for menor do que o original esperava
                // isso pode significar que o texto original era menor que o total de letras extraídas do cifrado
                // (por causa do padding extra inserido na criptografia que não foi um 'X')
            }
        }
        return textoOriginalRestaurado.toString();
    }

    /**
     * Exemplo de uso da Cifra de Transposição Colunar com preservação total.
     */
    public static void main(String[] args) {
        String chave = "SEGREDO";
        CriptografiaColunar cc = new CriptografiaColunar(chave);

        System.out.println("--- Teste 1: Nome com espaços e case ---");
        String original1 = "Como Moisele";
        System.out.println("Original: " + original1);
        String cifrado1 = cc.criptografar(original1);
        System.out.println("Cifrado: " + cifrado1);
        String descriptografado1 = cc.descriptografar(cifrado1);
        System.out.println("Descriptografado: " + descriptografado1);
        System.out.println("Igual ao Original? " + original1.equals(descriptografado1)); // Deve ser true

        System.out.println("\n--- Teste 2: Frase com números e símbolos ---");
        String original2 = "Olá, Mundo! 123. R$ 50,00";
        System.out.println("Original: " + original2);
        String cifrado2 = cc.criptografar(original2);
        System.out.println("Cifrado: " + cifrado2);
        String descriptografado2 = cc.descriptografar(cifrado2);
        System.out.println("Descriptografado: " + descriptografado2);
        System.out.println("Igual ao Original? " + original2.equals(descriptografado2)); // Deve ser true

        System.out.println("\n--- Teste 3: Texto mais longo ---");
        String original3 = "A criptografia é a arte e a ciência de escrever mensagens em código.";
        System.out.println("Original: " + original3);
        String cifrado3 = cc.criptografar(original3);
        System.out.println("Cifrado: " + cifrado3);
        String descriptografado3 = cc.descriptografar(cifrado3);
        System.out.println("Descriptografado: " + descriptografado3);
        System.out.println("Igual ao Original? " + original3.equals(descriptografado3)); // Deve ser true

        System.out.println("\n--- Teste 4: Texto sem letras ---");
        String original4 = "123.456 !@#$";
        System.out.println("Original: " + original4);
        String cifrado4 = cc.criptografar(original4);
        System.out.println("Cifrado: " + cifrado4);
        String descriptografado4 = cc.descriptografar(cifrado4);
        System.out.println("Descriptografado: " + descriptografado4);
        System.out.println("Igual ao Original? " + original4.equals(descriptografado4)); // Deve ser true
    }
}