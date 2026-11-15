package dev.rakrecorder;

import android.content.Context;
import org.json.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;
import java.security.MessageDigest;
import java.util.zip.*;

/** App-private session files. No data leaves the device without the user's export action. */
final class SessionStore {
    private final File root;
    SessionStore(Context context) {root=new File(context.getFilesDir(),"sessions"); if (!root.exists() && !root.mkdirs()) throw new IllegalStateException("Не удалось создать папку сессий");}
    File file(String id) { if (!id.matches("[0-9a-f-]{36}")) throw new IllegalArgumentException("Некорректный ID"); return new File(root,id); }
    private static void copy(InputStream in,OutputStream out) throws IOException {byte[] b=new byte[65536]; int n; while((n=in.read(b))!=-1) {if(n>0)out.write(b,0,n);}}
    String importPcap(InputStream input) throws Exception { return importPcap(input,"pcap_import"); }
    String importPcap(InputStream input, String source) throws Exception {
        String id=UUID.randomUUID().toString(); File dir=file(id); if(!dir.mkdir()) throw new IOException("Не удалось создать сессию");
        try {
            File raw=new File(dir,"capture.pcap"); try(OutputStream out=new BufferedOutputStream(new FileOutputStream(raw))) {copy(input,out);}
            PcapReader.Result r; try(InputStream in=new FileInputStream(raw)) {r=PcapReader.analyze(in);}
            JSONObject s=new JSONObject(); s.put("schema_version","1.0.0");s.put("session_id",id);s.put("source",source);s.put("created_utc",Instant.now().toString());s.put("packets",r.packets);s.put("udp",r.udp);s.put("tcp",r.tcp);s.put("other",r.other);s.put("udp_5125",r.udp5125);s.put("udp_5127",r.udp5127);s.put("first_us",r.firstMicros);s.put("last_us",r.lastMicros);s.put("markers",new JSONArray()); write(id,s); return id;
        } catch(Exception e) {deleteTree(dir); throw e;}
    }
    JSONObject read(String id) throws Exception {try(InputStream in=new FileInputStream(new File(file(id),"session.json"))) {ByteArrayOutputStream out=new ByteArrayOutputStream();copy(in,out);return new JSONObject(out.toString("UTF-8"));}}
    void write(String id, JSONObject value) throws Exception {File dir=file(id), tmp=new File(dir,"session.json.tmp"), dest=new File(dir,"session.json"); try(FileOutputStream out=new FileOutputStream(tmp)){out.write(value.toString(2).getBytes(StandardCharsets.UTF_8));out.getFD().sync();} Files.move(tmp.toPath(),dest.toPath(),StandardCopyOption.REPLACE_EXISTING,StandardCopyOption.ATOMIC_MOVE);}
    List<JSONObject> list() {List<JSONObject> result=new ArrayList<>();File[] dirs=root.listFiles();if(dirs!=null)for(File dir:dirs)try{if(dir.isDirectory())result.add(read(dir.getName()));}catch(Exception ignored){} result.sort((a,b)->b.optString("created_utc").compareTo(a.optString("created_utc")));return result;}
    void addMarker(String id, String type, String note, long offset) throws Exception {if(offset<0)throw new IllegalArgumentException("Время не может быть отрицательным");JSONObject s=read(id), marker=new JSONObject();marker.put("id",UUID.randomUUID().toString());marker.put("type",type);marker.put("note",note);marker.put("time_ms",offset);marker.put("source","manual");marker.put("position",JSONObject.NULL);marker.put("position_status","unknown");s.getJSONArray("markers").put(marker);write(id,s);}
    void delete(String id) throws IOException {deleteTree(file(id));}
    private static void deleteTree(File f) throws IOException {if(f.isDirectory()){File[] children=f.listFiles();if(children!=null)for(File child:children)deleteTree(child);}if(f.exists()&&!f.delete())throw new IOException("Не удалось удалить "+f.getName());}
    private static byte[] json(JSONObject j) throws JSONException {return j.toString(2).getBytes(StandardCharsets.UTF_8);}
    private static void entry(ZipOutputStream z,String name,byte[] data,Map<String,String> sums) throws Exception {z.putNextEntry(new ZipEntry(name));z.write(data);z.closeEntry();sums.put(name,hex(MessageDigest.getInstance("SHA-256").digest(data)));}
    private static String hex(byte[] bytes){StringBuilder b=new StringBuilder();for(byte x:bytes)b.append(String.format(Locale.ROOT,"%02x",x&255));return b.toString();}
    void export(String id,boolean includeRaw,OutputStream target) throws Exception {
        JSONObject s=read(id);Map<String,String> sums=new LinkedHashMap<>();JSONObject manifest=new JSONObject();manifest.put("schema_version","1.0.0");manifest.put("app","RakRecorder Studio");manifest.put("session_id",id);manifest.put("source",s.optString("source","pcap_import"));manifest.put("created_utc",s.getString("created_utc"));manifest.put("raw_pcap_included",includeRaw);manifest.put("xyz_verified",false);
        JSONObject route=new JSONObject();route.put("schema_version","1.0.0");route.put("session_id",id);route.put("has_verified_coordinates",false);route.put("reason","game_position_not_decoded");route.put("segments",new JSONArray());route.put("manual_markers_without_xyz",s.getJSONArray("markers"));
        JSONObject diag=new JSONObject(s.toString());diag.remove("markers");diag.put("decoder_status","unsupported");
        JSONArray markers=s.getJSONArray("markers"), games=new JSONArray();JSONObject openGame=null;StringBuilder eventLines=new StringBuilder();
        for(int i=0;i<markers.length();i++) {JSONObject marker=markers.getJSONObject(i);eventLines.append(marker.toString()).append('\n');String type=marker.optString("type");
            if(type.equals("Начало мини-игры")) {if(openGame!=null)games.put(openGame);openGame=new JSONObject().put("id",UUID.randomUUID().toString()).put("start_ms",marker.getLong("time_ms")).put("end_ms",JSONObject.NULL).put("type","unknown").put("label_source","manual").put("result","unknown").put("position",JSONObject.NULL).put("position_status","unknown");}
            else if(type.equals("Конец мини-игры") && openGame!=null && marker.getLong("time_ms")>=openGame.getLong("start_ms")){openGame.put("end_ms",marker.getLong("time_ms"));games.put(openGame);openGame=null;}
        }if(openGame!=null)games.put(openGame);
        try(ZipOutputStream z=new ZipOutputStream(new BufferedOutputStream(target))) {
            entry(z,"manifest.json",json(manifest),sums);entry(z,"route/route.json",json(route),sums);entry(z,"events/session_timeline.json",json(new JSONObject().put("items",s.getJSONArray("markers"))),sums);entry(z,"diagnostics/capture_stats.json",json(diag),sums);entry(z,"events/events.jsonl",eventLines.toString().getBytes(StandardCharsets.UTF_8),sums);entry(z,"minigames/minigames.json",json(new JSONObject().put("schema_version","1.0.0").put("items",games)),sums);entry(z,"route/route.csv","time_ms,x,y,z,source,confidence\n".getBytes(StandardCharsets.UTF_8),sums);
            String summary="RakRecorder Studio — импорт PCAP\nПакетов: "+s.getLong("packets")+"\nUDP: "+s.getLong("udp")+"\nTCP: "+s.getLong("tcp")+"\nКоординаты и игровые события не декодированы. Ручные отметки не имеют XYZ.\n";entry(z,"summary.txt",summary.getBytes(StandardCharsets.UTF_8),sums);
            if(includeRaw) {MessageDigest md=MessageDigest.getInstance("SHA-256");z.putNextEntry(new ZipEntry("raw/capture.pcap"));try(InputStream in=new FileInputStream(new File(file(id),"capture.pcap"))){byte[] b=new byte[65536];int n;while((n=in.read(b))!=-1){if(n>0){z.write(b,0,n);md.update(b,0,n);}}}z.closeEntry();sums.put("raw/capture.pcap",hex(md.digest()));}
            StringBuilder checks=new StringBuilder();for(Map.Entry<String,String> item:sums.entrySet())checks.append(item.getValue()).append("  ").append(item.getKey()).append('\n');z.putNextEntry(new ZipEntry("diagnostics/checksums.sha256"));z.write(checks.toString().getBytes(StandardCharsets.UTF_8));z.closeEntry();
        }
    }
}
