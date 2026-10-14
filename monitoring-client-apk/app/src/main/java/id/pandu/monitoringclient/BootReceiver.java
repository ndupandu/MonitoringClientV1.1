package id.pandu.monitoringclient;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** Menyalakan layanan notifikasi tiket lagi setelah HP restart atau aplikasi diperbarui. */
public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context ctx, Intent intent) {
        try {
            ctx.startForegroundService(new Intent(ctx, TicketService.class));
        } catch (Exception ignored) {
            // Android menolak start dari latar belakang: layanan akan menyala saat aplikasi dibuka
        }
    }
}
