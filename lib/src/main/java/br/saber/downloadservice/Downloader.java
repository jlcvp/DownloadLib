package br.saber.downloadservice;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.SocketTimeoutException;
import java.net.URL;
import java.security.MessageDigest;

import br.saber.downloadservice.database.RepositorioService;
import br.saber.downloadservice.models.Downloadable;
import br.saber.downloadservice.models.FilaPrioritaria;
import android.content.Context;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.Handler;
import android.os.StatFs;
import android.util.Log;

public class Downloader {

    public interface onLackOfStorageSpaceListener
    {
        void onLackofStorage(long bytesNeeded);
    }
    public static final String LOG_TAG = "DEBUG_SERVICE";

    private onLackOfStorageSpaceListener mListener;
    public static final String PROGRESS_ACTION = "br.saber.downloadservice.progress_action";

    //TODO ajustar
    public static final String CURRENT_URL = "br.saber.downloadservice.current_url";
    @Deprecated    public static final String CURRENT_ID = "br.saber.downloadservice.current_id";
    public static final String CURRENT_CODIGO = CURRENT_ID;
    public static final String CURRENT_PATH = "br.saber.downloadservice.current_path";
    public static final String CURRENT_PROGRESS = "br.saber.downloadservice.current_progress";
    public static final String CURRENT_TIME = "br.saber.downloadservice.current_time";

    public static final String DOWNLOAD_STARTED_ACTION = "br.saber.downloadservice.download_started_action";
    public static final String DOWNLOAD_TERMINATED_ACTION = "br.saber.downloadservice.download_terminated_action";
    public static final String DOWNLOAD_CANCELED_ACTION = "br.saber.downloadservice.download_canceled_action";

    public static final int BUFFER_SIZE = 50*1024;
    private static final int GORDURA_MAX = 50*1024*1024; //50 mega

    public static final String HAS_NEXT = "br.saber.downloadservice.has_next";


    public final int MAX_RETRIES = 4;
    public final int RETRY_WAIT_TIME = 5000;
    public long startTime;

    private static final int READ_TIMEOUT = 10 * 1000;
    private static final int CONNECT_TIMEOUT = 20 * 1000;

    private static final int ERROR_CONNECTION = -50;
    private static final int ERROR_SPACE = -51;
    private static final int ERROR_CHECKSUM = -52;
    private static final int NO_ERROR = -49;



    //DownloadService service;
    Context ctx;
    FilaPrioritaria queue;
    Downloadable currentDownload;
    DownloadTask bg_thread = null;
    Handler downloadHandler;
    int currentTries;

    public Downloader(Context appContext, FilaPrioritaria queue)
    {
        this.ctx = appContext;
        mListener = (onLackOfStorageSpaceListener)appContext; //DANGER DANGER
        this.queue = queue;
        downloadHandler = new Handler();

        currentDownload=null;
        bg_thread = null;




    }

    public void cancel(String id)
    {
        if(id!=null)
        {

            if(currentDownload!=null && !currentDownload.id_arquivo.equals(id)) //se download está enfileirado mas não é o atual
            {
                queue.remove(id);
                removeFromDB(id);
                downloadCancelado(id);
            }
            else
            {
                if(bg_thread!=null){ //evitando erros de corrida extremamente acirradas
                    bg_thread.wasCancelledByUser = true;
                    bg_thread.cancel(false);
                    Log.i("DEBUG","cancela ELSE");
                }
                else
                {

                    try{
                        if(queue!=null)
                        {
                            Log.d(LOG_TAG,"Wow, Queue = "+queue.toString()+"\nQueue size = "+queue.getQueueSize());
                        }
                        //TODO: COISAR AQUI PRA APAGAR O ARQUIVO SE EXISTIR
                        Downloadable d = queue.get(id);
                        if(d!=null)
                        {
                            boolean w = new File(d.path_destino).delete();

                            if(w)
                            {
                                Log.i(LOG_TAG, "arquivo apagado\n Path="+d.path_destino );
                            }
                            else
                            {
                                Log.i(LOG_TAG,"arquivo não existia, sem necessidade de apagar");
                            }

                        }
                        queue.remove(id);
                        removeFromDB(id);
                        downloadCancelado(id);
                    }
                    catch (Exception e)
                    {
                        Log.e(LOG_TAG,e.getMessage());
                    }
                }
            }
        }
    }

