/**
 * 
 */
package br.saber.downloadservice;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Bundle;
import android.os.IBinder;
import android.os.SystemClock;
import android.util.Log;
import br.saber.downloadservice.database.RepositorioScripts;
import br.saber.downloadservice.database.RepositorioService;
import br.saber.downloadservice.models.Downloadable;
import br.saber.downloadservice.models.FilaPrioritaria;


/**
 * @author joao leonardo
 *
 */
public class DownloadService extends Service implements Downloader.onLackOfStorageSpaceListener {
	public static final String ACTION_START_AND_UNBLOCK = "br.saber.downloadlib.service.ACTION_START";
	public static final String ACTION_CANCEL = "br.saber.downloadlib.service.ACTION_CANCEL";
	public static final String PROGRESS_ACTION = "br.saber.downloadservice.progress_action";
	public static final String DOWNLOADSERVICE_MANAGER = "br.saber.downloadService";
    public static final String STORAGE_BYTES_NEEDED = "br.saber.downloadService.STORAGE_BYTES_NEEDED";
    public static final String STORAGE_FULL_ACTION = "br.saber.downloadService.STORAGE_FULL";
	public static final String FLAG_ENQUEUE_DOWNLOAD = "br.saber.downloadService.download";
	public static final String FLAG_CANCEL_DOWNLOAD = "br.saber.downloadService.cancel_download_flag";
    public static final String FLAG_UNBLOCK_DOWNLOADS="br.saber.downloadService.unblock_service";

	public static final String DOWNLOADSERVICE_URL = "br.saber.downloadService.url";
	public static final String DOWNLOADSERVICE_ID = "br.saber.downloadService.id_arquivo";
	public static final String DOWNLOADSERVICE_PRIORIDADE = "br.saber.downloadService.prioridade";
	public static final String DOWNLOADSERVICE_PATH = "br.saber.downloadService.path_arquivo";
	public static final String DOWNLOADSERVICE_MD5 = "br.saber.downloadService.md5";

	
	public static final String LOG_TAG = "DEBUG_SERVICE";
	private static final String SHARED_PREFS_FILENAME = "WOW_SUCH_PREFS";
    private static final String SHARED_PREFS_ISBLOCKED_KEY = "WOW_SUCH_BLOCKED";


    SharedPreferences prefs;
	
	RepositorioService repositorio=null;
	FilaPrioritaria downloadQueue=null;
	Downloader dl;
	//ServiceManagerReceiver mServiceManagerReceiver = new ServiceManagerReceiver();
	boolean alreadyRunning=false;
	
