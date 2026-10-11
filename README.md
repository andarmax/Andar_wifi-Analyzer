# Andar WiFi Analyzer

<p align="center">
  <img src="app/src/main/res/drawable/andar_logo_icon_1791651458189.jpg" alt="Andar Logo" width="100" />
</p>

<p align="center">
  Android utility for scanning WiFi channels, measuring signal strength (dBm) via polar radar, benchmarking DNS latency, and testing network throughput.
</p>

<p align="center">
  <a href="https://kotlinlang.org"><img src="https://img.shields.io/badge/Kotlin-2.0-blue.svg?style=flat-square" alt="Kotlin"></a>
  <a href="https://developer.android.com/jetpack/compose"><img src="https://img.shields.io/badge/Jetpack_Compose-Material_3-blue.svg?style=flat-square" alt="Compose"></a>
  <a href="https://developer.android.com/about/versions/oreo"><img src="https://img.shields.io/badge/Android-8.0+_API_26+-green.svg?style=flat-square" alt="Android Version"></a>
  <a href="LICENSE"><img src="https://img.shields.io/badge/License-MIT-blue.svg?style=flat-square" alt="License"></a>
</p>

---

## Ringkasan Fitur

* **Analisis Spektrum Saluran (2.4 GHz & 5 GHz)**
  * Kurva parabola frekuensi untuk mendeteksi tumpukan *co-channel* dan *adjacent channel interference*.
  * Rekomendasi saluran router terbaik berdasarkan kalkulasi kepadatan sinyal.
  * Inspeksi detail AP: BSSID, RSSI (dBm), channel width (20/40/80 MHz), standar (802.11n/ac/ax), dan keamanan.

* **Radar Sinyal Polar 360°**
  * Pemetaan koordinat polar interaktif berdasarkan level dBm (-30 dBm hingga -95 dBm).
  * Filter pita frekuensi (Semua, 2.4 GHz, 5 GHz) dengan indikator node terpilih.

* **Pengukur Kecepatan (Speed Test)**
  * Pengukuran bertahap: Ping latency, Jitter, Download (HTTP chunk stream), dan Upload.
  * Penyimpanan riwayat pengujian lokal menggunakan Room (SQLite).
  * Evaluasi otomatis untuk kelayakan streaming 4K, gaming online, dan video call.

* **Diagnostik Jaringan & DNS Benchmark**
  * Uji komparasi latency (ms) ke berbagai resolver DNS (Cloudflare 1.1.1.1, Google 8.8.8.8, Quad9 9.9.9.9, OpenDNS, AdGuard, dan Gateway lokal).
  * Pemindai port TCP (port 21, 22, 53, 80, 443, 3306, 8080).
  * Pemindai subnet LAN lokal untuk menemukan perangkat aktif (IP, Hostname, Ping).
  * Grafik timeline fluktuasi dBm untuk melacak area *dead zone*.
  * Tautan langsung ke halaman web admin router gateway (`http://<gateway_ip>`).

---

## Catatan Teknis Sistem Android

* **WiFi Scan Throttling (Android 9+)**  
  Mulai dari Android 9 (Pie), sistem membatasi frekuensi pemindaian WiFi (*throttling*) hingga 4 kali per 2 menit untuk aplikasi di latar depan. Untuk pemindaian *real-time* tanpa jeda, Anda dapat menonaktifkan pembatasan ini melalui:  
  `Opsi Pengembang (Developer Options) > Batasan Pemindaian Wi-Fi (Wi-Fi scan throttling) > Nonaktifkan`.

* **Izin Lokasi pada Android**  
  Android mewajibkan izin `ACCESS_FINE_LOCATION` (atau `NEARBY_WIFI_DEVICES` pada Android 13+) agar aplikasi dapat membaca nama SSID dan kekuatan sinyal dBm jaringan sekitar. Aplikasi ini memproses seluruh data secara lokal tanpa mengunggah data lokasi apa pun ke server eksternal.

---

## Rincian Izin (Permissions)

| Izin | Tujuan |
| :--- | :--- |
| `ACCESS_WIFI_STATE` | Membaca status koneksi WiFi, link speed, frekuensi, dan BSSID. |
| `CHANGE_WIFI_STATE` | Memulai permintaan pemindaian (*scan request*) Access Point sekitar. |
| `ACCESS_FINE_LOCATION` | Diwajibkan oleh OS Android untuk mengakses hasil pemindaian WiFi. |
| `NEARBY_WIFI_DEVICES` | Izin pemindaian perangkat WiFi terdekat untuk Android 13+ (API 33). |
| `INTERNET` | Menjalankan uji kecepatan (speed test), ping latency, dan benchmark DNS. |
| `ACCESS_NETWORK_STATE` | Memeriksa ketersediaan gateway dan antarmuka jaringan aktif. |

---

## Kompilasi & Build

### Prasyarat
* Android Studio (Hedgehog 2023.1.1 atau yang lebih baru)
* JDK 17
* Android SDK (API level 26 hingga 36)

### Perintah Gradle
```bash
# Clone repository
git clone https://github.com/USERNAME/andar-wifi-analyzer.git
cd andar-wifi-analyzer

# Menjalankan unit test lokal
./gradlew testDebugUnitTest

# Membangun file APK Debug
./gradlew assembleDebug

# Membangun file APK Release
./gradlew assembleRelease
```
File APK hasil build dapat ditemukan di:
`app/build/outputs/apk/debug/app-debug.apk`

---

## Tech Stack

* **Language**: Kotlin 2.0
* **UI**: Jetpack Compose, Material Design 3
* **Concurrency**: Kotlin Coroutines, StateFlow
* **Database**: Room Persistence Library + KSP
* **Network**: OkHttp 4, Java NIO Sockets
* **Architecture**: MVVM (Model-View-ViewModel)

---

## Lisensi

Proyek ini dirilis di bawah lisensi [MIT License](LICENSE). Anda bebas menggunakan, memodifikasi, dan mendistribusikan kode ini untuk keperluan pribadi maupun komersial.
