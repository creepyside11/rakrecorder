package dev.rakrecorder;

import android.content.Context;
import android.graphics.PixelFormat;
import android.graphics.Color;
import android.view.*;
import android.widget.*;

/** Explicitly enabled overlay; tapping an action writes a time-stamped manual marker. */
final class CaptureOverlay {
    interface Actions {void mark(String type);void stop();}
    private final WindowManager manager; private final LinearLayout panel;
    CaptureOverlay(Context context,Actions actions) {
        manager=(WindowManager)context.getSystemService(Context.WINDOW_SERVICE);panel=new LinearLayout(context);panel.setOrientation(LinearLayout.VERTICAL);panel.setBackgroundColor(0xdd2d1846);panel.setPadding(8,8,8,8);
        TextView title=new TextView(context);title.setText("RakRecorder · вручную");title.setTextColor(Color.WHITE);panel.addView(title);
        for(String type:new String[]{"Точка","Начало мини-игры","Конец мини-игры","Событие"}){Button b=new Button(context);b.setText(type);b.setAllCaps(false);panel.addView(b);b.setOnClickListener(v->actions.mark(type));}
        Button stop=new Button(context);stop.setText("Остановить");panel.addView(stop);stop.setOnClickListener(v->actions.stop());
        WindowManager.LayoutParams p=new WindowManager.LayoutParams(WindowManager.LayoutParams.WRAP_CONTENT,WindowManager.LayoutParams.WRAP_CONTENT,WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE|WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,PixelFormat.TRANSLUCENT);p.gravity=Gravity.TOP|Gravity.END;p.y=70;manager.addView(panel,p);
    }
    void close(){try{manager.removeView(panel);}catch(IllegalArgumentException ignored){}}
}
