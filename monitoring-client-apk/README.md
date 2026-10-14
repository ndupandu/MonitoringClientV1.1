# Monitoring Client (APK WebView)

Membuka dashboard https://palapanetstb.duckdns.org:121 (ubah di `app/src/main/res/values/strings.xml`).
Saat aplikasi pertama kali dibuka, Android meminta izin Kamera dan Notifikasi.

## Build lewat GitHub
1. Buat repository **private** di GitHub, upload seluruh isi folder ini (termasuk folder `.github`).
2. Buka tab **Actions** → **Build APK** → **Run workflow** (atau cukup push ke `main`).
3. Setelah hijau, buka run tersebut → bagian **Artifacts** → unduh `MonitoringClient-apk` (isinya `app-release.apk`).
4. Kirim APK ke HP dan install (izinkan "install dari sumber tidak dikenal").

## Notifikasi tiket saat aplikasi ditutup (v1.1)
Aplikasi menjalankan layanan latar belakang (`TicketService`) yang memeriksa tiket baru ke server tiap ~20 detik memakai login
yang sama dengan halaman web, lalu menampilkan notifikasi Android biasa (muncul di atas layar, berbunyi).
* Notifikasi kecil "Memantau tiket" yang menetap itu wajib dari Android supaya layanan tidak dimatikan.
* Saat pertama dibuka, aplikasi meminta pengecualian hemat baterai: pilih **Izinkan**.
* Pengaturan bunyi: Pengaturan HP > Aplikasi > Monitoring Client > Notifikasi > **Tiket baru** (pilih nada dan volume).
* Bila login habis (teknisi 7 hari), muncul notifikasi "Login berakhir": buka aplikasi dan login ulang.
* Butuh `web_tiket.py` dan `dashboard.py` terbaru di server (endpoint `/api/tiket/poll`).
