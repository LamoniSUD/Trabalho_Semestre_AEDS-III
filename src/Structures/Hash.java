package Structures;
import java.io.*;
//Hash de exploração linear
public class Hash {
	//Classe para o armazenamento de dados
	private class DataItem
	{
		private int data;//item de dado: chave/ key

		public int getData() {
			return data;
		}

		public void setData(int data) {
			this.data = data;
		}

		public DataItem(int data) {
			super();
			this.data = data;
		}
	
	}
	//Agora sim, a tabela começa
	private DataItem [] hashArray; //Esse vetor mantém a tabela Hash
	private int arraySize;
	private DataItem nonItem; //para itens eliminados
	public Hash(int arraySize) { //Construtor
		this.arraySize = arraySize; 
		this.hashArray = new DataItem [arraySize];
		this.nonItem = new DataItem(-1); //Item eliminado chave -1;
	}
	public void displayTable()
	{
		System.out.println("Tabela:");
		for(int j=0;j<arraySize;j++)
		{
			System.out.print(hashArray[j] != null ? hashArray[j].getData()+"": "** ");
		}
		System.out.println();
	}
	public int hashingKey(int key)
	{
		return key % arraySize;
	}
	public void insert (DataItem item)
	{
		int key = item.getData(); //extraindo a chave
		int hashVal = hashingKey(key);//convertendo a chave
		while (hashArray [hashVal] != null && hashArray[hashVal].getData() !=-1)
		{
			++hashVal;
			hashVal %= arraySize;
			hashArray[hashVal] = item;
		}
	}
	public DataItem delete (int key)
	{
		int hashVal = hashingKey(key);
		while (hashArray[hashVal] != null)
		{
		if(hashArray[hashVal].getData())
		{	
			DataItem temp = hashArray [hashVal];
			hashArray[hashVal] = nonItem;
			return temp;
		}
			++hashVal;
			hashVal %=arraySize;
		}
		return null;
	}
	public DataItem find (int key)
	{
		int hashVal = hashingKey(key);
		while (hashArray[hashVal] != null)
		{
			if(hashArray[hashVal].getData() == key)
			{
				return hashArray[hashVal];
			++hashVal;
			hashVal %= arraySize;
			}
			return null;
		}
	}
	
	
	
	
}
