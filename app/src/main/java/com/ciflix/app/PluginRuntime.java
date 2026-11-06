package com.ciflix.app;

import android.content.Context;
import android.util.Base64;
import android.webkit.JavascriptInterface;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.os.Handler;
import android.os.Looper;

import okhttp3.*;
import org.json.*;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.TimeUnit;

public final class PluginRuntime {
    public interface Listener { void onComplete(JSONArray streams, String report); }
    private final Context context;
    private final WebView web;
    private final OkHttpClient http;
    private final Handler main = new Handler(Looper.getMainLooper());
    private Listener listener;
    private final JSONArray allStreams = new JSONArray();
    private final StringBuilder report = new StringBuilder();
    private final ArrayList<File> plugins = new ArrayList<>();
    private int index;
    private String tmdbId, mediaType;
    private int season, episode;
    private boolean finished;

    public PluginRuntime(Context context) {
        this.context = context.getApplicationContext();
        http = new OkHttpClient.Builder().connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(40, TimeUnit.SECONDS).callTimeout(55, TimeUnit.SECONDS)
            .followRedirects(true).build();
        web = new WebView(context);
        WebSettings settings = web.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(false);
        settings.setAllowFileAccess(false);
        settings.setAllowContentAccess(false);
        web.addJavascriptInterface(new FetchBridge(), "NativeFetch");
        web.setWebViewClient(new WebViewClient() {
            @Override public void onPageFinished(WebView view, String url) { startNext(); }
        });
    }

    public void findStreams(String tmdbId, String mediaType, int season, int episode, Listener listener) {
        this.listener = listener;
        this.tmdbId = tmdbId;
        this.mediaType = mediaType;
        this.season = season;
        this.episode = episode;
        File dir = new File(context.getFilesDir(), "plugins");
        File[] files = dir.listFiles((d, name) -> name.endsWith(".js"));
        if (files != null) {
            Arrays.sort(files, Comparator.comparing(File::getName));
            plugins.addAll(Arrays.asList(files));
        }
        if (plugins.isEmpty()) { finish(); return; }
        String bootstrap = bootstrapScript();
        web.loadDataWithBaseURL("https://ciflix.local/", "<!doctype html><html><head><meta charset='utf-8'></head><body><script>" +
            bootstrap.replace("</script", "<\\/script") + "</script></body></html>", "text/html", "UTF-8", null);
    }

    private void startNext() {
        if (finished) return;
        if (index >= plugins.size()) { finish(); return; }
        File file = plugins.get(index++);
        try {
            byte[] bytes = java.nio.file.Files.readAllBytes(file.toPath());
            String source = new String(bytes, StandardCharsets.UTF_8);
            JSONObject payload = new JSONObject();
            payload.put("name", file.getName());
            payload.put("source", source);
            payload.put("id", tmdbId);
            payload.put("type", mediaType);
            payload.put("season", season);
            payload.put("episode", episode);
            web.evaluateJavascript("window.__runPlugin(" + JSONObject.quote(payload.toString()) + ")", null);
        } catch (Exception e) {
            report.append(file.getName()).append(": ").append(e.getMessage()).append("\n");
            startNext();
        }
    }

    private void onPluginResult(String name, String json, String error) {
        try {
            if (error != null && !error.isEmpty()) report.append(name).append(": ").append(error).append("\n");
            else {
                JSONArray arr = new JSONArray(json);
                for (int i=0;i<arr.length();i++) {
                    JSONObject item=arr.optJSONObject(i);
                    if (item == null) continue;
                    item.put("provider", item.optString("provider", name.replace(".js","")));
                    if (item.has("url") && item.optString("url").startsWith("http")) allStreams.put(item);
                }
                report.append(name).append(": ").append(arr.length()).append(" source(s)\n");
            }
        } catch (Exception e) { report.append(name).append(": invalid result\n"); }
        startNext();
    }

    private void finish() {
        if (finished) return;
        finished = true;
        if (listener != null) listener.onComplete(allStreams, report.toString());
        main.post(() -> { try { web.removeJavascriptInterface("NativeFetch"); web.destroy(); } catch (Exception ignored) {} });
    }

