# Andar WiFi Analyzer

<p align="center">
  <img src="app/src/main/res/drawable/andar_logo_icon_1791651458189.jpg" alt="Andar Logo" width="100" />
</p>

<p align="center">
  Aplikasi diagnostik jaringan defensif untuk Android guna memindai saluran WiFi (2.4 GHz, 5 GHz, & 6 GHz), memetakan kekuatan sinyal nyata (dBm) melalui radar polar, menguji kecepatan internet (HTTPS), serta menganalisis latency DNS, port lokal, dan perangkat LAN pada jaringan milik sendiri.
</p>

<p align="center">
  <a href="https://kotlinlang.org"><img src="https://img.shields.io/badge/Kotlin-2.2.10-blue.svg?style=flat-square" alt="Kotlin"></a>
  <a href="https://developer.android.com/jetpack/compose"><img src="https://img.shields.io/badge/Jetpack_Compose-Material_3-blue.svg?style=flat-square" alt="Compose"></a>
  <a href="https://developer.android.com/about/versions/oreo"><img src="https://img.shields.io/badge/Android-8.0+_API_26+-green.svg?style=flat-square" alt="Android Version"></a>
  <a href="LICENSE"><img src="https://img.shields.io/badge/License-MIT-blue.svg?style=flat-square" alt="License"></a>
</p>

---

## Tujuan & Etika Penggunaan (Defensive Network Diagnostics)

**Andar WiFi Analyzer** dirancang khusus sebagai **alat diagnostik jaringan defensif** untuk:
* Menganalisis kualitas sinyal dan kepadatan saluran WiFi di rumah, kantor, atau laboratorium.
* Memeriksa konektivitas dan konfigurasi jaringan milik sendiri atau jaringan yang memiliki izin pengujian tertulis.
* Aplikasi ini **tidak** menyediakan fitur eksploitasi, *deauthentication*, *packet injection*, *brute force*, maupun pengambilan kredensial.
* Seluruh hasil pemindaian berasal dari kondisi perangkat dan jaringan nyata tanpa data simulasi/palsu.

---

## Ringkasan Fitur

* **Analisis Spektrum Saluran (2.4 GHz, 5 GHz, & 6 GHz)**
  * Kurva spektrum frekuensi untuk mendeteksi tumpukan *co-channel* dan *adjacent-channel interference* ($\pm 4$ saluran pada 2.4 GHz).
  * Rekomendasi saluran router terbaik yang memprioritaskan kanal *non-overlapping* sesuai dukungan perangkat/regulasi.
  * Inspeksi detail Access Point nyata: SSID, BSSID, RSSI (dBm), lebar saluran (20/40/80/160/320 MHz), standar (802.11a/b/g/n/ac/ax/be), dan keamanan.

* **Radar Sinyal Polar 360°**
  * Pemetaan koordinat polar interaktif berdasarkan level dBm nyata (`-30 dBm` hingga `-95 dBm`).
  * Filter pita frekuensi (`Semua`, `2.4 GHz`, `5 GHz`, `6 GHz`) dengan fokus detail Access Point.

* **Pengukur Kecepatan Internet (Speed Test)**
  * Pengukuran bertahap secara nyata melalui endpoint HTTPS (`speed.cloudflare.com`): Ping latency, Jitter, Download, dan Upload.
  * Hanya menghitung byte yang benar-benar ditransfer di jaringan (tanpa mencampur jeda UI) dan hanya menyimpan pengujian yang selesai dengan valid (`FINISHED`) ke database lokal Room.
  * Dilengkapi batas waktu dan ukuran transfer yang aman serta tombol pembatalan (*Cancel*) aktif.

* **Diagnostik Jaringan, TCP Latency, & DNS Benchmark**
  * **DNS Benchmark**: Mengukur TCP latency ke port 53 berbagai resolver DNS (Cloudflare, Google, Quad9, OpenDNS, AdGuard, serta Gateway lokal) dengan pembedaan status `COMPLETED`, `TIMEOUT`, `UNREACHABLE`, `OFFLINE`, dan `FAILED`.
  * **Uji TCP Latency**: Mengukur keterjangkauan dan waktu sambung TCP (port 443/80/53) dengan perhitungan paket terkirim, diterima, *packet loss*, dan min/avg/max riil.
  * **Pemindai Port Lokal (Port Scanner)**: Dibatasi secara ketat hanya untuk `localhost` dan alamat IP privat RFC1918 (`10.0.0.0/8`, `172.16.0.0/12`, `192.168.0.0/16`). Menolak target IP publik, URL, multicast, dan broadcast.
  * **Pemindai Perangkat LAN (LAN Scanner)**: Menghitung rentang host dari `LinkProperties` subnet aktual (dibatasi maksimal 254 host dengan *concurrency* terukur) dan menampilkan perangkat yang benar-benar merespons tanpa memotong daftar.

