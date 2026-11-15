package dev.rakrecorder;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.*;
import java.io.InputStream;
import java.time.Instant;
import java.util.concurrent.Executors;

public class MainActivity extends Activity {
    private TextView output; private Button open;
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        LinearLayout root = new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL); root.setPadding(36,60,36,30); root.setBackgroundColor(Color.rgb(20,13,36));
        TextView title=new TextView(this); title.setText("RakRecorder · альфа"); title.setTextColor(Color.WHITE); title.setTextSize(26); root.addView(title);
        TextView subtitle=new TextView(this); subtitle.setText("Локальный анализ PCAP\nVPN, запись маршрута и декодирование игры ещё не реализованы."); subtitle.setTextSize(16); subtitle.setTextColor(0xffd5c9e8); subtitle.setPadding(0,20,0,30); root.addView(subtitle);
        open=new Button(this); open.setText("Открыть PCAP"); root.addView(open); open.setOnClickListener(v->{Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT); i.addCategory(Intent.CATEGORY_OPENABLE); i.setType("*/*"); startActivityForResult(i,1);});
        output=new TextView(this); output.setTextColor(Color.WHITE); output.setTextSize(16); output.setPadding(0,30,0,0); output.setText("Выберите классический PCAP. Файл обрабатывается на устройстве."); ScrollView scroll=new ScrollView(this); scroll.addView(output); root.addView(scroll); setContentView(root);
    }
    @Override protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request,result,data); if(request!=1 || result!=RESULT_OK || data==null || data.getData()==null)return;
        open.setEnabled(false); output.setText("Анализ файла…");
        Executors.newSingleThreadExecutor().execute(()->{String text; try(InputStream in=getContentResolver().openInputStream(data.getData())) { if(in==null) throw new Exception("Не удалось открыть файл"); PcapReader.Result r=PcapReader.analyze(in); text="Пакетов: "+r.packets+"\nUDP: "+r.udp+"\nTCP: "+r.tcp+"\nДругие / нераспознанные: "+r.other+"\nUDP с портом 5125: "+r.udp5125+"\nUDP с портом 5127: "+r.udp5127+"\nПервый пакет: "+format(r.firstMicros)+"\nПоследний пакет: "+format(r.lastMicros)+"\n\nXYZ и игровые события: не определены. Порты сами по себе не доказывают принадлежность к игре."; } catch(Exception e) {text="Ошибка импорта: "+e.getMessage();} final String resultText=text; runOnUiThread(()->{output.setText(resultText);open.setEnabled(true);});});
    }
    private String format(long t) {return t<0 ? "нет" : Instant.ofEpochSecond(t/1000000,(t%1000000)*1000).toString();}
}