    private String bootstrapScript() {
        return "var __pending={}; var __seq=0;" +
        "window.__fetchDone=function(id,p){var x=__pending[id];if(!x)return;delete __pending[id];" +
        "if(p.error){x.reject(new Error(p.error));return;}var raw=atob(p.body64||'');var bytes=new Uint8Array(raw.length);for(var i=0;i<raw.length;i++)bytes[i]=raw.charCodeAt(i);" +
        "var hs=new Map(Object.entries(p.headers||{}).map(function(a){return [a[0].toLowerCase(),a[1]];}));" +
        "var h={get:function(k){return hs.get(String(k).toLowerCase())||null;},forEach:function(fn){hs.forEach(function(v,k){fn(v,k);});}};" +
        "var used=false;var r={ok:p.status>=200&&p.status<300,status:p.status,statusText:p.statusText||'',url:p.url,headers:h," +
        "text:function(){if(used)return Promise.reject(new TypeError('Body used'));used=true;return Promise.resolve(new TextDecoder().decode(bytes));}," +
        "json:function(){return this.text().then(JSON.parse);}," +
        "arrayBuffer:function(){if(used)return Promise.reject(new TypeError('Body used'));used=true;return Promise.resolve(bytes.buffer.slice(bytes.byteOffset,bytes.byteOffset+bytes.byteLength));}};x.resolve(r);};" +
        "window.fetch=function(url,opt){return new Promise(function(resolve,reject){var id=String(++__seq);__pending[id]={resolve:resolve,reject:reject};NativeFetch.request(id,String(url),JSON.stringify(opt||{}));});};" +
        "window.__runPlugin=function(packed){var p=JSON.parse(packed);try{var module={exports:{}};var factory=new Function('module','exports',p.source+'\\n;return module.exports;');var exp=factory(module,module.exports)||module.exports;" +
        "if(!exp||typeof exp.getStreams!=='function')throw new Error('getStreams export missing');" +
        "Promise.resolve(exp.getStreams(p.id,p.type,p.season||null,p.episode||null)).then(function(v){NativeFetch.pluginDone(p.name,JSON.stringify(Array.isArray(v)?v:[]),'');},function(e){NativeFetch.pluginDone(p.name,'',String(e&&e.stack||e));});" +
        "}catch(e){NativeFetch.pluginDone(p.name,'',String(e&&e.stack||e));}};";
    }

    private final class FetchBridge {
        @JavascriptInterface public void pluginDone(String name, String json, String error) {
            main.post(() -> onPluginResult(name, json, error));
        }
        @JavascriptInterface public void request(String id, String url, String optionsJson) {
            try {
                JSONObject options = new JSONObject(optionsJson);
                Request.Builder b = new Request.Builder().url(url);
                JSONObject headers = options.optJSONObject("headers");
                if (headers != null) {
                    Iterator<String> it = headers.keys();
                    while (it.hasNext()) { String k=it.next(); b.header(k, headers.optString(k)); }
                }
                String method = options.optString("method", "GET").toUpperCase(Locale.ROOT);
                RequestBody body = null;
                if (options.has("body") && !options.isNull("body")) {
                    MediaType type = MediaType.parse(headers == null ? null : headers.optString("Content-Type", null));
                    body = RequestBody.create(options.optString("body"), type);
                }
                if ("GET".equals(method) || "HEAD".equals(method)) b.method(method, null);
                else b.method(method, body == null ? RequestBody.create(new byte[0], null) : body);
                http.newCall(b.build()).enqueue(new Callback() {
                    @Override public void onFailure(Call call, IOException e) { deliverFetch(id, errorData(e.getMessage())); }
                    @Override public void onResponse(Call call, Response response) throws IOException {
                        try (Response r=response) {
                            byte[] bytes = r.body() == null ? new byte[0] : r.body().bytes();
                            JSONObject out = new JSONObject();
                            out.put("status", r.code()); out.put("statusText", r.message()); out.put("url", r.request().url().toString());
                            out.put("body64", Base64.encodeToString(bytes, Base64.NO_WRAP));
                            JSONObject hs = new JSONObject();
                            for (String key : r.headers().names()) hs.put(key, r.header(key));
                            out.put("headers", hs);
                            deliverFetch(id, out);
                        } catch (Exception e) { deliverFetch(id, new JSONObject().put("error", e.getMessage())); }
                    }
                });
            } catch (Exception e) { deliverFetch(id, new JSONObject().put("error", e.getMessage())); }
        }
        private JSONObject errorData(String message) { JSONObject o = new JSONObject(); try { o.put("error", message); } catch (Exception ignored) {} return o; }
        private void deliverFetch(String id, JSONObject data) {
            String script = "window.__fetchDone(" + JSONObject.quote(id) + "," + JSONObject.quote(data.toString()) + ")";
            main.post(() -> { if (!finished) web.evaluateJavascript(script, null); });
        }
    }
}
