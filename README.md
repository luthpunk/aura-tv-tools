# Aura TV Tools

<p align="center">
  <img src="app/src/main/res/mipmap-anydpi/ic_launcher.xml" width="96" alt="Aura TV Tools Logo" />
</p>

<p align="center">
  <b>Aplikasi remote kontrol TV & STB Android modern dengan Live Screen Mirroring, Auto-Discovery Subnet instan, Debloat (Detox), Optimasi (Boost), dan penginstal resmi Aura TV Home.</b>
</p>

<p align="center">
  <a href="https://github.com/luthpunk/aura-tv-tools/releases/latest"><b>⬇ Download Aura TV Tools APK Terbaru</b></a>
</p>

---

## 🌟 Fitur Utama

- 📺 **Live Screen Mirroring & Kontrol Realtime**
  - Tampilkan layar Android TV / STB secara langsung di ponsel Anda.
  - Mendukung dua mode: **⚡ Rapid Snapshot** (ringan dan 100% kompatibel di semua STB) serta **📹 Hardware H.264 Video Decoder** (720p / 480p low-latency).
  - Sidebar remote kontrol bertema *Matte Dark* terpadu dengan D-Pad circular, feedback haptik getar, dan tombol navigasi lengkap.
  - Tangkapan layar (Screenshot) instan yang otomatis tersimpan ke folder `Pictures/AuraTV` galeri ponsel.
  - Menggunakan stream multiplexing ADB langsung dari sesi aktif sehingga tidak ada delay koneksi ganda atau dialog otorisasi berulang.

- 🔍 **Pencarian Cepat (Subnet Auto-Discovery)**
  - Pemindaian subnet `/24` paralel pada port ADB 5555 dalam waktu ~1-1.5 detik.
  - Langsung mendeteksi STB IndiHome, ZTE, Fiberhome, Xiaomi, dan Android TV lainnya di jaringan Wi-Fi lokal tanpa perlu mengetik alamat IP secara manual.
  - Mendukung penemuan otomatis via mDNS (`_androidtvremote2._tcp.` & `_adb-tls-connect._tcp.`).

- 🏠 **Integrasi Aura TV Home (Tab Boost)**
  - Mengunduh dan memasang [Aura TV Home](https://github.com/luthpunk/aura-tv-home) langsung ke TV Anda melalui streaming ADB.
  - Otomatis memberikan izin secure settings (`WRITE_SECURE_SETTINGS`) dan menjadikannya beranda utama TV.
  - Tersedia opsi untuk mengembalikan launcher bawaan TV kapan saja dengan aman.

- 🎮 **Remote Control Modern**
  - D-Pad melingkar ergonomis dengan tombol tengah OK beraksen *Mint Accent*.
  - Tombol lengkap: Back, Home, Menu, Volume +, Volume −, Mute, Power, dan Play/Pause.
  - Fitur pengetikan teks langsung dari keyboard ponsel ke TV.

- 🧹 **Detox (Debloat Otomatis)**
  - Matikan bloatware, iklan sponsor, dan aplikasi bawaan yang memperlambat TV hanya dengan satu ketukan.
  - Aplikasi dinonaktifkan (`pm disable-user`), bukan dihapus permanen, sehingga aman dan dapat dipulihkan kapan saja melalui tab Riwayat.

- ⚡ **Boost & Optimasi Sistem**
  - Pemantauan penggunaan RAM dan penyimpanan TV secara realtime.
  - Sekali ketuk untuk membersihkan aplikasi latar belakang dan melegakan cache.
  - Opsi mematikan animasi antarmuka TV agar menu terbuka instan tanpa jeda.

- 📦 **Manajer Aplikasi TV**
  - Menampilkan semua aplikasi TV dengan nama dan ikon aslinya.
  - Buka, paksa berhenti, hapus data aplikasi, atau pasang file APK dari penyimpanan ponsel langsung ke TV.

---

## 📱 Panduan Pemasangan

1. Unduh file `aura-tv-tools.apk` dari [GitHub Releases](https://github.com/luthpunk/aura-tv-tools/releases/latest).
2. Pasang pada ponsel Android Anda (Android 12+ / SDK 31+ didukung).

---

## ⚙️ Persiapan TV / STB (Hanya Sekali)

1. Di TV / STB Anda, buka **Pengaturan (Settings) › Preferensi Perangkat › Tentang (About)**.
2. Ketuk baris **Build** sebanyak 7 kali hingga muncul notifikasi bahwa Anda telah menjadi pengembang (*Developer*).
3. Buka **Opsi Pengembang (Developer Options)** dan aktifkan **USB Debugging** / **Debugging Nirkabel (Wireless Debugging)**.
4. Hubungkan ponsel dan TV ke jaringan Wi-Fi yang sama.

---

## 🚀 Cara Menghubungkan

1. Buka **Aura TV Tools** di ponsel Anda.
2. TV Anda akan otomatis muncul di bagian **FOUND ON YOUR WI-FI** dalam hitungan detik.
3. Ketuk nama TV untuk menghubungkan:
   - Jika muncul dialog **"Izinkan debugging USB?"** di layar TV, centang **Selalu izinkan dari komputer ini** lalu pilih **Izinkan (Allow)**.
   - Untuk TV dengan *Wireless Debugging Pair Code* (Android 11+), pilih opsi **Pair** dan masukkan 6 digit kode yang tampil di layar TV.
4. Setelah terhubung pertama kali, aplikasi akan mengingat TV Anda dan dapat terhubung kembali dalam satu ketukan.

---

## 🛠️ Build dari Kode Sumber

Membutuhkan JDK 17+ dan Android SDK (compileSdk 37, targetSdk 36):

```bash
git clone https://github.com/luthpunk/aura-tv-tools.git
cd aura-tv-tools
./gradlew assembleRelease
```

Hasil build APK akan berada di folder `app/build/outputs/apk/release/`.

---

## 📄 Lisensi & Kredit

- Berdasarkan basis kode open-source [KonnectTV](https://github.com/yashoncode/konnect-tv) oleh Yashwynth, disesuaikan dan dikembangkan oleh [luthpunk](https://github.com/luthpunk).
- Menggunakan [libadb-android](https://github.com/MuntashirAkon/libadb-android) untuk protokol komunikasi ADB.
- Tampilan frosted glass didukung oleh [Haze](https://github.com/chrisbanes/haze).
- Lisensi di bawah [MIT License](LICENSE).