    private void downloadCancelado(String id) {
        Intent it = new Intent(DOWNLOAD_CANCELED_ACTION);
        it.putExtra(CURRENT_CODIGO, id);
        ctx.sendBroadcast(it);

    }

    public void startDownloads()
    {
        if(!queue.isEmpty() && bg_thread==null ) //há downloads enfileirados ainda, mas não baixando
        {

            currentDownload = queue.poll();
            currentTries++;
            //TESTE FALHA DOWNLOAD
            //currentDownload.md5 = "aaaaa11111";

            bg_thread=	new DownloadTask(false);
            bg_thread.execute(currentDownload.url, currentDownload.path_destino);//executando download na AsyncTask
            Log.v(LOG_TAG, "download iniciado -> "+currentDownload.id_arquivo);
        }
        else
        {
            Log.v(LOG_TAG, "download já iniciado ou fila vazia, no donut for you");
        }

    }

    public void getNextDownload() {
        bg_thread=null;
        removeCurrentFromDB();
        currentDownload=null;
        startDownloads();
    }

    public void downloadTerminado()
    {

        bg_thread=null;

        //verificaçlão do Checksum MD5
        try {
            if (isFileMD5Correct()) {
                Log.v("CHECKSUM", "VERIFICAÇÂO OK!\nmd5 CORRETO: " + currentDownload.md5
                        + "\nmd5 FILE MD5: "
                        + getFileMD5(currentDownload.path_destino));
                broadcastDownloadTerminado();
                //removerRegistro do Bd
                removeCurrentFromDB();


                currentDownload = null;
                currentTries=0;
            }
            else{

                Log.e("CHECKSUM", "VERIFICAÇÂO FALHOU!\nmd5 CORRETO: " + currentDownload.md5
                        + "\nmd5 FILE MD5: "
                        + getFileMD5(currentDownload.path_destino)+"\nBaixando novamente");
                retryDownload(ERROR_CHECKSUM,-1);
            }
        } catch (IOException e) {

            e.printStackTrace();
        }

        startDownloads();


    }

    public void downloadIniciado()
    {
        Intent it = new Intent(DOWNLOAD_STARTED_ACTION);
        it.putExtra(CURRENT_URL, currentDownload.url);
        it.putExtra(CURRENT_CODIGO, currentDownload.id_arquivo);
        it.putExtra(CURRENT_PATH, currentDownload.path_destino);

        ctx.sendBroadcast(it);
    }


    /**
     * verifica se o arquivo baixado tem um MD5 compatível com o esperado.
     * @return true: se os MD5 estiverem corretos ou false: os MD5 não são iguais
     *
     */
    private boolean isFileMD5Correct(){
        boolean ret = false;
        if(currentDownload.md5 !=null) {
            try {
                if (currentDownload.md5.equals(getFileMD5(currentDownload.path_destino)))
                    ret = true;
            } catch (IOException e) {
                Log.e("DEBUG", "IOException no MD5");
            } catch (NullPointerException e) {
                Log.e("DEBUG", "Null pointer exception, dados a partir desse ponto não são confiáveis");
            }

        }
        else{
            ret = true; //skip md5verification
        }

        return ret;
    }


    private char[] hexDigits = "0123456789abcdef".toCharArray();//essa linha é usada no metodo abaixo MD5// não remover
    private String getFileMD5(String filePath) throws IOException {
        String md5 = "";

        //verificaçlão do Checksum MD5
        InputStream is = null;
        try {
            is = new FileInputStream(filePath);

            byte[] bytes = new byte[4096];
            int read;
            MessageDigest digest = MessageDigest.getInstance("MD5");

            while ((read = is.read(bytes)) != -1) {
                digest.update(bytes, 0, read);
            }

            byte[] messageDigest = digest.digest();

            StringBuilder sb = new StringBuilder(32);

            for (byte b : messageDigest) {
                sb.append(hexDigits[(b >> 4) & 0x0f]);
                sb.append(hexDigits[b & 0x0f]);
            }

            md5 = sb.toString();
        } catch (Exception ignored) {

        }finally{
            if (is != null) {
                is.close();
            }
        }


        return md5;
    }


    private void removeCurrentFromDB() {
        removeFromDB(currentDownload.id_arquivo);

    }

    private void removeFromDB(String id)
    {
        RepositorioService bd = new RepositorioService(ctx);
        bd.delete("id_arquivo = '"+id+"'");
        Log.i(LOG_TAG, "entrada do bd removida -- ID=" + id);
        bd.fechar();
    }


