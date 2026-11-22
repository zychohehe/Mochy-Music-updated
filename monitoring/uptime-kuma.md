# Pemantauan server dengarkan bareng

Server menyediakan `GET /health`:

```json
{ "rooms": 3, "connections": 7, "uptimeSec": 86400 }
```

Respons `200` berarti server hidup. Jumlah `rooms` dan `connections` berguna untuk melihat pemakaian.

## Opsi 1: Uptime Kuma

1. Tambah monitor baru, tipe **HTTP(s) - Keyword**.
2. URL: `https://alamat-server-anda.example.com/health`
3. Keyword: `"uptimeSec"`
4. Interval: 60 detik, retries: 2.
5. Di **Notifications** pilih Telegram, Discord, atau email, lalu centang monitor ini.

Opsional: monitor kedua tipe **HTTP(s) - Json Query** dengan query `rooms` untuk memicu peringatan bila jumlah room tiba-tiba tinggi.

## Opsi 2: cron + curl

Simpan sebagai `/usr/local/bin/kiyo-health.sh`, lalu `chmod +x`:

```bash
#!/bin/sh
URL="https://alamat-server-anda.example.com/health"
WEBHOOK="https://discord.com/api/webhooks/ID/TOKEN"   # atau URL webhook lain

if ! curl -fsS --max-time 10 "$URL" | grep -q '"uptimeSec"'; then
  curl -fsS -H "Content-Type: application/json" \
    -d '{"content":"Server dengarkan bareng Kiyo tidak merespons /health"}' "$WEBHOOK"
fi
```

Crontab (tiap menit):

```
* * * * * /usr/local/bin/kiyo-health.sh
```

Skrip ini mengirim notifikasi tiap menit selama server mati. Tambahkan file penanda bila ingin satu pesan saja.

## Log

Server menulis log JSON satu baris ke stdout. Dengan Docker: `docker logs -f <container> | jq .`. Event: `room_created`, `room_closed`, `host_migrated`, `rate_limited`, `connection_dropped`, `unhandled_error`. Untuk peringatan, cari `"event":"unhandled_error"`.