	@Override
	public void onCreate() {
		Log.i(LOG_TAG, "service iniciado (onCreate)");
		super.onCreate();

		repositorio = new RepositorioService(this);
		// 1) buscar os downloads ativos no banco //TODO depois verificar paginação de consulta para otimizar a velocidade
		preparaFila();		
		// 2) iniciar rotina de checagem de rede e loop e download.		
		dl=new Downloader(this, downloadQueue);
		//IntentFilter filter = new IntentFilter(DOWNLOADSERVICE_MANAGER_INTERNO);
		//registerReceiver(mServiceManagerReceiver, filter );


        prefs = getSharedPreferences(SHARED_PREFS_FILENAME,MODE_PRIVATE);

		Context context = getApplicationContext();
		Intent it = new Intent(context, DownloadService.class);
		//Log.d("DEBUG","service onDestroy, fila ainda com coisas");
		AlarmManager alarmMgr = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);       
		PendingIntent alarmIntent = PendingIntent.getService(context, 0, it, PendingIntent.FLAG_CANCEL_CURRENT);       
		alarmMgr.cancel(alarmIntent);
		Log.i("DEBUG","Service ALARM OFF");
		
	}
	
	@Override
	public int onStartCommand(Intent intent, int flags, int startId) {
		Log.i(LOG_TAG, "service chamado (onStartCommand)");
		Bundle pack=null;
		if(intent!=null)
		{
			pack = intent.getExtras();

            if(pack!=null) {
                if (pack.getBoolean(FLAG_ENQUEUE_DOWNLOAD, false)) {
                    Log.d(LOG_TAG, "ENTROU NO IF DE JP");
                    enqueueDownload(pack);
                } else if (pack.getBoolean(FLAG_CANCEL_DOWNLOAD, false)) {
                    String cancel_id = pack.getString(DOWNLOADSERVICE_ID);
                    cancelDownload(cancel_id);
//                    return Service.START_STICKY;
                }
                else if(pack.getBoolean(FLAG_UNBLOCK_DOWNLOADS,false)){

                    prefs.edit().putBoolean(SHARED_PREFS_ISBLOCKED_KEY,false).apply();
                }
            }
		}
		Log.d(LOG_TAG,"pack ="+pack+"\nintent="+intent);



		
//		Log.d("DEBUG", "isConnected = "+isConnectedToInternet()+"\nQueue.isEmpty= "+downloadQueue.isEmpty());
        boolean isBlocked = prefs.getBoolean(SHARED_PREFS_ISBLOCKED_KEY,false);
        if(!isBlocked) {
            if (isConnectedToInternet() && (!downloadQueue.isEmpty())) {
                Log.i(LOG_TAG, "service(onStartCommand): dl.startDownloads() chamado");
                dl.startDownloads();
            } else {
                //TODO \/ ver comentário abaixo

                stopSelf(); //parar o service... será mesmo?

                Log.i(LOG_TAG, "tá sem net, doido!");
            }
        }
        else
        {
            Log.d(LOG_TAG,">>>>>>>> isBlocked == true <<<<<<<<<<<");
            stopSelf();
        }




		return Service.START_STICKY; //service se restarta assim que possível se for finalizado pelo sistema
	}

	private void enqueueDownload(Bundle pack) {
		
		String URL = pack.getString(DOWNLOADSERVICE_URL);
		String id_arquivo = pack.getString(DOWNLOADSERVICE_ID);
		int prioridade = pack.getInt(DOWNLOADSERVICE_PRIORIDADE, Integer.MAX_VALUE);
		String path = pack.getString(DOWNLOADSERVICE_PATH);
		String md5 = pack.getString(DOWNLOADSERVICE_MD5);

		repositorio.insert(id_arquivo,prioridade,path,URL,md5,0);
		if(prioridade == Integer.MAX_VALUE)
		{
			dl.enqueueDownload(URL,id_arquivo,path,md5);
		}
		else
		{
			dl.enqueueDownload(URL, prioridade, id_arquivo, path, md5);
		}
		Log.i(LOG_TAG, "ServiceManagerReceiver: Download Enfileirado\nQueue Size: "+ dl.queue.getQueueSize());
	}	
	
	@Override
	public void onDestroy() {
		super.onDestroy();
		Log .i("DEBUG", "Service onDestroy");
		if(!downloadQueue.isEmpty() || dl.bg_thread!=null) //se a fila tiver algo ou a thread de background tiver rodando
		{
			retainDownloadTimes();

            if(!prefs.getBoolean(SHARED_PREFS_ISBLOCKED_KEY,false)) {
                Context context = getApplicationContext();
                Intent it = new Intent(context, DownloadService.class);
                Log.i("DEBUG", "Service ALARM ON");
                AlarmManager alarmMgr = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
                PendingIntent alarmIntent = PendingIntent.getService(context, 0, it, PendingIntent.FLAG_CANCEL_CURRENT);
                alarmMgr.set(AlarmManager.ELAPSED_REALTIME_WAKEUP, SystemClock.elapsedRealtime() + 10 * 1000, alarmIntent);
            }
			
		}

		repositorio.fechar();
        Log.i(LOG_TAG,"Faliceu");
		
	}

	private void retainDownloadTimes() {
		if(dl.bg_thread!=null) {
			dl.stopTimer();
			Downloadable d = dl.currentDownload;
			repositorio.updateTempo(d.tempo,d.id_arquivo);
		}

		if(downloadQueue!=null)
		{
			Downloadable element;

			while(!downloadQueue.isEmpty())
			{
				element = downloadQueue.poll();

				if(element!=null && element.tempo!=0) //dumb check, verificar um política melhor pra "economizar" operações em banco
				{
					repositorio.updateTempo(element.tempo,element.id_arquivo);
				}

			}
		}



	}


	public void cancelDownload(String id)
	{
        Log.i(LOG_TAG,"CANCEL DOWNLOAD - dl ="+dl);
		if(dl!=null)
		{
			dl.cancel(id);
		}
	}
	
	@Override
	public IBinder onBind(Intent intent) {
		return null;
	}
	