    private void broadcastDownloadTerminado() {
        Intent it = new Intent(DOWNLOAD_TERMINATED_ACTION);
        it.putExtra(CURRENT_URL, currentDownload.url);
        it.putExtra(CURRENT_CODIGO, currentDownload.id_arquivo);
        it.putExtra(CURRENT_PATH, currentDownload.path_destino);
        it.putExtra(HAS_NEXT, queue.isEmpty());
        it.putExtra(CURRENT_TIME, currentDownload.tempo);

        ctx.sendBroadcast(it);

    }

    /**
     * Enfileira uma url para download.
     * @param URL Url para o arquivo
     * @param id_arquivo Identificador interno do arquivo no sistema
     * @param destino Pasta de destino do arquivo
     */
    public void enqueueDownload(String URL, String id_arquivo, String destino, String md5)
    {
        enqueueDownload(URL, Integer.MAX_VALUE, id_arquivo, destino, md5); //prioridade minima (padrão)
    }

    /**
     * Enfileira uma url para download. Qualquer prioridade especificada será automaticamente maior que a fila sem prioridade.
     * @param URL url para o arquivo
     * @param id_arquivo identificador interno do arquivo
     * @param destino Pasta de destino do arquivo
     */
    public void enqueueDownload(String URL,int prioridade, String id_arquivo, String destino, String md5)
    {
        queue.enqueue(URL, prioridade, id_arquivo, destino, md5);
        //		if(bg_thread ==null)
        //		{
        //			startDownloads();
        //		}
        //
    }


