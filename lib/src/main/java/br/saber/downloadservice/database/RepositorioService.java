package br.saber.downloadservice.database;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;

public class RepositorioService extends RepositorioGenerico {

	public RepositorioService(Context ctx) {
		super(ctx);
		super.abrir();
	}
	
	public Cursor query(String SQL)
	{
		Cursor c;
		//abre o banco, consulta e fecha
		
		c=super.db.rawQuery(SQL, null);
		return c;
		
	}
	
	public void insert(String id_arquivo,int prioridade, String path, String URL, String md5,long tempo)
	{
		super.db.execSQL("INSERT INTO tb_downloads(id_arquivo,prioridade,destino,URL,tempo,md5) VALUES('" +
				id_arquivo+"', "+
				prioridade+", '"+
				path+"', '"+
				URL+"', '"+
				tempo+"', '"+
				md5 + "')");
	}
	
	public void delete(String whereClause){
		super.db.execSQL("DELETE FROM tb_downloads WHERE "+whereClause  );
	}

	public void updateTempo(long tempo, String id_arquivo)
	{
		ContentValues cv = new ContentValues();
		cv.put("tempo",tempo);
		super.db.update("tb_downloads",cv,"id_arquivo=?",new String[]{id_arquivo});
	}
}
