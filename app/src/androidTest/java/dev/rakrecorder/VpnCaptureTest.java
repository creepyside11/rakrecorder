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
        Intent permission=android.net.VpnService.prepare(ctx);
        if(permission!=null){inst.runOnMainSync(()->activity.startActivityForResult(permission,50));androidx.test.uiautomator.UiDevice device=androidx.test.uiautomator.UiDevice.getInstance(inst);androidx.test.uiautomator.UiObject2 ok=device.wait(androidx.test.uiautomator.Until.findObject(androidx.test.uiautomator.By.res("android","button1")),10000);assertNotNull("VPN consent button missing",ok);ok.click();Thread.sleep(1000);}
        assertNull("VPN permission not granted",android.net.VpnService.prepare(ctx));
        String command="sh -c \"{ printf 'GET / HTTP/1.0\\r\\n\\r\\n'; sleep 2; } | nc -w 5 10.0.2.2 8765 2>&1\"";
        String baseline=shell(command);assertTrue("Baseline HTTP failed: "+baseline,baseline.contains("HTTP/1."));
        int before=new SessionStore(ctx).list().size();
        Intent start=new Intent(ctx,RecorderVpnService.class).setAction(RecorderVpnService.ACTION_START).putExtra("package","com.android.shell");ctx.startForegroundService(start);
        long deadline=System.currentTimeMillis()+15000;while(!RecorderVpnService.isRunning() && System.currentTimeMillis()<deadline)Thread.sleep(200);
        assertTrue("VPN service did not start",RecorderVpnService.isRunning());
        String response=shell(command);
        ctx.startService(new Intent(ctx,RecorderVpnService.class).setAction(RecorderVpnService.ACTION_STOP));
        deadline=System.currentTimeMillis()+15000;while(RecorderVpnService.isRunning() && System.currentTimeMillis()<deadline)Thread.sleep(200);
        assertFalse("VPN service did not stop",RecorderVpnService.isRunning());
        java.util.List<JSONObject> sessions=new SessionStore(ctx).list();assertTrue("Session missing",sessions.size()>before);JSONObject session=sessions.get(0);assertEquals("live_vpn",session.getString("source"));assertTrue("No TCP packets recorded; response: "+response,session.getLong("tcp")>0);assertTrue("HTTP response did not arrive: "+response,response.contains("HTTP/1."));
        activity.finish();
    }
}
