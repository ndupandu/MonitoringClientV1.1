package id.pandu.monitoringclient;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;

/**
 * Layanan latar belakang: memeriksa tiket baru ke server tiap ~20 detik (memakai login yang sama dengan halaman web)
 * lalu menampilkan notifikasi Android biasa (muncul di atas layar, berbunyi) walau aplikasi sudah ditutup.
 * Notifikasi kecil "Memantau tiket" yang menetap adalah syarat Android supaya layanan tidak dimatikan.
 */
public class TicketService extends Service {

    static final String CH_FG = "pantau";
    static final String CH_TIKET = "tiket";
    private static final int FG_ID = 1001;
    private static final long INTERVAL_MS = 20_000L;

    private HandlerThread thread;
    private Handler handler;
    private boolean authWarned = false;

    private final Runnable loop = new Runnable() {
        @Override
        public void run() {
            try {
                pollOnce();
            } catch (Exception ignored) {
                // jaringan putus / server mati: coba lagi pada putaran berikutnya
            }
            if (handler != null) handler.postDelayed(this, INTERVAL_MS);
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();
        NotificationManager nm = getSystemService(NotificationManager.class);
        NotificationChannel fg = new NotificationChannel(CH_FG, "Pemantauan tiket", NotificationManager.IMPORTANCE_MIN);
        fg.setShowBadge(false);
        nm.createNotificationChannel(fg);
        NotificationChannel tk = new NotificationChannel(CH_TIKET, "Tiket baru", NotificationManager.IMPORTANCE_HIGH);
        tk.enableVibration(true);
        tk.setVibrationPattern(new long[]{0, 300, 150, 300});
        nm.createNotificationChannel(tk);

        thread = new HandlerThread("tiket-poll");
        thread.start();
        handler = new Handler(thread.getLooper());
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Notification n = new Notification.Builder(this, CH_FG)
                .setSmallIcon(R.drawable.ic_stat)
                .setContentTitle("Memantau tiket")
                .setContentText("Notifikasi tiket aktif")
                .setOngoing(true)
                .setContentIntent(openApp(0, null))
                .build();
        try {
            if (Build.VERSION.SDK_INT >= 34) {
                startForeground(FG_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
            } else {
                startForeground(FG_ID, n);
            }
        } catch (Exception e) {
            stopSelf();
            return START_NOT_STICKY;
        }
        handler.removeCallbacks(loop);
        handler.post(loop);
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        if (handler != null) handler.removeCallbacksAndMessages(null);
        handler = null;
        if (thread != null) thread.quitSafely();
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private PendingIntent openApp(int code, String path) {
        Intent i = new Intent(this, MainActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        if (path != null) i.putExtra("open", path);
        return PendingIntent.getActivity(this, code, i, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
    }

    // ---------- satu putaran pemeriksaan ----------
    private void pollOnce() throws Exception {
        SharedPreferences sp = getSharedPreferences("app", MODE_PRIVATE);
        String cookie = sp.getString("cookie", "");
        if (cookie.isEmpty()) return;                       // belum pernah login di aplikasi

        long last = sp.getLong("last_id", -1);
        URL url = new URL(getString(R.string.app_url) + "/api/tiket/poll?after=" + last);
        HttpURLConnection c = (HttpURLConnection) url.openConnection();
        c.setConnectTimeout(10_000);
        c.setReadTimeout(10_000);
        c.setInstanceFollowRedirects(false);                // dialihkan ke /login = sesi habis
        c.setRequestProperty("Cookie", cookie);
        c.setRequestProperty("Accept", "application/json");
        try {
            int code = c.getResponseCode();
            if (code == 401 || code == 403 || (code >= 300 && code < 400)) {
                warnAuth();
                return;
            }
            if (code != 200) return;
            JSONObject o = new JSONObject(readAll(c.getInputStream()));
            if (!o.optBoolean("ok", false)) return;
            authWarned = false;

            long max = o.optLong("max", last);
            JSONArray items = o.optJSONArray("items");
            if (last >= 0 && items != null && !MainActivity.visible) {   // aplikasi sedang dibuka: halaman web sudah menampilkannya
                for (int i = 0; i < items.length(); i++) {
                    JSONObject it = items.getJSONObject(i);
                    notifyTicket(it.optInt("id"), it.optString("title"), it.optString("body"), it.optBoolean("loud"));
                }
            }
            if (max != last) sp.edit().putLong("last_id", max).apply();
        } finally {
            c.disconnect();
        }
    }

    private void warnAuth() {
        if (authWarned) return;
        authWarned = true;
        notifyTicket(900001, "Login berakhir", "Buka aplikasi dan login ulang supaya notifikasi tiket aktif lagi.", false);
    }

    private void notifyTicket(int id, String title, String body, boolean loud) {
        NotificationManager nm = getSystemService(NotificationManager.class);
        String t = title.length() > 90 ? title.substring(0, 90) : title;
        String b = body.length() > 400 ? body.substring(0, 400) : body;
        Notification n = new Notification.Builder(this, CH_TIKET)
                .setSmallIcon(R.drawable.ic_stat)
                .setContentTitle(t)
                .setContentText(b.split("\n")[0])
                .setStyle(new Notification.BigTextStyle().bigText(b))      // isi lengkap saat notifikasi dibuka/diperluas
                .setCategory(Notification.CATEGORY_MESSAGE)
                .setAutoCancel(true)
                .setWhen(System.currentTimeMillis())
                .setShowWhen(true)
                .setContentIntent(openApp(id, "/tiket"))
                .build();
        nm.notify(id, n);
    }

    private static String readAll(InputStream in) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[4096];
        int n, total = 0;
        while ((n = in.read(buf)) > 0 && total < 262_144) {
            out.write(buf, 0, n);
            total += n;
        }
        return out.toString("UTF-8");
    }
}
