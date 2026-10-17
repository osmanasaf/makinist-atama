package com.makinistatama.atama;

import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;

import org.springframework.jdbc.core.JdbcTemplate;

import com.makinistatama.TestTanimlari;

/** Atama testlerinin ön verisi (doğrudan SQL): Ankara/Eskişehir garları, iki hat, tek araç tipi; belgeleri geçerli makinistler; seferler. */
public final class AtamaVeri {

    public static final ZoneId ISTANBUL = ZoneId.of("Europe/Istanbul");

    final JdbcTemplate jdbc;
    public final long ankara;
    public final long eskisehir;
    public final long hat;
    public final long hat2;
    public final long yht;

    public AtamaVeri(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
        ankara = TestTanimlari.gar(jdbc, "Ankara");
        eskisehir = TestTanimlari.gar(jdbc, "Eskişehir");
        hat = TestTanimlari.hat(jdbc, "Ankara–Eskişehir");
        hat2 = TestTanimlari.hat(jdbc, "Ankara–Konya");
        yht = TestTanimlari.aracTipi(jdbc, "YHT");
    }

    /** Belgeleri 2030'a kadar geçerli AKTIF makinist; bröve: YHT + {@link #hat} (sınav 2024-01-01, son sürüş 2026-09-01). */
    public long makinist(String sicil) {
        return makinist(sicil, true, "AKTIF");
    }

    public long makinist(String sicil, boolean hatYetkisi, String durum) {
        long id = jdbc.queryForObject("""
                INSERT INTO makinist (sicil, ad_soyad, bagli_gar_id, durum, ehliyet_bitis, saglik_bitis, psikoteknik_bitis, son_surus)
                VALUES (?, ?, ?, ?, '2030-01-01', '2030-01-01', '2030-01-01', '2026-09-01') RETURNING id""", Long.class, sicil, "Demo Makinist " + sicil,
                ankara, durum);
        jdbc.update("INSERT INTO brove_arac_tipi (makinist_id, arac_tipi_id, sinav_tarihi) VALUES (?, ?, '2024-01-01')", id, yht);
        if (hatYetkisi) {
            jdbc.update("INSERT INTO brove_hat (makinist_id, hat_id, sinav_tarihi, son_surus) VALUES (?, ?, '2024-01-01', '2026-09-01')", id, hat);
        }
        return id;
    }

    /** Belgeleri 2030'a kadar geçerli AKTIF kondüktör (F3b): bröve yok. */
    public long konduktor(String sicil) {
        return jdbc.queryForObject("""
                INSERT INTO makinist (sicil, ad_soyad, bagli_gar_id, durum, tur, ehliyet_bitis, saglik_bitis, psikoteknik_bitis)
                VALUES (?, ?, ?, 'AKTIF', 'KONDUKTOR', '2030-01-01', '2030-01-01', '2030-01-01') RETURNING id""", Long.class, sicil, "Demo Kondüktör " + sicil, ankara);
    }

    public long sefer(String tren, LocalDate gun, String kalkis, String varis) {
        return sefer(tren, gun, kalkis, varis, ankara, eskisehir);
    }

    /** Varış saati kalkıştan küçük/eşitse ertesi gün. */
    public long sefer(String tren, LocalDate gun, String kalkis, String varis, long kalkisGar, long varisGar) {
        LocalTime k = LocalTime.parse(kalkis);
        LocalTime v = LocalTime.parse(varis);
        var kalkisAn = gun.atTime(k).atZone(ISTANBUL).toInstant();
        var varisAn = (v.isAfter(k) ? gun : gun.plusDays(1)).atTime(v).atZone(ISTANBUL).toInstant();
        return jdbc.queryForObject("""
                INSERT INTO sefer (tren_no, tarih, kalkis_gar_id, varis_gar_id, planlanan_kalkis, planlanan_varis, arac_tipi_id, hat_id, km)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, 250) RETURNING id""", Long.class, tren, gun, kalkisGar, varisGar, Timestamp.from(kalkisAn),
                Timestamp.from(varisAn), yht, hat);
    }

    /** Ankara -> Ankara: makinist evinde biter (konum uyuşmazlığı K-20 çıkmaz). */
    public long seferEv(String tren, LocalDate gun, String kalkis, String varis) {
        return sefer(tren, gun, kalkis, varis, ankara, ankara);
    }

    public long atama(long seferId, long makinistId, String rol) {
        return jdbc.queryForObject("INSERT INTO atama (sefer_id, makinist_id, rol, atayan, kalkis_gecikme_dk) "
                + "VALUES (?, ?, ?, 'test', (SELECT kalkis_gecikme_dk FROM sefer WHERE id = ?)) RETURNING id", Long.class, seferId, makinistId, rol, seferId); // API gibi: atama anındaki gecikme (OK-6)
    }

    /** Doğrudan SQL ile PLANLI serbest intikal (kural kontrolsüz yazma); varış saati kalkıştan küçük/eşitse ertesi gün. Döner: intikal kimliği. */
    public long intikal(long makinistId, long kalkisGar, long varisGar, LocalDate gun, String kalkis, String varis) {
        LocalTime k = LocalTime.parse(kalkis);
        LocalTime v = LocalTime.parse(varis);
        var kalkisAn = gun.atTime(k).atZone(ISTANBUL).toInstant();
        var varisAn = (v.isAfter(k) ? gun : gun.plusDays(1)).atTime(v).atZone(ISTANBUL).toInstant();
        return jdbc.queryForObject("""
                INSERT INTO intikal (makinist_id, kalkis_gar_id, varis_gar_id, kalkis, varis, arac_turu) VALUES (?, ?, ?, ?, ?, 'SERVIS') RETURNING id""", Long.class, makinistId,
                kalkisGar, varisGar, Timestamp.from(kalkisAn), Timestamp.from(varisAn));
    }

    public static String ataGovdesi(long makinistId) {
        return "{\"makinistId\":" + makinistId + "}";
    }
}
