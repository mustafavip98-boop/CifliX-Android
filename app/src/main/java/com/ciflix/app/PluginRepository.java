package com.ciflix.app;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

public final class PluginRepository {
    public static final class Plugin {
        public final String id, name, version, description, sourceUrl;
        public final boolean enabled;
        public String state = "بانتظار التحميل";
        Plugin(String id, String name, String version, String description, String sourceUrl, boolean enabled) {
            this.id=id; this.name=name; this.version=version; this.description=description;
            this.sourceUrl=sourceUrl; this.enabled=enabled;
        }
    }
    public interface Listener {
        void onComplete(List<Plugin> plugins, String error);
    }

    private final Context context;
    private final OkHttpClient client;
    private final Handler main = new Handler(Looper.getMainLooper());

    public PluginRepository(Context context) {
        this.context=context.getApplicationContext();
        this.client=new OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .callTimeout(45, TimeUnit.SECONDS)
            .build();
    }

    public void sync(String manifestUrl, Listener listener) {
        if (manifestUrl == null || !manifestUrl.startsWith("https://")) {
            listener.onComplete(new ArrayList<>(), "يجب استخدام رابط HTTPS آمن");
            return;
        }
        Request request=new Request.Builder().url(manifestUrl).header("Accept","application/json").build();
        client.newCall(request).enqueue(new Callback() {
            @Override public void onFailure(Call call, IOException e) {
                main.post(() -> listener.onComplete(new ArrayList<>(), "تعذر تحميل ملف manifest: "+e.getMessage()));
            }
            @Override public void onResponse(Call call, Response response) throws IOException {
                try (Response res=response) {
                    if (!res.isSuccessful()) throw new IOException("HTTP "+res.code());
                    String body=res.body()==null ? "" : res.body().string();
                    JSONObject root=new JSONObject(body);
                    JSONArray scrapers=root.optJSONArray("scrapers");
                    if (scrapers==null) throw new IOException("المستودع لا يحتوي على scrapers");
                    List<Plugin> plugins=new ArrayList<>();
                    URI base=URI.create(manifestUrl);
                    URI parent=base.resolve(".");
                    File dir=new File(context.getFilesDir(),"plugins");
                    if (!dir.exists() && !dir.mkdirs()) throw new IOException("تعذر إنشاء مجلد الإضافات");
                    for (int i=0;i<scrapers.length();i++) {
                        JSONObject item=scrapers.optJSONObject(i);
                        if (item==null) continue;
                        String id=item.optString("id","").trim();
                        String filename=item.optString("filename","").trim();
                        boolean enabled=item.optBoolean("enabled",true);
                        if (id.isEmpty() || filename.isEmpty() || !enabled) continue;
                        URI resolved=parent.resolve(filename).normalize();
                        if (!"https".equalsIgnoreCase(resolved.getScheme()) || resolved.getHost()==null)
                            throw new IOException("رابط إضافة غير آمن: "+filename);
                        Plugin plugin=new Plugin(id,item.optString("name",id),item.optString("version",""),
                            item.optString("description",""),resolved.toString(),true);
                        try {
                            Request jsRequest=new Request.Builder().url(plugin.sourceUrl).header("Accept","text/javascript, application/javascript, */*").build();
                            try (Response jsResponse=client.newCall(jsRequest).execute()) {
                                if (!jsResponse.isSuccessful() || jsResponse.body()==null)
                                    throw new IOException("HTTP "+jsResponse.code());
                                String source=jsResponse.body().string();
                                if (source.trim().isEmpty()) throw new IOException("ملف JavaScript فارغ");
                                File target=new File(dir,id+".js");
                                try (FileOutputStream out=new FileOutputStream(target,false)) {
                                    out.write(source.getBytes(StandardCharsets.UTF_8));
                                }
                                plugin.state="تم التنزيل";
                            }
                        } catch (Exception ex) {
                            plugin.state="فشل التنزيل: "+ex.getMessage();
                        }
                        plugins.add(plugin);
                    }
                    main.post(() -> listener.onComplete(plugins,null));
                } catch (Exception e) {
                    main.post(() -> listener.onComplete(new ArrayList<>(), "تعذر قراءة المستودع: "+e.getMessage()));
                }
            }
        });
    }
}
