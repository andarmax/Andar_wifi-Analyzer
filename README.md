# Andar WiFi Analyzer 📶⚡

<p align="center">
  <img src="app/src/main/res/drawable/andar_logo_icon_1791651458189.jpg" alt="Andar WiFi Analyzer Logo" width="140" style="border-radius: 28px;" />
</p>

<p align="center">
  <b>Aplikasi Analisis Jaringan & Pengukur Kecepatan WiFi Modern untuk Android</b><br>
  <i>Modern, Privacy-First WiFi Analyzer, Radar, Speed Tester & Network Diagnostics built with Jetpack Compose & Kotlin.</i>
</p>

<p align="center">
  <img src="https://img.shields.io/badge/Platform-Android-3DDC84?logo=android&logoColor=white" alt="Android Platform" />
  <img src="https://img.shields.io/badge/Language-Kotlin-7F52FF?logo=kotlin&logoColor=white" alt="Kotlin" />
  <img src="https://img.shields.io/badge/UI-Jetpack%20Compose-4285F4?logo=jetpackcompose&logoColor=white" alt="Jetpack Compose" />
  <img src="https://img.shields.io/badge/Design-Material%203-00E5FF" alt="Material 3" />
  <img src="https://img.shields.io/badge/Min%20SDK-26%20(Android%208.0)-brightgreen" alt="Min SDK" />
  <img src="https://img.shields.io/badge/Target%20SDK-36-blue" alt="Target SDK" />
  <img src="https://img.shields.io/badge/Database-Room%20SQLite-orange" alt="Room Database" />
  <img src="https://img.shields.io/badge/License-MIT-green" alt="License" />
</p>

---

## 🌟 Fitur Utama (Key Features)

### 1. 📡 Analisis Saluran & Spektrum Frekuensi (Channel Analyzer)
* **Grafik Spektrum Kurva Parabola**: Visualisasi gelombang WiFi pada pita **2.4 GHz** (Saluran 1–14) dan **5 GHz** (Saluran 36–165).
* **Deteksi Tumpukan Interferensi**: Menghitung tumpukan *co-channel* dan *adjacent-channel* dari router tetangga.
* **Sistem Rating Saluran Router**: Rekomendasi saluran terbaik (⭐️ 1–5 bintang) untuk konfigurasi router agar bebas gangguan (misal saluran 1, 6, dan 11).
* **Daftar Access Point Sekitar**: Informasi detail BSSID, kekuatan sinyal dBm, lebar pita (*channel width* 20/40/80 MHz), tipe enkripsi (WPA2/WPA3), dan estimasi jarak meter.

### 2. 🎯 Radar Sinyal Polar 360° (Visual Signal Strength Radar)
* **Circular Polar Chart**: Tata letak radar lingkaran dengan cincin konsentris dBm (*Sangat Dekat < -40 dBm*, *Kuat -60 dBm*, *Sedang -75 dBm*, *Lemah > -85 dBm*).
* **Sinar Radar Animasi (Sweep Beam)**: Sapuan berkas radar 360° berputar real-time dengan efek ekor gradasi cyan.
* **Efek Denyut (Pulse Effect)**: Jaringan yang tersambung memancarkan denyut riak hijau.
* **Interaktif**: Sentuh titik node jaringan pada radar untuk memeriksa detail teknis jaringan secara langsung.

### 3. ⚡ Uji Kecepatan Internet Riil (Speed Test)
* **Speedometer Gauge Animasi**: Dial jarum analog interaktif dengan cincin *glow* dan angka transfer digital real-time.
* **Metrik Lengkap**: Pengukuran multi-tahap untuk **Download (Mbps)**, **Upload (Mbps)**, **Ping Latency (ms)**, dan **Jitter (ms)**.
* **Analisis Kelayakan Aplikasi**: Rekomendasi otomatis untuk streaming video 4K Ultra HD, game online kompetitif, dan video call Zoom/Meet.
* **Database Riwayat Lokal (Room SQLite)**: Riwayat pengujian tersimpan rapi di perangkat lokal.
* **Fitur Bagikan Hasil**: Tombol instan membagikan ringkasan kecepatan ke WhatsApp atau media sosial.

### 4. 🔍 Pemindai Jaringan LAN (LAN Device Scanner)
* Memindai subnet IP lokal untuk menemukan semua perangkat yang terhubung ke jaringan WiFi Anda.
* Mengidentifikasi jenis perangkat (Router, Smartphone, Laptop/PC, Smart TV, Printer Jaringan, Kamera CCTV/IoT).
* Menampilkan IP lokal, hostname, vendor produsen, dan waktu respons ping.

### 5. 🛠️ Alat Diagnostik Jaringan Lengkap
* **Uji Ping DNS Server (DNS Latency Benchmark)**: Menguji dan membandingkan latency ke server DNS populer (Cloudflare `1.1.1.1`, Google `8.8.8.8`, Quad9 `9.9.9.9`, OpenDNS, AdGuard, Gateway Router) lengkap dengan rekomendasi **DNS Tercepat ⚡**.
* **Grafik Fluktuasi Sinyal (Real-time dBm Timeline)**: Memantau kekuatan sinyal secara dinamis saat berjalan di sekitar ruangan untuk melacak titik mati sinyal (*dead zone*).
* **Ping Latency Tester**: Konsol terminal log monospace untuk menguji ping ke host mana pun dengan statistik *packet loss*.
* **Pemindai Port (Port Scanner)**: Memeriksa port terbuka pada router atau server lokal (Port 80 HTTP, 443 HTTPS, 22 SSH, 53 DNS, 3306 MySQL, dll.).
* **Pintasan Admin Router**: Tombol satu sentuhan untuk membuka halaman web admin router (misal `http://192.168.1.1`) di browser.
* **IP Publik & ISP Lookup**: Menampilkan alamat IP publik dan nama penyedia internet Anda.