---

## Catatan Teknis & Privasi

* **WiFi Scan Throttling (Android 9+)**
  Mulai Android 9 (Pie), sistem membatasi permintaan pemindaian WiFi hingga 4 kali setiap 2 menit untuk aplikasi latar depan. Jika batas ini tercapai, aplikasi akan menampilkan pemberitahuan jujur bahwa sistem mengembalikan data *cache* pemindaian terakhir.
* **Izin Lokasi & Perangkat WiFi Sekitar**
  Sistem operasi Android mewajibkan izin `ACCESS_FINE_LOCATION` dan `NEARBY_WIFI_DEVICES` (Android 13+) serta layanan Lokasi yang aktif agar aplikasi dapat membaca SSID/BSSID hasil pemindaian WiFi.
* **Koneksi Layanan Eksternal & Penyimpanan Lokal**
  * Hasil pemindaian WiFi dan riwayat uji kecepatan disimpan secara lokal di perangkat (`Room SQLite`) dan dikecualikan dari pencadangan awan (*cloud backup* / `allowBackup="false"`).
  * Ketika fitur pemeriksaan **IP Publik/ISP** atau **Uji Kecepatan** dijalankan, aplikasi melakukan koneksi HTTPS ke layanan pihak ketiga (`https://api.ipify.org`, `https://ipwho.is`, dan `https://speed.cloudflare.com`).

---

## Rincian Izin (Permissions)

| Izin | Tujuan |
| :--- | :--- |
| `ACCESS_WIFI_STATE` | Membaca status WiFi, link speed, frekuensi, dan informasi koneksi aktif. |
| `CHANGE_WIFI_STATE` | Meminta pemindaian (*scan request*) jaringan WiFi sekitar. |
| `ACCESS_FINE_LOCATION` / `ACCESS_COARSE_LOCATION` | Diwajibkan oleh Android untuk membaca hasil pemindaian WiFi dan SSID. |
| `NEARBY_WIFI_DEVICES` | Izin pemindaian perangkat WiFi sekitar pada Android 13+ (API 33+). |
| `INTERNET` | Mengukur kecepatan internet, memeriksa IP publik, dan menguji keterjangkauan TCP/DNS. |
| `ACCESS_NETWORK_STATE` | Membaca `LinkProperties` (IP lokal, prefix subnet, gateway, DNS) dan status koneksi. |

---

## Kompilasi & Build

### Prasyarat
* Android Studio (Ladybug / Meerkat atau lebih baru)
* JDK 17+
* Android SDK (`compileSdk = 36`, `targetSdk = 36`, `minSdk = 26`)

### Perintah Gradle (Windows PowerShell / CMD)
```powershell
# Memeriksa versi Gradle Wrapper
.\gradlew.bat --version

# Menjalankan seluruh unit test lokal
.\gradlew.bat testDebugUnitTest

# Menjalankan pemeriksaan Android Lint
.\gradlew.bat lintDebug

# Membangun APK Debug
.\gradlew.bat assembleDebug
```
File APK hasil build debug akan tersedia di:
`app/build/outputs/apk/debug/app-debug.apk`

### Konfigurasi Release Signing (Opsional)
Build `release` tidak menyertakan keystore di dalam repositori. Jika ingin menandatangani APK/AAB `release`, sediakan variabel lingkungan atau properti Gradle berikut secara lokal:
* `RELEASE_STORE_FILE`
* `RELEASE_STORE_PASSWORD`
* `RELEASE_KEY_ALIAS`
* `RELEASE_KEY_PASSWORD`

---

## Tech Stack

* **Package / Application ID**: `com.andar.wifianalyzer`
* **Bahasa**: Kotlin 2.2.10
* **UI**: Jetpack Compose, Material Design 3
* **Concurrency**: Kotlin Coroutines, StateFlow
* **Database Lokal**: Room Persistence Library + KSP
* **Jaringan**: OkHttp 4, Java NIO / Net Sockets, Android `ConnectivityManager` & `WifiManager`
* **Arsitektur**: MVVM (Model-View-ViewModel)

---

## Lisensi

Proyek ini dirilis di bawah lisensi [MIT License](LICENSE).
