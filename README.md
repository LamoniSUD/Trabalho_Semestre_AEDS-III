import java.io.*;
import java.util.Random;
import java.time.LocalDate;
import java.util.Scanner;

public class Main {
	private Scanner scan = new Scanner(System.in);
	private Escrever escrever = new Escrever();
	private Ler ler = new Ler();

	public void menu() {
		while (true) {
			System.out.println("\nMenu:");
			System.out.println("1. Add a Parfum in stock");
			System.out.println("2. Show Parfums in Stock");
			System.out.println("3. Update perfume");
			System.out.println("4. Delete the archive");
			System.out.println("5. Show Out of Stock Parfums");
			System.out.println("6. Quit");
			System.out.print("Choose your option: ");

			int opcao = scan.nextInt();
			scan.nextLine();

			switch (opcao) {
			case 1:
				escrever.write(scan);
				break;
			case 2:
				ler.readIn();
				break;
			case 3:
				System.out.println("Escreva o Código ou nome do Produto");
				String term = scan.nextLine();
				Perfume parfum = searchPerfume(term);

				if (parfum != null) {
					System.out.println(parfum.toString());
					update(term);
				} else {
					System.out.println("Perfume não encontrado.");
				}
				break;
			case 4:
				System.out.println("Deseja Deletar arquivo completamente? sim/nao");
				String choice = scan.nextLine();
				if(choice.equalsIgnoreCase("sim")) {
					deletarArquivo();
				}
				break;
			case 5:
				ler.readOut();
				break;
			case 6:
				System.out.println("Saindo...");
				return;
			default:
				System.out.println("Opção inválida. Tente novamente.");
			}
		}
	}

	private void deletarArquivo() {
		File arquivo = new File("archive.bin");
		if(arquivo.exists()) {
			System.out.println("Arquivo Existe");
		}else {
			System.out.println("O arquivo não existe.");
		}
		if (arquivo.delete()) {
			System.out.println("Arquivo Deletado com Sucesso");
		} else {
			System.out.println("Falha ao deletar o arquivo.");}
		
	}

	public Perfume searchPerfume(String searchTerm) {
		try (RandomAccessFile raf = new RandomAccessFile("archive.bin", "rw")) {
			long fileLength = raf.length();

			while (raf.getFilePointer() < fileLength) {
				int size = raf.readInt();
				byte[] data = new byte[size];
				raf.readFully(data);
				Perfume perfume = new Perfume();
				perfume = Perfume.fromByteArray(data); // Alterado para chamada de método, retornando o perfume

				if (searchTerm.equalsIgnoreCase(perfume.getName())
						|| searchTerm.equals(String.valueOf(perfume.getId()))) {
					return perfume;
				}
			}
		} catch (IOException e) {
			System.out.println("Erro ao ler o arquivo: " + e.getMessage());
		}
		return null;
	}

	public void update(String term) {
		try (RandomAccessFile raf = new RandomAccessFile("archive.bin", "rw")) {
			long fileLength = raf.length();

			while (raf.getFilePointer() < fileLength) {
				int size = raf.readInt();
				byte[] data = new byte[size];
				raf.readFully(data);
				Perfume perfume = new Perfume();
				perfume = Perfume.fromByteArray(data); // Alterado para chamada de método, retornando o perfume

				if (term.equalsIgnoreCase(perfume.getName()) || term.equals(String.valueOf(perfume.getId()))) {
					System.out.println("O que deseja alterar?\n1- Nome\n2- Marca\n3- Estoque\n4- Valor");
					int opcao = scan.nextInt();
					scan.nextLine();

					switch (opcao) {
					case 1:
						System.out.println("Escreva o novo Nome: ");
						perfume.setName(scan.nextLine());
						break;
					case 2:
						System.out.println("Escreva a nova Marca: ");
						perfume.setMarca(scan.nextLine());
						break;
					case 3:
						System.out.println("Escreva a nova quantidade em Estoque: ");
						perfume.setStock(scan.nextInt());
						break;
					case 4:
						System.out.println("Escreva o novo valor: ");
						perfume.setValue(scan.nextInt());
						break;
					default:
						System.out.println("Opção inválida.");
						return;
					}

					// Voltar para o ponto de gravação do perfume e reescrever
					raf.seek(raf.getFilePointer() - size - 4); // 4 é o espaço do tamanho do perfume
					byte[] updatedData = perfume.toByteArray();
					raf.writeInt(updatedData.length);
					raf.write(updatedData);

					System.out.println("Perfume atualizado com sucesso!");
					return;
				}
			}
		} catch (IOException e) {
			System.out.println("Erro ao ler o arquivo: " + e.getMessage());
		}
		System.out.println("Perfume não encontrado.");
	}

