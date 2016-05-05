package br.saber.downloadservice.database;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.util.Log;

public class SQLiteHelper extends SQLiteOpenHelper {

	private static final String CATEGORIA = "BANCO";

	private String[] scriptSQLCreate;
	private String scriptSQLDelete;

	
	
	public SQLiteHelper(Context ctx, String nomeBanco, int versaoBanco, String[] scriptCriacaoBanco, String scriptDatabaseDelete) {
		super(ctx, nomeBanco,null, versaoBanco);
		this.scriptSQLCreate = scriptCriacaoBanco;	
		this.scriptSQLDelete = scriptDatabaseDelete;
	}

	@Override
	public void onCreate(SQLiteDatabase db) {

		Log.i(CATEGORIA, "Criando banco com sql");
		int qtdeScripts = scriptSQLCreate.length;
		Log.i(CATEGORIA, ""+qtdeScripts);

		// Executa cada sql passado como parâmetro
		for (int i = 0; i < qtdeScripts; i++) {
			String sql = scriptSQLCreate[i];
			Log.i(CATEGORIA, sql);
			// Cria o banco de dados executando o script de criação
			db.execSQL(sql);
		}
		
		
		
	}

	@Override
	// Mudou a versão...
		public void onUpgrade(SQLiteDatabase db, int versaoAntiga, int novaVersao) {
			Log.w(CATEGORIA, "Atualizando da versao " + versaoAntiga + " para " + novaVersao + ". Todos os registros ser�o deletados.");
			Log.i(CATEGORIA, scriptSQLDelete);
			// Deleta as tabelas...
			db.execSQL(scriptSQLDelete);
			// Cria novamente...
			onCreate(db);
		}

}
