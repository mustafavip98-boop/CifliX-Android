package com.ciflix.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.Button;
import android.view.Gravity;
import android.view.WindowManager;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.EditText;
import android.widget.Toast;
import android.content.SharedPreferences;
import android.graphics.Color;
import androidx.webkit.WebViewAssetLoader;

public class MainActivity extends Activity {
    private WebView web;
    private SharedPreferences prefs;
    private WebViewAssetLoader assetLoader;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setStatusBarColor(Color.rgb(9,11,16));
        getWindow().setNavigationBarColor(Color.rgb(9,11,16));
        prefs = getSharedPreferences("ciflix", MODE_PRIVATE);
        web = new WebView(this);
        FrameLayout root = new FrameLayout(this);
        root.addView(web, new FrameLayout.LayoutParams(-1, -1));
        Button settings = new Button(this);
        settings.setText("⚙");
        settings.setTextSize(18);
        FrameLayout.LayoutParams bp = new FrameLayout.LayoutParams(52, 52, Gravity.TOP | Gravity.END);
        bp.topMargin = 8; bp.rightMargin = 8;
        root.addView(settings, bp);
        settings.setOnClickListener(v -> showApiDialog());
        setContentView(root);
        web.getSettings().setJavaScriptEnabled(true);
        web.getSettings().setDomStorageEnabled(true);
        web.getSettings().setMediaPlaybackRequiresUserGesture(false);
        web.setBackgroundColor(Color.rgb(9,11,16));
        web.setWebChromeClient(new WebChromeClient());
        web.addJavascriptInterface(new Bridge(), "CifliXNative");
        assetLoader = new WebViewAssetLoader.Builder()
            .addPathHandler("/assets/", new WebViewAssetLoader.AssetsPathHandler(this)).build();
        web.setWebViewClient(new WebViewClient() {
            @Override public android.webkit.WebResourceResponse shouldInterceptRequest(WebView v, WebResourceRequest r) {
                return assetLoader.shouldInterceptRequest(r.getUrl());
            }
        });
        web.loadUrl("https://appassets.androidplatform.net/assets/www/index.html");
    }

    private class Bridge {
        @JavascriptInterface public String getApiBase() { return prefs.getString("api_base", ""); }
        @JavascriptInterface public void openApiSettings() { runOnUiThread(() -> showApiDialog()); }
    }

    private void showApiDialog() {
        EditText input = new EditText(this);
        input.setSingleLine(true);
        input.setHint("https://your-api-domain.com/api");
        input.setText(prefs.getString("api_base", ""));
        new AlertDialog.Builder(this)
            .setTitle("إعداد اتصال CifliX")
            .setMessage("أدخل عنوان API العام الذي يستضيف خادم CifliX. بدون خادم عام ستظهر بيانات تجريبية فقط.")
            .setView(input)
            .setNegativeButton("إلغاء", null)
            .setNeutralButton("مسح", (d,w) -> { prefs.edit().remove("api_base").apply(); web.reload(); })
            .setPositiveButton("حفظ", (d,w) -> {
                String value = input.getText().toString().trim().replaceAll("/$", "");
                if (!value.isEmpty() && !value.startsWith("https://")) {
                    Toast.makeText(this, "استخدم رابط HTTPS آمن", Toast.LENGTH_LONG).show(); return;
                }
                prefs.edit().putString("api_base", value).apply(); web.reload();
            }).show();
    }

    @Override public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_MENU) { showApiDialog(); return true; }
        if (keyCode == KeyEvent.KEYCODE_BACK && web.canGoBack()) { web.goBack(); return true; }
        return super.onKeyDown(keyCode, event);
    }
}