	public static void main(String[] args) {
		Main main = new Main();
		main.menu();
	}
}

class Ler {
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
                perfume = Perfume.fromByteArray(data); // Alterado para chamada de método, retornando o perfume
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
				perfume = Perfume.fromByteArray(data); // Alterado para chamada de método, retornando o perfume

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

class Escrever {
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

			raf.seek(raf.length()); // Move para o final do arquivo
			raf.writeInt(perfumeBytes.length);
			raf.write(perfumeBytes); // Escreve o perfume serializado

			System.out.println("Perfume adicionado com sucesso!");
			System.out.println("Dados gravados: " + perfume.toString()); // Adicionando para verificar
		} catch (IOException e) {
			System.out.println("Erro ao escrever no arquivo: " + e.getMessage());
		}
	}

}

class Perfume {
	private int id;
	private boolean available;
	private String[] info;
	private int value;
	private int stock;
	private LocalDate date;

	public Perfume() {
		this(-1, false, "", "", -1, -1, LocalDate.now());
	}

	public Perfume(String name, String marca, int value, LocalDate date) {
		Random rand = new Random();
		int ano = LocalDate.now().getYear();
		int numeroAleatorio = rand.nextInt(9000) + 1000;
		this.id = Integer.parseInt(ano + "" + numeroAleatorio);

		this.value = value;
		this.available = true;
		this.info = new String[2];
		this.info[0] = name;
		this.info[1] = marca;
		this.date = date;
	}

	public Perfume(int i, boolean b, String inf1, String inf2, int V, int S, LocalDate D) {
		// TODO Auto-generated constructor stub
		this.id = i;
		this.available = b;
		this.info = new String[2];
		this.info[0] = inf1;
		this.info[1] = inf2;
		this.value = V;
		this.stock = S;
		this.date = D;
	}

	public int getId() {
		return id;
	}

	public void setId(int id) {
		this.id = id;
	}

	public boolean isAvailable() {
		return available;
	}

	public void setAvailable(boolean available) {
		this.available = available;
	}

	public String getName() {
		return info[0];
	}

	public void setName(String name) {
		this.info[0] = name;
	}

	public String getMarca() {
		return info[1];
	}

	public void setMarca(String marca) {
		this.info[1] = marca;
	}

	public float getValue() {
		return (float)value;
	}

	public void setValue(int value) {
		this.value = value;
	}

	public int getStock() {
		return stock;
	}

	 public void setStock(int stock) {
         this.stock = stock;
         this.available = stock > 0;
     }

	public LocalDate getDate() {
		return date;
	}

	public void setDate(LocalDate date) {
		this.date = date;
	}

	public byte[] toByteArray() {
		try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
				DataOutputStream dos = new DataOutputStream(baos)) {
			dos.writeInt(this.id);
			dos.writeBoolean(this.available);
			dos.writeUTF(this.info[0]);
			dos.writeUTF(this.info[1]);
			dos.writeInt(this.value);
			dos.writeInt(this.stock);
			dos.writeLong(this.date.toEpochDay());
			return baos.toByteArray();
		} catch (IOException e) {
			System.out.println("Erro ao converter para byte array: " + e.getMessage());
		}
		return new byte[0];
	}

	public static Perfume fromByteArray(byte[] data) {
		try (ByteArrayInputStream bais = new ByteArrayInputStream(data);
				DataInputStream dis = new DataInputStream(bais)) {
			int id = dis.readInt();
			boolean available = dis.readBoolean();
			String name = dis.readUTF();
			String marca = dis.readUTF();
			int value = dis.readInt();
			int stock = dis.readInt();
			LocalDate date = LocalDate.ofEpochDay(dis.readLong());
			return new Perfume(id, available, name, marca, value, stock, date);
		} catch (IOException e) {
			System.out.println("Erro ao converter byte array para Perfume: " + e.getMessage());
		}
		return null;
	}

	@Override
	public String toString() {
     	return "\nID........: " + this.id +
     	"\nAvaiable...:" + this.available +
     	"\nNome.......: " + this.info[0] +
     	"\nMarca......: " + this.info[1] +
     	"\nPreço......: " + this.value +
     	"\nEstoque....:" + this.stock +
     	"\nData.......: " + this.date;
     	}
}

class Lista {
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

class Celula {
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
