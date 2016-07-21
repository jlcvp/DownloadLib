package br.saber.downloadservice.models;

public class Downloadable {
	public String url;
	public int prioridade;
	public String id_arquivo;
	public String path_destino;
	public String md5;
	public long tempo;
	public int retries;
    public int id_int;
	
	public Downloadable(String url, int prioridade, String id_arquivo, String path_destino, String md5) {
		
		this.url = url;
		this.prioridade = prioridade;
		this.id_arquivo = id_arquivo;
		this.path_destino = path_destino;
		this.md5 = md5;
        this.id_int = genIntId(id_arquivo,md5);
		tempo = 0;
		retries = 0;
	}


	
	public Downloadable(String id_arquivo)
	{
		this.id_arquivo = id_arquivo;
	}

    private int genIntId(String id_arquivo, String md5){

        String s = (id_arquivo+md5).substring(0,id_arquivo.length()+(md5.length()/2));

        int hash = 7;
        for (int i = 0; i < s.length(); i++) {
            hash = hash*11 - ((int) s.charAt(i))*3;
        }

        return hash;
    }
	
	@Override
	public boolean equals(Object o) {
		if(o instanceof Downloadable) {
			Downloadable d = (Downloadable) o;
			return d.id_arquivo.equals(this.id_arquivo);
		}
		else
		{
			return false;
		}
	}
	
	
	
}