package id.pandu.monitoringclient;

import android.Manifest;
import android.app.Activity;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.net.http.SslError;
import android.os.Build;
import android.os.Bundle;
import android.provider.MediaStore;
import android.webkit.CookieManager;
import android.webkit.GeolocationPermissions;
import android.webkit.JavascriptInterface;
import android.webkit.PermissionRequest;
import android.webkit.SslErrorHandler;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import androidx.core.content.FileProvider;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class MainActivity extends Activity {

    private static final int REQ_START = 10;
    private static final int REQ_CAMERA_WEB = 11;
    private static final int REQ_FILE = 12;
    private static final int REQ_GEO = 13;
    private static final String CHANNEL = "alerts";

    private WebView web;
    private String appUrl;
    private String appHost;
    private PermissionRequest pendingWeb;
    private String pendingGeoOrigin;
    private GeolocationPermissions.Callback pendingGeoCb;
    private ValueCallback<Uri[]> fileCallback;
    private Uri cameraUri;
    private int notifId = 1;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        appUrl = getString(R.string.app_url);
        appHost = Uri.parse(appUrl).getHost();

        web = new WebView(this);
        setContentView(web);
        setupWebView();

        askFirstLaunchPermissions();
        web.loadUrl(appUrl);
    }

    // ---------- izin saat aplikasi pertama kali dibuka ----------
    private boolean has(String perm) {
        return checkSelfPermission(perm) == PackageManager.PERMISSION_GRANTED;
    }

    private void askFirstLaunchPermissions() {
        SharedPreferences sp = getSharedPreferences("app", MODE_PRIVATE);
        if (sp.getBoolean("asked", false)) return;
        sp.edit().putBoolean("asked", true).apply();

        List<String> need = new ArrayList<>();
        if (!has(Manifest.permission.CAMERA)) need.add(Manifest.permission.CAMERA);
        if (!has(Manifest.permission.ACCESS_FINE_LOCATION)) need.add(Manifest.permission.ACCESS_FINE_LOCATION);
        if (!has(Manifest.permission.ACCESS_COARSE_LOCATION)) need.add(Manifest.permission.ACCESS_COARSE_LOCATION);
        if (Build.VERSION.SDK_INT >= 33 && !has(Manifest.permission.POST_NOTIFICATIONS)) {
            need.add(Manifest.permission.POST_NOTIFICATIONS);
        }
        if (!need.isEmpty()) {
            requestPermissions(need.toArray(new String[0]), REQ_START);
        }
    }

    @Override
    public void onRequestPermissionsResult(int code, String[] perms, int[] results) {
        super.onRequestPermissionsResult(code, perms, results);
        if (code == REQ_CAMERA_WEB) {
            PermissionRequest p = pendingWeb;
            pendingWeb = null;
            if (p != null) {
                if (results.length > 0 && results[0] == PackageManager.PERMISSION_GRANTED) {
                    p.grant(new String[]{PermissionRequest.RESOURCE_VIDEO_CAPTURE});
                } else {
                    p.deny();
                }
            }
        } else if (code == REQ_GEO) {
            GeolocationPermissions.Callback cb = pendingGeoCb;
            String origin = pendingGeoOrigin;
            pendingGeoCb = null;
            pendingGeoOrigin = null;
            if (cb != null) cb.invoke(origin, hasLocation(), false);
        }
    }

    private boolean hasLocation() {
        return has(Manifest.permission.ACCESS_FINE_LOCATION) || has(Manifest.permission.ACCESS_COARSE_LOCATION);
    }

    // ---------- WebView ----------
    private void setupWebView() {
        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setMediaPlaybackRequiresUserGesture(false);
        s.setAllowFileAccess(false);
        s.setGeolocationEnabled(true);
        s.setSupportZoom(true);
        s.setBuiltInZoomControls(true);
        s.setDisplayZoomControls(false);
        s.setUseWideViewPort(true);
        s.setLoadWithOverviewMode(true);
        s.setUserAgentString(s.getUserAgentString() + " MonitoringClientApp/1.0");
        CookieManager.getInstance().setAcceptCookie(true);
        web.addJavascriptInterface(new Bridge(), "AndroidApp");

        web.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView v, WebResourceRequest r) {
                Uri u = r.getUrl();
                if ("https".equals(u.getScheme()) && appHost.equalsIgnoreCase(u.getHost())) {
                    return false;
                }
                try {
                    startActivity(new Intent(Intent.ACTION_VIEW, u));
                } catch (Exception ignored) {
                }
                return true;
            }

            @Override
            public void onReceivedError(WebView v, WebResourceRequest r, WebResourceError e) {
                if (r.isForMainFrame()) showError();
            }

            @Override
            public void onReceivedSslError(WebView v, SslErrorHandler h, SslError e) {
                h.cancel();   // sertifikat bermasalah: jangan pernah dilewati
                showError();
            }
        });

        web.setWebChromeClient(new WebChromeClient() {
            // kamera lewat getUserMedia (HTTPS)
            @Override
            public void onPermissionRequest(final PermissionRequest request) {
                runOnUiThread(() -> {
                    boolean cam = false;
                    for (String r : request.getResources()) {
                        if (PermissionRequest.RESOURCE_VIDEO_CAPTURE.equals(r)) cam = true;
                    }
                    String host = request.getOrigin() == null ? null : request.getOrigin().getHost();
                    if (!cam || host == null || !appHost.equalsIgnoreCase(host)) {
                        request.deny();
                        return;
                    }
                    if (has(Manifest.permission.CAMERA)) {
                        request.grant(new String[]{PermissionRequest.RESOURCE_VIDEO_CAPTURE});
                    } else {
                        if (pendingWeb != null) pendingWeb.deny();
                        pendingWeb = request;
                        requestPermissions(new String[]{Manifest.permission.CAMERA}, REQ_CAMERA_WEB);
                    }
                });
            }

            // lokasi / GPS dari halaman web (hanya untuk domain dashboard)
            @Override
            public void onGeolocationPermissionsShowPrompt(final String origin, final GeolocationPermissions.Callback callback) {
                runOnUiThread(() -> {
                    String host = Uri.parse(origin).getHost();
                    if (host == null || !appHost.equalsIgnoreCase(host)) {
                        callback.invoke(origin, false, false);
                    } else if (hasLocation()) {
                        callback.invoke(origin, true, false);
                    } else {
                        if (pendingGeoCb != null) pendingGeoCb.invoke(pendingGeoOrigin, false, false);
                        pendingGeoOrigin = origin;
                        pendingGeoCb = callback;
                        requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION,
                                Manifest.permission.ACCESS_COARSE_LOCATION}, REQ_GEO);
                    }
                });
            }

            @Override
            public void onPermissionRequestCanceled(PermissionRequest request) {
                if (pendingWeb == request) pendingWeb = null;
            }

            // tombol "pilih foto" / <input type=file>: tawarkan kamera dan galeri
            @Override
            public boolean onShowFileChooser(WebView v, ValueCallback<Uri[]> cb, FileChooserParams params) {
                if (fileCallback != null) fileCallback.onReceiveValue(null);
                fileCallback = cb;
                cameraUri = null;

                Intent pick = params.createIntent();
                Intent chooser = Intent.createChooser(pick, "Pilih foto");

                if (has(Manifest.permission.CAMERA)) {
                    try {
                        File dir = new File(getCacheDir(), "photos");
                        //noinspection ResultOfMethodCallIgnored
                        dir.mkdirs();
                        File f = File.createTempFile("foto_", ".jpg", dir);
                        cameraUri = FileProvider.getUriForFile(MainActivity.this, getPackageName() + ".files", f);
                        Intent cam = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
                        cam.putExtra(MediaStore.EXTRA_OUTPUT, cameraUri);
                        cam.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION | Intent.FLAG_GRANT_READ_URI_PERMISSION);
                        chooser.putExtra(Intent.EXTRA_INITIAL_INTENTS, new Intent[]{cam});
                    } catch (Exception e) {
                        cameraUri = null;
                    }
                }
                try {
                    startActivityForResult(chooser, REQ_FILE);
                } catch (Exception e) {
                    fileCallback = null;
                    cb.onReceiveValue(null);
                    return false;
                }
                return true;
            }
        });
    }

    @Override
    protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        if (req != REQ_FILE || fileCallback == null) return;
        Uri[] out = null;
        if (res == RESULT_OK) {
            if (data != null) out = WebChromeClient.FileChooserParams.parseResult(res, data);
            if (out == null && cameraUri != null) out = new Uri[]{cameraUri};
        }
        fileCallback.onReceiveValue(out);
        fileCallback = null;
        cameraUri = null;
    }

    private void showError() {
        String html = "<html><head><meta name='viewport' content='width=device-width,initial-scale=1'></head>"
                + "<body style='margin:0;min-height:100vh;display:flex;flex-direction:column;align-items:center;"
                + "justify-content:center;background:#061820;color:#e8f3f6;font-family:sans-serif;text-align:center;padding:24px'>"
                + "<h2 style='margin:0 0 8px'>Tidak dapat terhubung</h2>"
                + "<p style='margin:0 0 22px;color:#9db6bd'>Periksa koneksi internet, lalu coba lagi.</p>"
                + "<a href='" + appUrl + "' style='background:#0A8FA8;color:#fff;text-decoration:none;"
                + "padding:14px 28px;border-radius:14px;font-weight:700'>Coba lagi</a></body></html>";
        web.loadDataWithBaseURL(null, html, "text/html", "UTF-8", null);
    }

    // ---------- notifikasi: dipanggil dari halaman dengan AndroidApp.show(judul, isi) ----------
    private class Bridge {
        @JavascriptInterface
        public void show(String title, String text) {
            if (Build.VERSION.SDK_INT >= 33 && !has(Manifest.permission.POST_NOTIFICATIONS)) return;
            NotificationManager nm = getSystemService(NotificationManager.class);
            nm.createNotificationChannel(new NotificationChannel(CHANNEL, "Peringatan", NotificationManager.IMPORTANCE_HIGH));
            PendingIntent pi = PendingIntent.getActivity(MainActivity.this, 0,
                    new Intent(MainActivity.this, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
                    PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
            String t = title == null ? "" : title;
            String b = text == null ? "" : text;
            nm.notify(notifId++, new android.app.Notification.Builder(MainActivity.this, CHANNEL)
                    .setSmallIcon(R.drawable.ic_stat)
                    .setContentTitle(t.length() > 80 ? t.substring(0, 80) : t)
                    .setContentText(b.length() > 200 ? b.substring(0, 200) : b)
                    .setContentIntent(pi)
                    .setAutoCancel(true)
                    .build());
        }
    }

    // ---------- siklus hidup ----------
    @Override
    public void onBackPressed() {
        if (web.canGoBack()) web.goBack();
        else super.onBackPressed();
    }

    @Override
    protected void onPause() {
        web.onPause();
        super.onPause();
    }

    @Override
    protected void onResume() {
        super.onResume();
        web.onResume();
    }

    @Override
    protected void onDestroy() {
        web.destroy();
        super.onDestroy();
    }
}
