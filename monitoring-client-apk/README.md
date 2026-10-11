# Monitoring Client (APK WebView)

Membuka dashboard https://palapanetstb.duckdns.org:121 (ubah di `app/src/main/res/values/strings.xml`).
Saat aplikasi pertama kali dibuka, Android meminta izin Kamera dan Notifikasi.

## Build lewat GitHub
1. Buat repository **private** di GitHub, upload seluruh isi folder ini (termasuk folder `.github`).
2. Buka tab **Actions** → **Build APK** → **Run workflow** (atau cukup push ke `main`).
3. Setelah hijau, buka run tersebut → bagian **Artifacts** → unduh `MonitoringClient-apk` (isinya `app-release.apk`).
4. Kirim APK ke HP dan install (izinkan "install dari sumber tidak dikenal").
