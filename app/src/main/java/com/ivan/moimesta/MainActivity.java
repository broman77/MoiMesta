package com.ivan.moimesta;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.util.Base64;
import android.view.View;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

public class MainActivity extends Activity {
    private static final int REQUEST_SAVE_FILE = 4101;
    private static final int REQUEST_OPEN_BACKUP = 4102;

    private WebView webView;
    private String pendingFileContent;
    private String pendingFileMime;
    private String pendingFileName;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        applySystemBars(false);

        webView = new WebView(this);
        webView.setBackgroundColor(Color.rgb(246, 249, 254));
        webView.setFitsSystemWindows(true);
        setContentView(webView);

        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(true);
        settings.setAllowFileAccess(true);
        settings.setAllowContentAccess(true);
        settings.setBuiltInZoomControls(false);
        settings.setDisplayZoomControls(false);
        settings.setLoadWithOverviewMode(true);
        settings.setUseWideViewPort(true);
        settings.setMediaPlaybackRequiresUserGesture(true);

        webView.addJavascriptInterface(new AndroidBridge(), "AndroidBridge");
        webView.setWebViewClient(new WebViewClient());
        webView.setWebChromeClient(new WebChromeClient());
        webView.setLayerType(View.LAYER_TYPE_HARDWARE, null);
        webView.loadUrl("file:///android_asset/index.html");
    }

    private void applySystemBars(boolean dark) {
        int background = dark ? Color.rgb(21, 31, 48) : Color.WHITE;
        getWindow().setStatusBarColor(background);
        getWindow().setNavigationBarColor(background);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            int flags = 0;
            if (!dark) {
                flags |= View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    flags |= View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
                }
            }
            getWindow().getDecorView().setSystemUiVisibility(flags);
        }
        if (webView != null) {
            webView.setBackgroundColor(dark ? Color.rgb(14, 22, 36) : Color.rgb(246, 249, 254));
        }
    }

    public class AndroidBridge {
        @JavascriptInterface
        public void setDarkTheme(boolean dark) {
            runOnUiThread(() -> applySystemBars(dark));
        }

        @JavascriptInterface
        public void saveFile(String fileName, String mimeType, String content) {
            pendingFileName = (fileName == null || fileName.trim().isEmpty()) ? "MoiMesta-export.txt" : fileName;
            pendingFileMime = (mimeType == null || mimeType.trim().isEmpty()) ? "text/plain" : mimeType;
            pendingFileContent = content == null ? "" : content;

            runOnUiThread(() -> {
                Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
                intent.addCategory(Intent.CATEGORY_OPENABLE);
                intent.setType(pendingFileMime);
                intent.putExtra(Intent.EXTRA_TITLE, pendingFileName);
                startActivityForResult(intent, REQUEST_SAVE_FILE);
            });
        }

        @JavascriptInterface
        public void openBackup() {
            runOnUiThread(() -> {
                Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                intent.addCategory(Intent.CATEGORY_OPENABLE);
                intent.setType("application/json");
                intent.putExtra(Intent.EXTRA_MIME_TYPES, new String[]{"application/json", "text/json", "text/plain", "application/octet-stream"});
                startActivityForResult(intent, REQUEST_OPEN_BACKUP);
            });
        }
    }

    private void notifySaveResult(boolean ok, String message) {
        if (webView == null) return;
        String js = "window.onNativeFileSaved && window.onNativeFileSaved(" + ok + "," + JSONObject.quote(message) + ")";
        webView.evaluateJavascript(js, null);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == REQUEST_SAVE_FILE) {
            if (resultCode != RESULT_OK || data == null || data.getData() == null) {
                notifySaveResult(false, "Сохранение отменено");
                return;
            }
            Uri uri = data.getData();
            try (OutputStream out = getContentResolver().openOutputStream(uri, "wt")) {
                if (out == null) throw new IllegalStateException("Не удалось открыть файл");
                out.write((pendingFileContent == null ? "" : pendingFileContent).getBytes(StandardCharsets.UTF_8));
                out.flush();
                notifySaveResult(true, "Файл сохранён");
            } catch (Exception e) {
                notifySaveResult(false, "Ошибка сохранения: " + e.getMessage());
            } finally {
                pendingFileContent = null;
                pendingFileMime = null;
                pendingFileName = null;
            }
            return;
        }

        if (requestCode == REQUEST_OPEN_BACKUP) {
            if (resultCode != RESULT_OK || data == null || data.getData() == null) return;
            Uri uri = data.getData();
            try (InputStream in = getContentResolver().openInputStream(uri);
                 ByteArrayOutputStream buffer = new ByteArrayOutputStream()) {
                if (in == null) throw new IllegalStateException("Не удалось открыть резервную копию");
                byte[] chunk = new byte[8192];
                int read;
                int total = 0;
                while ((read = in.read(chunk)) != -1) {
                    total += read;
                    if (total > 20 * 1024 * 1024) throw new IllegalStateException("Файл слишком большой");
                    buffer.write(chunk, 0, read);
                }
                String encoded = Base64.encodeToString(buffer.toByteArray(), Base64.NO_WRAP);
                if (webView != null) {
                    webView.evaluateJavascript("window.importBackupFromNative && window.importBackupFromNative('" + encoded + "')", null);
                }
            } catch (Exception e) {
                if (webView != null) {
                    webView.evaluateJavascript("alert(" + JSONObject.quote("Не удалось открыть резервную копию: " + e.getMessage()) + ")", null);
                }
            }
        }
    }

    @Override
    public void onBackPressed() {
        if (webView == null) {
            super.onBackPressed();
            return;
        }
        webView.evaluateJavascript(
            "(typeof handleAndroidBack === 'function' && handleAndroidBack()) ? 'handled' : 'exit'",
            value -> {
                if (value == null || !value.contains("handled")) {
                    MainActivity.super.onBackPressed();
                }
            }
        );
    }

    @Override
    protected void onDestroy() {
        if (webView != null) webView.destroy();
        super.onDestroy();
    }
}
