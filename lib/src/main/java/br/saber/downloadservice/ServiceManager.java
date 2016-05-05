package br.saber.downloadservice;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;


public class ServiceManager extends BroadcastReceiver {

	@Override
	public void onReceive(Context context, Intent intent) { 
		
	
		if(intent.getAction().equals(DownloadService.DOWNLOADSERVICE_MANAGER))
		{
			Intent downloadIntent = new Intent(context, DownloadService.class);
			
			Bundle pack = new Bundle();
			
			Log.i("ServiceStarter","start service");
			
			pack.putString(DownloadService.DOWNLOADSERVICE_URL,intent.getStringExtra(DownloadService.DOWNLOADSERVICE_URL));
			pack.putString(DownloadService.DOWNLOADSERVICE_ID, intent.getStringExtra(DownloadService.DOWNLOADSERVICE_ID));
			pack.putInt(DownloadService.DOWNLOADSERVICE_PRIORIDADE, intent.getIntExtra(DownloadService.DOWNLOADSERVICE_PRIORIDADE, Integer.MAX_VALUE));
			pack.putString(DownloadService.DOWNLOADSERVICE_PATH, intent.getStringExtra(DownloadService.DOWNLOADSERVICE_PATH));
			pack.putString(DownloadService.DOWNLOADSERVICE_MD5, intent.getStringExtra(DownloadService.DOWNLOADSERVICE_MD5));
			pack.putBoolean(DownloadService.FLAG_ENQUEUE_DOWNLOAD, true);
			
			downloadIntent.putExtras(pack);
			context.startService(downloadIntent);			
			
			Log.i("DEBUG","broadcastDownload");
			
			
		}
		else if(intent.getAction().equals(DownloadService.ACTION_CANCEL))
		{
			String cancel_id = intent.getStringExtra(DownloadService.DOWNLOADSERVICE_ID);
			Intent cancelIntent = new Intent(context, DownloadService.class);
			Bundle pack = new Bundle();	
			
			pack.putString(DownloadService.DOWNLOADSERVICE_ID, cancel_id);
			pack.putBoolean(DownloadService.FLAG_CANCEL_DOWNLOAD, true);
			
			cancelIntent.putExtras(pack);	
			Log.i("DEBUG","CancelDownload: " + cancel_id);
			context.startService(cancelIntent);
			
		}
        else if (intent.getAction().equals(DownloadService.ACTION_START_AND_UNBLOCK))
        {
            Intent unblockIntent = new Intent(context,DownloadService.class);
            Bundle pack = new Bundle();
            pack.putBoolean(DownloadService.FLAG_UNBLOCK_DOWNLOADS, true);
            unblockIntent.putExtras(pack);

            context.startService(unblockIntent);
        }
		else //chamada via broadcast de boot
		{	
			Intent it = new Intent(context, DownloadService.class);
			context.startService(it);
		}

		
	}

}
