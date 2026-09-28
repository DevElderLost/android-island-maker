package com.megernolep.islandeditor;

import android.app.Activity;
import android.content.ContentValues;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.webkit.JavascriptInterface;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.widget.FrameLayout;
import android.widget.Toast;

import java.io.OutputStream;

/**
 * Wrapper offline buat island_editor.html: satu WebView, tanpa koneksi internet
 * (semua asset ada di dalam APK, di assets/island_editor.html).
 *
 * Dua hal yang WebView default GAK bisa tanpa ditambahin manual:
 *  1. <input type=file> (dipakai tombol "Import spec.json") -> perlu onShowFileChooser
 *  2. Blob download lewat <a download> (dipakai tombol "Export spec.json" versi browser)
 *     -> gak jalan reliable di WebView, makanya index.html manggil AndroidBridge.saveSpec()
 *     yang nulis langsung ke folder Downloads lewat MediaStore (aman buat scoped storage
 *     Android 10 / API 29, gak butuh permission WRITE_EXTERNAL_STORAGE).
 */
public class MainActivity extends Activity {

    private WebView webView;
    private ValueCallback<Uri[]> filePathCallback;
    private static final int FILE_CHOOSER_REQUEST = 51;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        webView = new WebView(this);
        setContentView(webView, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));

        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setAllowFileAccess(true);

        webView.addJavascriptInterface(new WebAppInterface(), "AndroidBridge");

        webView.setWebChromeClient(new WebChromeClient() {
            // Ngaktifin <input type=file> (tombol "Import spec.json" di editor)
            @Override
            public boolean onShowFileChooser(WebView view, ValueCallback<Uri[]> callback,
                                              FileChooserParams params) {
                filePathCallback = callback;
                Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
                intent.addCategory(Intent.CATEGORY_OPENABLE);
                intent.setType("application/json");
                try {
                    startActivityForResult(Intent.createChooser(intent, "Pilih spec.json"),
                            FILE_CHOOSER_REQUEST);
                } catch (Exception e) {
                    filePathCallback = null;
                    Toast.makeText(MainActivity.this, "Gak ada file manager: " + e.getMessage(),
                            Toast.LENGTH_LONG).show();
                    return false;
                }
                return true;
            }
        });

        webView.loadUrl("file:///android_asset/island_editor.html");
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == FILE_CHOOSER_REQUEST) {
            if (filePathCallback == null) return;
            Uri[] result = null;
            if (resultCode == Activity.RESULT_OK && data != null && data.getData() != null) {
                result = new Uri[]{data.getData()};
            }
            filePathCallback.onReceiveValue(result);
            filePathCallback = null;
            return;
        }
        super.onActivityResult(requestCode, resultCode, data);
    }

    /** Dipanggil dari JS: window.AndroidBridge.saveSpec(filename, jsonString) */
    private class WebAppInterface {
        @JavascriptInterface
        public String saveSpec(String filename, String json) {
            try {
                ContentValues values = new ContentValues();
                values.put(MediaStore.Downloads.DISPLAY_NAME, filename);
                values.put(MediaStore.Downloads.MIME_TYPE, "application/json");
                values.put(MediaStore.Downloads.IS_PENDING, 1);
                Uri uri = getContentResolver().insert(
                        MediaStore.Downloads.EXTERNAL_CONTENT_URI, values);
                if (uri == null) throw new RuntimeException("insert() balikin null");
                try (OutputStream out = getContentResolver().openOutputStream(uri)) {
                    out.write(json.getBytes("UTF-8"));
                }
                values.clear();
                values.put(MediaStore.Downloads.IS_PENDING, 0);
                getContentResolver().update(uri, values, null, null);
                return "Downloads/" + filename;
            } catch (Exception e) {
                return "GAGAL: " + e.getMessage();
            }
        }
    }
}
