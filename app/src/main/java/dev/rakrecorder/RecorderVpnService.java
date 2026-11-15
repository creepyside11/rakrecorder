package dev.rakrecorder;

import android.app.*;
import android.content.*;
import android.content.pm.ServiceInfo;
import android.net.VpnService;
import android.os.*;
import java.io.*;
import org.json.*;
import java.nio.charset.StandardCharsets;

/** Experimental local VPN; user must opt in and choose an installed application. */
public final class RecorderVpnService extends VpnService {
    public static final String ACTION_START="dev.rakrecorder.START", ACTION_STOP="dev.rakrecorder.STOP", ACTION_OVERLAY="dev.rakrecorder.OVERLAY";
    private static volatile boolean active;
    public static boolean isRunning(){return active;}
    private volatile boolean stopping; private ParcelFileDescriptor tun; private Thread worker; private CaptureOverlay overlay; private File markerFile;
    static {System.loadLibrary("rakcapture");}
    private native int runTunnel(int fd,String path);
    public boolean isStopping(){return stopping;}
    @Override public void onCreate(){super.onCreate();NotificationChannel c=new NotificationChannel("capture","Захват трафика",NotificationManager.IMPORTANCE_LOW);getSystemService(NotificationManager.class).createNotificationChannel(c);}
    @Override public int onStartCommand(Intent intent,int flags,int startId){
        if(intent==null)return START_NOT_STICKY;
        if(ACTION_STOP.equals(intent.getAction())){stopping=true;if(overlay!=null){overlay.close();overlay=null;}if(worker==null)stopSelf();return START_NOT_STICKY;}
        if(ACTION_OVERLAY.equals(intent.getAction())){showOverlay();return START_NOT_STICKY;}
        if(!ACTION_START.equals(intent.getAction()) || worker!=null)return START_NOT_STICKY;
        String pkg=intent.getStringExtra("package");
        Intent stop=new Intent(this,RecorderVpnService.class).setAction(ACTION_STOP);
        PendingIntent pending=PendingIntent.getService(this,1,stop,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        Notification n=new Notification.Builder(this,"capture").setSmallIcon(android.R.drawable.presence_online).setContentTitle("RakRecorder записывает трафик").setContentText("Локальный VPN · "+pkg).setOngoing(true).addAction(new Notification.Action.Builder(null,"Остановить",pending).build()).build();
        if(Build.VERSION.SDK_INT>=34) startForeground(21,n,ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);else startForeground(21,n);
        try {
            if(pkg==null || pkg.isEmpty() || pkg.equals(getPackageName()))throw new IllegalArgumentException("Выберите установленную игру");
            Builder b=new Builder().setSession("RakRecorder").setMtu(1500).addAddress("10.20.30.1",32).addRoute("0.0.0.0",0).addAddress("fd00:2020:3030::1",128).addRoute("::",0).addDnsServer("1.1.1.1").addAllowedApplication(pkg);
            tun=b.establish();if(tun==null)throw new IOException("Android не создал VPN");active=true;
            File raw=File.createTempFile("unfinished-capture-", ".pcap",getFilesDir());markerFile=new File(raw.getAbsolutePath()+".markers");
            worker=new Thread(()->{int status=runTunnel(tun.getFd(),raw.getAbsolutePath());try{tun.close();}catch(IOException ignored){}tun=null;
                if(status==0){try(InputStream in=new FileInputStream(raw)){SessionStore store=new SessionStore(this);String id=store.importPcap(in,"live_vpn");importMarkers(store,id,markerFile);}catch(Exception e){status=-20;}}
                if(status==0){raw.delete();markerFile.delete();}active=false;Intent result=new Intent("dev.rakrecorder.CAPTURE_FINISHED").setPackage(getPackageName()).putExtra("status",status);sendBroadcast(result);stopSelf();},"rak-vpn");worker.start();
        }catch(Exception e){active=false;if(tun!=null){try{tun.close();}catch(IOException ignored){}tun=null;}sendBroadcast(new Intent("dev.rakrecorder.CAPTURE_FINISHED").setPackage(getPackageName()).putExtra("status",-21).putExtra("error",e.getMessage()));stopSelf();}
        return START_NOT_STICKY;
    }
    private void showOverlay(){if(!active || stopping || overlay!=null)return;if(!android.provider.Settings.canDrawOverlays(this))return;try{overlay=new CaptureOverlay(this,new CaptureOverlay.Actions(){public void mark(String type){appendMarker(type);}public void stop(){stopping=true;if(overlay!=null){overlay.close();overlay=null;}}});}catch(RuntimeException e){android.util.Log.e("RakRecorder","Overlay unavailable",e);}}
    private synchronized void appendMarker(String type){if(stopping || markerFile==null)return;try(FileOutputStream out=new FileOutputStream(markerFile,true)){JSONObject event=new JSONObject().put("type",type).put("epoch_ms",System.currentTimeMillis());out.write((event.toString()+"\n").getBytes(StandardCharsets.UTF_8));out.getFD().sync();android.widget.Toast.makeText(this,"Отметка добавлена",android.widget.Toast.LENGTH_SHORT).show();}catch(Exception e){android.widget.Toast.makeText(this,"Не удалось сохранить отметку",android.widget.Toast.LENGTH_LONG).show();}}
    static void importMarkers(SessionStore store,String id,File path) throws Exception {if(!path.exists())return;long first=store.read(id).getLong("first_us")/1000;try(BufferedReader r=new BufferedReader(new InputStreamReader(new FileInputStream(path),StandardCharsets.UTF_8))){String line;while((line=r.readLine())!=null){JSONObject m=new JSONObject(line);store.addMarker(id,m.getString("type"),"",Math.max(0,m.getLong("epoch_ms")-first));}}}
    @Override public void onRevoke(){stopping=true;super.onRevoke();}
    @Override public void onDestroy(){stopping=true;if(overlay!=null){overlay.close();overlay=null;}super.onDestroy();}
}