//	public boolean onUnbind(Intent intent) {
//		LocalBroadcastManager.getInstance(this).unregisterReceiver(progressReceiver);
//		
//		return super.onUnbind(intent);
//	};

//	private BroadcastReceiver progressReceiver = new BroadcastReceiver() {
//		  @Override
//		  public void onReceive(Context context, Intent intent) {
//			  //recebe o broadcast de progresso aqui
//		  }		
//		};
		
		

	private void preparaFila() {
		RepositorioScripts repScripts = new RepositorioScripts(getApplicationContext());
		repScripts.fechar();

		
		Log.i(LOG_TAG, "Service: preparaFila()");
		Cursor c = repositorio.query("select * from tb_downloads");
		if(c!=null)
		{
			if(c.moveToFirst()) //true se o cursor não estiver vazio
			{
				String[] URLs = new String[c.getCount()];
				int[] prioridades = new int[c.getCount()];
				String[] ids_arquivos =  new String[c.getCount()];
				String[] paths =  new String[c.getCount()];
				String[] md5s = new String[c.getCount()];
 				
				for(int i=0; i<c.getCount();i++)
				{
					URLs[i] = c.getString(c.getColumnIndex("URL"));
					prioridades[i] = c.getInt(c.getColumnIndex("prioridade"));
					ids_arquivos[i] = c.getString(c.getColumnIndex("id_arquivo"));
					paths[i] = c.getString(c.getColumnIndex("destino"));
					md5s[i] = c.getString(c.getColumnIndex("md5"));
				}
				downloadQueue = new FilaPrioritaria(URLs, prioridades,ids_arquivos,paths,md5s);										
			}
			else
			{
				downloadQueue = new FilaPrioritaria();	//não há downloads, preparar apenas a fila vazia
			}
			
		}
		
	}
	


	@SuppressWarnings("deprecation")
	public boolean isConnectedToInternet(){
		
		//TODO Verificar se o contexto está certo aqui mesmo
	    ConnectivityManager connectivity = (ConnectivityManager) getApplicationContext().getSystemService(Context.CONNECTIVITY_SERVICE); 
	      if (connectivity != null)
	      {
	          NetworkInfo[] info = connectivity.getAllNetworkInfo();
	          if (info != null)
				  for (NetworkInfo anInfo : info)
					  if (anInfo.getState() == NetworkInfo.State.CONNECTED) {
						  return true;
					  }
	
	      }
	      return false;
	}

    @Override
    public void onLackofStorage(long bytesNeeded) {
        prefs.edit().putBoolean(SHARED_PREFS_ISBLOCKED_KEY,true).apply();

        Intent it = new Intent(STORAGE_FULL_ACTION);
        it.putExtra(STORAGE_BYTES_NEEDED,bytesNeeded);

        sendBroadcast(it);

        stopSelf();

    }

//	public class ServiceManagerReceiver extends BroadcastReceiver {
//
//		@Override
//		public void onReceive(Context context, Intent intent) {
//			
//			String URL = intent.getStringExtra(DOWNLOADSERVICE_URL);
//			String id_arquivo = intent.getStringExtra(DOWNLOADSERVICE_ID);
//			int prioridade = intent.getIntExtra(DOWNLOADSERVICE_PRIORIDADE, Integer.MAX_VALUE);
//			String path = intent.getStringExtra(DOWNLOADSERVICE_PATH);
//			String md5 = intent.getStringExtra(DOWNLOADSERVICE_MD5);
//			repositorio.insert(id_arquivo,prioridade,path,URL,md5);		
//			dl.enqueueDownload(URL, prioridade, id_arquivo, path, md5);
//			Log.i(LOG_TAG, "ServiceManagerReceiver: Download Enfileirado\nQueue Size: "+ dl.queue.getQueueSize());
//		}
//		
//
//	}
//	
	
	

}
