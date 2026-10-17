package com.makinistatama.takas;

import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

import com.makinistatama.Tarayici;
import com.makinistatama.TestVeritabani;
import com.makinistatama.atama.AtamaVeri;
import com.makinistatama.ortak.ZamanAyari;

/**
 * Takas testlerinin ortak ön verisi. Seferler Ankara→Ankara'dır (makinist evinde biter: konum uyuşmazlığı çıkmaz); günler GERÇEK bugüne göre (takas süre sınırı gerçek {@code Clock} kullanır,
 * kural motoru test için sabitlenmiş {@code makinist.atama.simdi} kullanır). İlk makinist kimlikleri 1, 2, ... (TRUNCATE RESTART IDENTITY).
 */
abstract class TakasTabani extends TestVeritabani {

    static final DateTimeFormatter SAAT = DateTimeFormatter.ofPattern("HH:mm");

    AtamaVeri v;

    static LocalDate gun(int sonra) {
        return LocalDate.now(ZamanAyari.BOLGE).plusDays(sonra);
    }

    /** Hesaplı (ilk parolası belirlenmiş) makinist; döner: makinist kimliği. Giriş {@link #giris}. */
    long hesapli(String sicil) throws Exception {
        if (v == null) {
            v = new AtamaVeri(jdbc);
        }
        long m = v.makinist(sicil);
        kullanicilar.makinistHesabiAc(m, sicil, "Demo Makinist " + sicil, Tarayici.PAROLA);
        jdbc.update("UPDATE kullanici SET parola_degistirmeli = FALSE WHERE makinist_id = ?", m);
        return m;
    }

    Tarayici giris(String sicil) throws Exception {
        return Tarayici.girisYapmis(mvc, sicil.toLowerCase(java.util.Locale.ROOT), Tarayici.PAROLA);
    }

    long sefer(String tren, LocalDate gun, String kalkis, String varis) {
        return v.seferEv(tren, gun, kalkis, varis);
    }

    long atama(long sefer, long makinist, String rol) {
        return v.atama(sefer, makinist, rol);
    }

    /** Şimdiden {@code saatSonra} saat + {@code dk} dakika sonra kalkan 1 saatlik sefer (gerçek saate göre; dakika hassasiyeti). */
    long seferSimdiden(String tren, long saatSonra, long dk) {
        ZonedDateTime k = ZonedDateTime.now(ZamanAyari.BOLGE).plusHours(saatSonra).plusMinutes(dk);
        return sefer(tren, k.toLocalDate(), k.format(SAAT), k.plusHours(1).format(SAAT));
    }

    static String acGovdesi(long atamaId, Long hedef, String neden, String not) {
        return "{\"atamaId\":" + atamaId + (hedef == null ? "" : ",\"hedefAtamaId\":" + hedef) + ",\"neden\":\"" + neden + "\"" + (not == null ? "" : ",\"nedenNotu\":\"" + not + "\"") + "}";
    }

    /** A (D001, SA'da ASIL, 3 gün sonra 08:00) ve B (D002, SB'de YARDIMCI, 4 gün sonra 08:00), ikisi de hesaplı. Atama kimlikleri ayrı alanlarda. */
    record Sahne(long a, long b, long sa, long sb, long ataA, long ataB) {
    }

    Sahne sahne() throws Exception {
        long a = hesapli("D001");
        long b = hesapli("D002");
        long sa = sefer("TA", gun(3), "08:00", "09:00");
        long sb = sefer("TB", gun(4), "08:00", "09:00");
        return new Sahne(a, b, sa, sb, atama(sa, a, "ASIL"), atama(sb, b, "YARDIMCI"));
    }

    /** Yanıt gövdesindeki sayısal {@code id}. */
    static long id(org.springframework.test.web.servlet.ResultActions r) throws Exception {
        return ((Number) com.jayway.jsonpath.JsonPath.read(r.andReturn().getResponse().getContentAsString(), "$.id")).longValue();
    }

    int sayi(String sql, Object... arg) {
        return jdbc.queryForObject(sql, Integer.class, arg);
    }
}
