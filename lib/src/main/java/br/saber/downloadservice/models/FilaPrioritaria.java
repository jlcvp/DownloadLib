package br.saber.downloadservice.models;

import java.util.Comparator;
import java.util.PriorityQueue;

import android.util.Log;

public class FilaPrioritaria {
	
	private PriorityQueue<Downloadable> queue;
	
	/**
	 * Cria uma fila vazia, mas pronta para receber novos dados
	 */
	public FilaPrioritaria()
	{
		Comparator<Downloadable> comparator = new DownloadablePriorityComparator();
		queue = new PriorityQueue<Downloadable>(10,comparator);

	}


	
	/**
	 * Cria uma fila já povoada com os dados passados como parâmetro	 
	 */
	public FilaPrioritaria(String[] URLs, int[] prioridades, String[] id_arquivo, String[] path_destino, String[] md5)
	{
		Comparator<Downloadable> comparator = new DownloadablePriorityComparator();
		queue = new PriorityQueue<Downloadable>(URLs.length,comparator);
		
		for(int i=0; i<URLs.length; i++){
			Downloadable d = new Downloadable(URLs[i], prioridades[i], id_arquivo[i], path_destino[i],md5[i]);
			queue.add(d);
		}
	}
	
	public void enqueue(String url, int prioridade, String id_arquivo, String path_destino, String md5)
	{
		Downloadable downloadable = new Downloadable(url, prioridade, id_arquivo, path_destino, md5);
		
		queue.add(downloadable);
	}
	
	public void remove(String id_arquivo)
	{
		Downloadable d = new Downloadable(id_arquivo);
		boolean isRemoved = queue.remove(d);
		Log.i("DEBUG", "Arquivo removido? " + isRemoved);
	}
	
	/**
	 * Gets and removes the head of the queue.
	 * @return the head of the queue or null if the queue is empty.
	 */
	public Downloadable poll()
	{
		return queue.poll();		
		
	}
	
	public void enqueue(Downloadable downloadable)
	{
		if(downloadable!=null)
		{
			queue.add(downloadable);
		}
	}
	
	public int getQueueSize()
	{
		return queue.size();
	}
	
	public boolean isEmpty()
	{
		return queue.isEmpty();
	}


    public Downloadable get(String id_arquivo)
    {
        if(!isEmpty()) {
            Object dArray[] = queue.toArray();
            Downloadable d;
            for(Object c : dArray)
            {
                d = (Downloadable)c;
                if(d.id_arquivo.equals(id_arquivo))
                {
                    return d;
                }
            }
        }
        return null;
    }
	

	public class DownloadablePriorityComparator implements Comparator<Downloadable> {

		@Override
		public int compare(Downloadable arg0, Downloadable arg1) {
			if(arg0.prioridade < arg1.prioridade)
			{
				return -1;
			}
			if(arg0.prioridade > arg1.prioridade)
			{
				return 1;
			}
			return 0;
		}
	}

}