    public void retryDownload(int ERROR_CODE, long complemento) {

        switch (ERROR_CODE) //TODO: tratar melhor as tentativas, risco de loop infinito para arquivo corrompido no server de onivaldo
        {
            case ERROR_CHECKSUM:
                currentTries++;
                if(currentTries<=MAX_RETRIES) {
                    downloadHandler.postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            bg_thread = new DownloadTask(true);
                            bg_thread.execute(currentDownload.url, currentDownload.path_destino);
                        }
                    }, RETRY_WAIT_TIME);
                }
                else
                {
                    queue.enqueue(currentDownload); //retornando o download atual para o fim da fila
                    bg_thread = null;
                    startDownloads();
                }
                break;
            case ERROR_CONNECTION:
                Log.i("DEBUG","PARANDO O SERVICE, FALHA DE CONEXAO");
                ctx.stopService(new Intent(ctx,DownloadService.class));
                break;
            case ERROR_SPACE:
                Log.i("DEBUG","PARANDO O SERVICE, FALTA DE ESPAÇO");
                mListener.onLackofStorage(complemento);
                break;

            default:

        }



    }

    public void stopTimer() {
        long interval = (System.currentTimeMillis() - startTime);

        if(startTime>0 && interval>0)
        {
            currentDownload.tempo += interval;
        }
    }

    public long bytesAvailable() {
        File f = new File(currentDownload.path_destino);
        StatFs stat = new StatFs(ctx.getExternalFilesDir(null).getPath());
        long bytesAvailable =(long) stat.getBlockSize() * (long)stat.getAvailableBlocks();
        Log.i(LOG_TAG,"bytes available = "+bytesAvailable);
        return bytesAvailable;
    }

    class DownloadTask extends AsyncTask<String, Integer, String> {
        int progress;
        boolean overwriteExisting;
        public boolean wasCancelledByUser = false;
        public long filesize=-1;

        public DownloadTask(boolean overwriteExisting)
        {
            this.overwriteExisting = overwriteExisting;

        }

        @Override
        protected void onPreExecute() {

            downloadIniciado();
        }

        @Override
        protected String doInBackground(String... arg0) { //arg0[0] = URL, arg0[1] = path
            progress = 0;
            URL url = null;
            String path =arg0[1];


            String directoryTree = path.substring(0, path.lastIndexOf(File.separator));
            File parentDirectories = new File(directoryTree);
            if(parentDirectories.mkdirs())
            {
                Log.i(LOG_TAG,"Diretórios criados com sucesso para o arquivo");
            }
            else
            {
                Log.i(LOG_TAG,"Diretório já existente");
            }

            File fileThatExists = new File(path);
            if(!fileThatExists.exists())
            {
                try {
                    //noinspection ResultOfMethodCallIgnored
                    fileThatExists.createNewFile();
                } catch (IOException e) {
                    Log.e(LOG_TAG, "erro, acesso negado!");
                }
            }
            OutputStream output;
            long tamTotal;



            try {
                url = new URL(arg0[0]);

            } catch (MalformedURLException e) {

                Log.e("DEBUG_DOWNLOADSERVICE", "URL Malformada");
            }

            if(url!=null){
                HttpURLConnection connection;

                try {

                    connection = (HttpURLConnection) url.openConnection();			//Abrindo a conexão
                    connection.setReadTimeout(READ_TIMEOUT);
                    connection.setConnectTimeout(CONNECT_TIMEOUT);
                    Log.i("DownloadSErvice","connection timeout = "+connection.getConnectTimeout()+"\n readTimeout = "+connection.getReadTimeout());
                } catch (IOException e) {
                    //e.printStackTrace();
                    Log.e("DEBUG_DOWNLOADSERVICE","Erro abrindo conexão");
                    retryDownload(ERROR_CONNECTION,NO_ERROR);
                    cancel(true);
                    return null;
                }


                if(!serverAcceptsResuming(arg0[0])) //Servidor não suporta partial downloads
                {
                    try {
                        output = new FileOutputStream(path, false); //sobrescrevendo arquivo existente
                    } catch (FileNotFoundException e) {

                        cancel(true);
                        Log.e("DEBUG_DOWNLOADSERVICE", "arquivo não encontrado ou acesso negado");
                        return null;
                    }
                }
                else //servidor suporta downloads parciais
                {
                    try {
                        output = new FileOutputStream(path, !(overwriteExisting));
                        Log.w(LOG_TAG, "ARQUIVO EXISTENTE.LENGTH() ==== "+fileThatExists.length());
                        connection.setRequestProperty("Range", "bytes=" + fileThatExists.length() + "-");

                    } catch (FileNotFoundException e) {
                        Log.e("EXCEPTIONS", "arquivo não encontrado");
                        cancel(true);
                        return null;
                    }
                }


                try {
                    connection.connect(); //começando a brincadeira de baixar
                    startTime = System.currentTimeMillis();
                    Log.d("DEBUG", "ResponseCode="+connection.getResponseCode());
                } catch (IOException e) {


                    Log.e("EXCEPTIONS","Erro estabelecendo conexão");

                }


                try {
                    if(connection.getResponseCode()==416) //Requested Range Not Satisfiable. Arquivo já existe e já terminou o download
                    {
                        return null;
                    }
                    else
                    {
                        int lenghtOfFile = connection.getContentLength();
                        Log.i("DEBUG", "content length = " +lenghtOfFile);
                        tamTotal = fileThatExists.length() + lenghtOfFile;
                        filesize=tamTotal;
                        progress = (int) ( Math.floor(100*(double)fileThatExists.length()/tamTotal));
                        InputStream input;
                        try {
                            input = new BufferedInputStream(connection.getInputStream());
                        } catch (Exception e) {

                            Log.e("EXCEPTIONS","Error retrieving inputStream");
                            cancel(true);
                            return null;
                        }
                        byte data[] = new byte[BUFFER_SIZE];

                        long total = fileThatExists.length();

                        int count;
                        try {
                            int lastPublishedProgress = 0;
                            while ((count = input.read(data)) != -1 && !isCancelled() && !wasCancelledByUser) {
                                total += count;

                                //			    System.out.println("qtd bytes lidos = "+count+"\n\n");
                                progress = (int) ( Math.floor(100 *((double)total)/tamTotal));
                                //Log.i("DEBUG", "Baixado = "+total+"/"+tamTotal+"\nProgress = "+progress);
                                output.write(data, 0 , count);
                                if(progress!=lastPublishedProgress)
                                {
                                    publishProgress(progress);
                                    lastPublishedProgress=progress;
                                }
                            }

                        }catch (SocketTimeoutException e)
                        {
                            cancel(false);
                            Log.w("DOWNLOAD_SERVICE","SOCKET TIMEOUT");

                        }
                        catch (IOException e) {

                            Log.w("DOWNLOAD_SERVICE","IOException");
                            cancel(false);
                        }
                        finally
                        {
                            input.close();
                            output.close();
                            stopTimer();

                            if(wasCancelledByUser)
                            {
                                File file = new File(path);
                                if(file.delete())
                                {
                                    Log.i(LOG_TAG,"DownloadCancelado, arquivo deletado com sucesso");
                                }
                                else
                                {
                                    Log.w(LOG_TAG,"Não foi possível deletar o arquivo cancelado.");
                                }

                                Log.v(LOG_TAG,"ASYNCTASK Cancelada in the midium do download");
                            }
                        }


                    }
                } catch (IOException e) {

                    cancel(false);
                }
                try {
                    output.close();
                } catch (IOException e) {
                    Log.e("ERROR DOWNLOADLIB", "error closing output, possible resource leak. " +
                            "May the Garbage collector bless us");
                }
            }
            return null;
        }



        @Override
        protected void onCancelled(String result) {
            if(!wasCancelledByUser)
            {
                //TODO: Verificar se faltou espaço no device ou foi erro de rede.
                boolean hasSpace = verificaEspaco();
                if(hasSpace) {
                    retryDownload(ERROR_CONNECTION,-1);
                }
                else
                {
                    retryDownload(ERROR_SPACE,filesize);
                }
            }
            else
            {
                Log.d("DEBUG","Cancelada com sucesso");
                downloadCancelado(currentDownload.id_arquivo);
                getNextDownload();
            }
        }

        /**
         * Verifica se tem espaço suficiente para guardar o arquivo alvo
         * @return <b>true</b> se houver espaço sucifiente, <b>false</b> caso contrário;
         */
        private boolean verificaEspaco() {

            long freeSpace = bytesAvailable();
            long spaceNeeded = (filesize > GORDURA_MAX/2 ? filesize+GORDURA_MAX : 2*filesize);
            boolean ret;
            if(freeSpace >= spaceNeeded)
            {
                ret = true;
            }
            else
            {
                ret = false;
            }

            return ret;
        }




        @Override
        protected void onProgressUpdate(Integer... values) {
            //super.onProgressUpdate(values);
            Intent it = new Intent(PROGRESS_ACTION);
            it.putExtra(CURRENT_URL,currentDownload.url);
            it.putExtra(CURRENT_PROGRESS, values[0]);
            it.putExtra(CURRENT_CODIGO, currentDownload.id_arquivo);
            ctx.sendBroadcast(it);

        }

        @Override
        protected void onPostExecute(String result) {
            if(!isCancelled()){ //if desnecessário, mas vai que...
                downloadHandler.post(new Runnable() {

                    @Override
                    public void run() {
                        downloadTerminado();
                    }
                });
            }

        }

//		public boolean fileExistsOnServer(String URL)
//		{
//			try {
//				URL url = new URL(URL);
//				if(url!=null){
//
//					HttpURLConnection connection=null;
//					connection = (HttpURLConnection) url.openConnection();			//Abrindo a conexão
//
//
//					int responseCode=-1;
//
//					connection.connect();
//
//					responseCode =  connection.getResponseCode();
//					Log.i("DEBUG_DOWNLOADSERVICE", "response code = "+responseCode);
//					//Log.i("DEBUG_DOWNLOADSERVICE"+"serverAcceptsResuming", "responseCode==HttpURLConnection.HTTP_PARTIAL ? "+(responseCode==HttpURLConnection.HTTP_PARTIAL));
//					connection.disconnect();
//					connection = null;
//					return (responseCode==HttpURLConnection.HTTP_NOT_FOUND);
//
//
//				}
//			} catch (Exception e)
//			{
//				e.printStackTrace();
//			}
//
//			return false; //default
//		}

        public boolean serverAcceptsResuming(String URL)
        {
            try {
                URL url = new URL(URL);

                HttpURLConnection connection;
                connection = (HttpURLConnection) url.openConnection();			//Abrindo a conexão
                connection.setRequestProperty("Range", "bytes=1-");

                int responseCode;

                connection.connect();

                responseCode =  connection.getResponseCode();
                Log.i("DEBUG_DOWNLOADSERVICE", "response code = "+responseCode);
                Log.i("DEBUG_DOWNLOADSERVICE", "responseCode==HttpURLConnection.HTTP_PARTIAL ? "+(responseCode==HttpURLConnection.HTTP_PARTIAL));
                connection.disconnect();
                return (responseCode==HttpURLConnection.HTTP_PARTIAL);


            } catch (Exception ignored)
            {

            }

            return false; //default

        }

    }













}