---

## 🛠️ Arsitektur & Teknologi (Tech Stack)

* **Bahasa Pemrograman**: [Kotlin](https://kotlinlang.org/) (100%)
* **Framework UI**: [Jetpack Compose](https://developer.android.com/jetpack/compose) & Material Design 3 (M3)
* **Arsitektur**: MVVM (Model-View-ViewModel) + Clean Architecture
* **State Management**: Kotlin Coroutines & `StateFlow` / `collectAsStateWithLifecycle`
* **Penyimpanan Lokal**: [Room Database](https://developer.android.com/training/data-storage/room) (SQLite) + KSP
* **Networking**: [OkHttp](https://square.github.io/okhttp/) & Java NIO Sockets
* **Asset & Visual**: Custom adaptive launcher icon & modern vector graphics

---

## 📂 Struktur Proyek

```
app/src/main/
├── AndroidManifest.xml
├── java/com/example/
│   ├── MainActivity.kt                # Host Activity, Navigation Bar, Permission Handler
│   ├── data/                          # Room Database, DAO & Entity (Riwayat Speed Test)
│   ├── model/                         # Data models (WifiAccessPoint, DnsItem, LanDevice, dll)
│   ├── service/
│   │   ├── WifiScannerEngine.kt       # Engine pemindaian WiFi & perhitungan rating saluran
│   │   ├── SpeedTestEngine.kt         # Engine uji kecepatan riil (Download, Upload, Ping)
│   │   └── NetworkDiagnosticsEngine.kt# Engine ping DNS, scanner port, dan subnet LAN
│   └── ui/
│       ├── WifiViewModel.kt           # StateFlow & logika bisnis antarmuka
│       ├── components/                # SpeedometerGauge, SignalRadarPolarChart, ArcMeter
│       ├── screens/
│       │   ├── DashboardScreen.kt     # Dasbor ringkasan & gauge sinyal
│       │   ├── SignalRadarScreen.kt   # Layar radar polar 360° interaktif
│       │   ├── ChannelAnalyzerScreen.kt# Grafik spektrum kurva 2.4G & 5G
│       │   ├── SpeedTestScreen.kt     # Pengukur kecepatan & riwayat
│       │   └── DiagnosticsScreen.kt   # DNS Benchmark, Ping, Port Scanner & LAN
│       └── theme/                     # Skema warna Cyber Dark & Tipografi M3
└── res/
    ├── drawable/                      # Logo resmi Andar & asset grafis
    ├── mipmap-*/                      # Ikon aplikasi adaptif Android
    └── values/strings.xml             # Resource teks aplikasi
```

---

## 🚀 Cara Menjalankan Proyek (Getting Started)

### Prasyarat:
1. **Android Studio** (versi Hedgehog, Iguana, Koala, atau yang lebih baru).
2. **JDK 17** atau yang lebih baru.
3. Perangkat fisik Android dengan **Android 8.0 (API 26)** ke atas atau Android Emulator.

### Langkah-langkah:
1. **Clone repository ini:**
   ```bash
   git clone https://github.com/USERNAME/andar-wifi-analyzer.git
   cd andar-wifi-analyzer
   ```
2. **Buka proyek di Android Studio:**
   Pilih *File > Open* lalu pilih direktori proyek ini.
3. **Sinkronkan Gradle:**
   Biarkan Gradle mengunduh dependensi secara otomatis (*Sync Project with Gradle Files*).
4. **Jalankan Aplikasi:**
   Hubungkan HP Android via kabel USB (aktifkan *USB Debugging*) atau jalankan emulator, lalu klik tombol **Run ▶**.

---

## 🔒 Privasi & Keamanan (Privacy & Security)

Aplikasi ini memprioritaskan privasi pengguna secara penuh:
* **Tanpa Pelacak (Zero Trackers)**: Tidak mengandung SDK iklan atau pelacak pihak ketiga.
* **Izin Lokasi**: Izin lokasi pada Android hanya digunakan oleh sistem operasi Android untuk mengakses data scan frekuensi saluran WiFi lokal dan **sama sekali tidak pernah dikirim atau diunggah ke internet**.
* **Penyimpanan Lokal**: Seluruh data riwayat pengujian kecepatan disimpan secara lokal di perangkat Anda menggunakan Room Database.

---

## 🤝 Kontribusi

Kontribusi selalu terbuka! Jika Anda memiliki ide perbaikan atau menemukan bug:
1. Fork repository ini.
2. Buat branch fitur baru (`git checkout -b fitur-keren`).
3. Commit perubahan Anda (`git commit -m 'Menambahkan fitur keren'`).
4. Push ke branch Anda (`git push origin fitur-keren`).
5. Buat **Pull Request**.

---

## 📄 Lisensi (License)

Proyek ini dilisensikan di bawah lisensi **MIT License** — Anda bebas menggunakan, memodifikasi, dan mendistribusikannya.

---

<p align="center">
  Dibuat dengan ❤️ oleh <b>Andar</b>
</p>
