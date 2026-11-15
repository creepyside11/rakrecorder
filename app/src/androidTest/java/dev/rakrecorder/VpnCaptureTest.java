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
    @Test public void forwardsTcpAndStoresCapture() throws Exception {
        Instrumentation inst=InstrumentationRegistry.getInstrumentation();Context ctx=inst.getTargetContext();
        Intent ui=new Intent(ctx,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        Activity activity=inst.startActivitySync(ui);
        shell("appops set dev.rakrecorder ACTIVATE_VPN allow");
        int before=new SessionStore(ctx).list().size();
        Intent start=new Intent(ctx,RecorderVpnService.class).setAction(RecorderVpnService.ACTION_START).putExtra("package","com.android.shell");ctx.startForegroundService(start);
        long deadline=System.currentTimeMillis()+15000;while(!RecorderVpnService.isRunning() && System.currentTimeMillis()<deadline)Thread.sleep(200);
        assertTrue("VPN service did not start",RecorderVpnService.isRunning());
        String response=shell("sh -c \"printf 'GET / HTTP/1.0\\r\\n\\r\\n' | nc -w 5 10.0.2.2 8765\"");
        ctx.startService(new Intent(ctx,RecorderVpnService.class).setAction(RecorderVpnService.ACTION_STOP));
        deadline=System.currentTimeMillis()+15000;while(RecorderVpnService.isRunning() && System.currentTimeMillis()<deadline)Thread.sleep(200);
        assertFalse("VPN service did not stop",RecorderVpnService.isRunning());
        assertTrue("HTTP response did not arrive: "+response,response.contains("HTTP/1."));
        java.util.List<JSONObject> sessions=new SessionStore(ctx).list();assertTrue("Session missing",sessions.size()>before);JSONObject session=sessions.get(0);assertEquals("live_vpn",session.getString("source"));assertTrue("No TCP packets recorded",session.getLong("tcp")>0);
        activity.finish();
    }
}
