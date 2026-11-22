# Mochy · Dengarkan bareng (relay server)

Server kecil untuk fitur **dengarkan bareng**. Host membuat room dan mendapat kode 6 karakter; pendengar memasukkan kode itu dan mendengar lagu yang sama, sinkron. Server hanya meneruskan ID lagu, judul, dan posisi putar. Tidak ada audio dan tidak ada yang disimpan ke disk. Room hanya ada di memori.

## Menjalankan

```bash
cd server/listen-together
npm install
PORT=8080 npm start
```

Server mendengarkan di `ws://<host>:8080/ws` (path bisa diubah lewat `WS_PATH`).

## Menghubungkan aplikasi

Buka `app/src/main/kotlin/com/mochyy/mochymusic/together/ListenTogetherClient.kt` dan ubah `SERVER_URL` ke alamat server Anda:

```kotlin
const val SERVER_URL = "wss://alamat-anda.example.com/ws"
```

**Gunakan `wss://` untuk produksi.** Android memblokir koneksi `ws://` biasa secara default. Letakkan server di belakang reverse proxy dengan TLS (Caddy, Nginx, atau layanan hosting yang menyediakan HTTPS).

## Uji cepat

```bash
PORT=8799 node server.js &
node -e "const ws=new WebSocket('ws://127.0.0.1:8799/ws');ws.onopen=()=>ws.send(JSON.stringify({type:'create'}));ws.onmessage=e=>{console.log(e.data);process.exit(0)}"
```

Balasan `room` dengan kode 6 karakter menandakan server berjalan.

## Protokol

| Arah | Pesan | Keterangan |
|---|---|---|
| klien → server | `{"type":"create"}` | membuat room, host |
| klien → server | `{"type":"join","code":"ABC123"}` | bergabung sebagai pendengar |
| klien → server | `{"type":"state","track":{...},"positionMs":0,"playing":true,"sentAt":0}` | hanya dari host |
| klien → server | `{"type":"request_state"}` | pendengar minta posisi terkini |
| klien → server | `{"type":"leave"}` | keluar dari room |
| server → klien | `room`, `members`, `state`, `request_state`, `closed`, `error` | balasan dan siaran |

Batasan yang diterapkan server: pesan maksimal 4 KB, host mengirim status paling cepat tiap 400 ms, room dihapus 60 detik setelah pendengar terakhir keluar, sehingga host bisa kembali ke room yang sama sebentar, dan field track yang tidak dikenal dibuang.
