package dev.rakrecorder;

import static org.junit.Assert.*;
import android.app.Activity;
import android.app.Instrumentation;
import android.content.Context;
import android.content.Intent;
import android.os.ParcelFileDescriptor;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.json.JSONObject;
import java.io.*;
import java.nio.charset.StandardCharsets;

@RunWith(AndroidJUnit4.class)
public class VpnCaptureTest {
    private String shell(String command) throws Exception {
        Instrumentation inst=InstrumentationRegistry.getInstrumentation();
        ParcelFileDescriptor p=inst.getUiAutomation().executeShellCommand(command);
        try(InputStream in=new ParcelFileDescriptor.AutoCloseInputStream(p);ByteArrayOutputStream out=new ByteArrayOutputStream()) {byte[] b=new byte[2048];int n;while((n=in.read(b))!=-1)out.write(b,0,n);return out.toString(StandardCharsets.UTF_8.name());}
    }
    @Test public void exportsManualEventsWithoutRawByDefault() throws Exception {
        Context ctx=InstrumentationRegistry.getInstrumentation().getTargetContext();SessionStore store=new SessionStore(ctx);
        java.nio.ByteBuffer b=java.nio.ByteBuffer.allocate(24+16+28).order(java.nio.ByteOrder.LITTLE_ENDIAN);b.putInt(0xa1b2c3d4).putShort((short)2).putShort((short)4).putInt(0).putInt(0).putInt(65535).putInt(101);b.putInt(1).putInt(0).putInt(28).putInt(28);byte[] p=new byte[28];p[0]=0x45;p[9]=17;b.put(p);
        String id=store.importPcap(new ByteArrayInputStream(b.array()));store.addMarker(id,"Начало мини-игры","",100);store.addMarker(id,"Конец мини-игры","",200);
        ByteArrayOutputStream bytes=new ByteArrayOutputStream();store.export(id,false,bytes);java.util.Map<String,byte[]> files=new java.util.HashMap<>();
        try(java.util.zip.ZipInputStream in=new java.util.zip.ZipInputStream(new ByteArrayInputStream(bytes.toByteArray()))){java.util.zip.ZipEntry e;while((e=in.getNextEntry())!=null){ByteArrayOutputStream out=new ByteArrayOutputStream();byte[] data=new byte[512];int n;while((n=in.read(data))!=-1)out.write(data,0,n);files.put(e.getName(),out.toByteArray());}}
        assertFalse(files.containsKey("raw/capture.pcap"));assertTrue(files.containsKey("diagnostics/checksums.sha256"));assertTrue(files.containsKey("events/events.jsonl"));JSONObject games=new JSONObject(new String(files.get("minigames/minigames.json"),StandardCharsets.UTF_8));assertEquals(1,games.getJSONArray("items").length());JSONObject game=games.getJSONArray("items").getJSONObject(0);assertEquals(100,game.getLong("start_ms"));assertEquals(200,game.getLong("end_ms"));assertTrue(game.isNull("position"));store.delete(id);
    }
    @Test public void forwardsTcpAndStoresCapture() throws Exception {
        Instrumentation inst=InstrumentationRegistry.getInstrumentation();Context ctx=inst.getTargetContext();
        Intent ui=new Intent(ctx,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        Activity activity=inst.startActivitySync(ui);
        Intent permission=android.net.VpnService.prepare(ctx);
        if(permission!=null){inst.runOnMainSync(()->activity.startActivityForResult(permission,50));androidx.test.uiautomator.UiDevice device=androidx.test.uiautomator.UiDevice.getInstance(inst);androidx.test.uiautomator.UiObject2 ok=device.wait(androidx.test.uiautomator.Until.findObject(androidx.test.uiautomator.By.res("android","button1")),10000);assertNotNull("VPN consent button missing",ok);ok.click();Thread.sleep(1000);}
        assertNull("VPN permission not granted",android.net.VpnService.prepare(ctx));
        String command="sh /data/local/tmp/rak-http.sh";
        String baseline=shell(command);assertTrue("Baseline HTTP failed: "+baseline,baseline.contains("HTTP/1."));
        String udpCommand="sh /data/local/tmp/rak-udp.sh";String udpBaseline=shell(udpCommand);assertTrue("Baseline UDP failed: "+udpBaseline,udpBaseline.contains("rakrecorder-echo:rakrecorder-test"));
        int before=new SessionStore(ctx).list().size();
        Intent start=new Intent(ctx,RecorderVpnService.class).setAction(RecorderVpnService.ACTION_START).putExtra("package","com.android.shell");ctx.startForegroundService(start);
        long deadline=System.currentTimeMillis()+15000;while(!RecorderVpnService.isRunning() && System.currentTimeMillis()<deadline)Thread.sleep(200);
        assertTrue("VPN service did not start",RecorderVpnService.isRunning());
        String response=shell(command);String udpResponse=shell(udpCommand);
        ctx.startService(new Intent(ctx,RecorderVpnService.class).setAction(RecorderVpnService.ACTION_STOP));
        deadline=System.currentTimeMillis()+15000;while(RecorderVpnService.isRunning() && System.currentTimeMillis()<deadline)Thread.sleep(200);
        assertFalse("VPN service did not stop",RecorderVpnService.isRunning());
        java.util.List<JSONObject> sessions=new SessionStore(ctx).list();assertTrue("Session missing",sessions.size()>before);JSONObject session=sessions.get(0);assertEquals("live_vpn",session.getString("source"));assertTrue("No TCP packets recorded; response: "+response,session.getLong("tcp")>0);assertTrue("HTTP response did not arrive: "+response,response.contains("HTTP/1."));assertTrue("UDP response did not arrive: "+udpResponse,udpResponse.contains("rakrecorder-echo:rakrecorder-test"));assertTrue("No UDP packets recorded",session.getLong("udp")>0);
        activity.finish();
    }
}
