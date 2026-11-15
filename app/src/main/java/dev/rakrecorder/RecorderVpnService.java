package dev.rakrecorder;

import android.app.*;
import android.content.*;
import android.content.pm.ServiceInfo;
import android.net.VpnService;
import android.os.*;
import java.io.*;

/** Experimental local VPN; user must opt in and choose an installed application. */
public final class RecorderVpnService extends VpnService {
    public static final String ACTION_START="dev.rakrecorder.START", ACTION_STOP="dev.rakrecorder.STOP";
    private volatile boolean stopping; private ParcelFileDescriptor tun; private Thread worker;
    static {System.loadLibrary("rakcapture");}
    private native int runTunnel(int fd,String path);
    public boolean isStopping(){return stopping;}
    @Override public void onCreate(){super.onCreate();NotificationChannel c=new NotificationChannel("capture","Захват трафика",NotificationManager.IMPORTANCE_LOW);getSystemService(NotificationManager.class).createNotificationChannel(c);}
    @Override public int onStartCommand(Intent intent,int flags,int startId){
        if(intent==null)return START_NOT_STICKY;
        if(ACTION_STOP.equals(intent.getAction())){stopping=true;return START_NOT_STICKY;}
        if(!ACTION_START.equals(intent.getAction()) || worker!=null)return START_NOT_STICKY;
        String pkg=intent.getStringExtra("package");
        Intent stop=new Intent(this,RecorderVpnService.class).setAction(ACTION_STOP);
        PendingIntent pending=PendingIntent.getService(this,1,stop,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        Notification n=new Notification.Builder(this,"capture").setSmallIcon(android.R.drawable.presence_online).setContentTitle("RakRecorder записывает трафик").setContentText("Локальный VPN · "+pkg).setOngoing(true).addAction(new Notification.Action.Builder(null,"Остановить",pending).build()).build();
        if(Build.VERSION.SDK_INT>=34) startForeground(21,n,ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);else startForeground(21,n);
        try {
            if(pkg==null || pkg.isEmpty() || pkg.equals(getPackageName()))throw new IllegalArgumentException("Выберите установленную игру");
            Builder b=new Builder().setSession("RakRecorder").setMtu(1500).addAddress("10.20.30.1",32).addRoute("0.0.0.0",0).addAddress("fd00:2020:3030::1",128).addRoute("::",0).addDnsServer("1.1.1.1").addAllowedApplication(pkg);
            tun=b.establish();if(tun==null)throw new IOException("Android не создал VPN");
            File raw=File.createTempFile("unfinished-capture-", ".pcap",getFilesDir());
            worker=new Thread(()->{int status=runTunnel(tun.getFd(),raw.getAbsolutePath());try{tun.close();}catch(IOException ignored){}tun=null;
                if(status==0){try(InputStream in=new FileInputStream(raw)){new SessionStore(this).importPcap(in);}catch(Exception e){status=-20;}}
                if(status==0)raw.delete();Intent result=new Intent("dev.rakrecorder.CAPTURE_FINISHED").setPackage(getPackageName()).putExtra("status",status);sendBroadcast(result);stopSelf();},"rak-vpn");worker.start();
        }catch(Exception e){sendBroadcast(new Intent("dev.rakrecorder.CAPTURE_FINISHED").setPackage(getPackageName()).putExtra("status",-21).putExtra("error",e.getMessage()));stopSelf();}
        return START_NOT_STICKY;
    }
    @Override public void onRevoke(){stopping=true;super.onRevoke();}
    @Override public void onDestroy(){stopping=true;super.onDestroy();}
}
