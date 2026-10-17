package com.makinistatama.atama;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.makinistatama.Tarayici;
import com.makinistatama.TestVeritabani;
import com.makinistatama.ortak.ZamanAyari;

/**
 * Refaktör (AtamaServisi bölünmesi, A0) karakterizasyonu: davranışı DEĞİŞTİRMEZ, yalnız bugünkü davranışı kilitler. Üç boşluk: (1) takas onayının denetim kaydı (2 x ATAMA_DEGISTIR, ortak
 * {@code takas} kimliği, eski/yeni, neden, sıra); (2) anlık görüntü JSON'unun TAM dizesi (ata, uyarılı ata, değiştir, yolcu, otomatik, otomatik yolculuk, takas) altın dosyada; (3) {@code atama_olayi}
 * içerik ve sırası (değiştir, kaldır, takas). Altın dosyalar: {@code golden/atama-denetim-*.json}. Bilinçli yenileme: {@code -Dgolden.yaz=true}.
 * <p>
 * Not: aynı işlem içindeki tablolar arası yazım sırası (olay -> kayıt) kimlikle gözlenemez; gözlenebilen sıralar (aynı tablodaki satır sırası, kimlikler) altın dosyada ORDER BY id ile kilitlidir.
 */
class AtamaDenetimKarakterizasyonTest extends TestVeritabani {

    static final LocalDate DUN = LocalDate.of(2026, 10, 9);

    AtamaVeri v;

    @BeforeEach
    void kur() {
        v = new AtamaVeri(jdbc);
    }

    private List<Map<String, Object>> kayitlar(String turler) {
        return jdbc.queryForList("SELECT tur, varlik_tipi, varlik_id, makinist_id, sefer_id, kullanici_adi, eski, yeni, neden, neden_notu, ayrinti FROM degisiklik_kaydi WHERE tur IN ("
                + turler + ") ORDER BY id");
    }

    private List<Map<String, Object>> olaylar() {
        return jdbc.queryForList("SELECT makinist_id, sefer_id, tur, ozet, neden_etiketi, kapatildi_zaman FROM atama_olayi ORDER BY id");
    }

    private static Map<String, Object> altin(List<Map<String, Object>> kayitlar, List<Map<String, Object>> olaylar) {
        Map<String, Object> sonuc = new LinkedHashMap<>();
        sonuc.put("kayitlar", kayitlar);
        sonuc.put("olaylar", olaylar);
        return sonuc;
    }

    @Test
    void manuelAtama_ata_uyarili_degistir_yolcu_kaldir_anlikGoruntuVeOlaylar() throws Exception {
        long d1 = v.makinist("D001");
        long d2 = v.makinist("D002");
        long d3 = v.makinist("D003");
        long d4 = v.makinist("D004");
        long s1 = v.sefer("A1", LocalDate.of(2026, 10, 12), "08:00", "10:00");
        long gecmis = v.sefer("G1", DUN, "08:00", "10:00");
        long yolculuk = v.sefer("Y1", LocalDate.of(2026, 10, 13), "08:00", "09:30");
        Tarayici t = atamaci();

        t.post("/api/atama/sefer/" + s1, AtamaVeri.ataGovdesi(d1)).andExpect(status().isCreated());
        t.post("/api/atama/sefer/" + s1, "{\"makinistId\":" + d2 + ",\"neden\":\"IZIN\",\"nedenNotu\":\"aile\"}").andExpect(status().isOk());
        t.post("/api/atama/sefer/" + gecmis, "{\"makinistId\":" + d3 + ",\"gerekce\":\"kayıt düzeltmesi\"}").andExpect(status().isCreated());
        t.post("/api/atama/sefer/" + yolculuk, "{\"makinistId\":" + d4 + ",\"rol\":\"YOLCU\"}").andExpect(status().isCreated());
        t.delete("/api/atama/sefer/" + s1 + "?rol=ASIL&neden=HASTALIK&nedenNotu=ates").andExpect(status().is2xxSuccessful());

        List<Map<String, Object>> kayit = kayitlar("'ATAMA', 'ATAMA_DEGISTIR', 'ATAMA_KALDIR'");
        assertThat(kayit).extracting(k -> k.get("tur")).containsExactly("ATAMA", "ATAMA_DEGISTIR", "ATAMA", "ATAMA", "ATAMA_KALDIR");
        List<Map<String, Object>> olay = olaylar();
        // BOŞLUK-3: değiştir (eski makinist D001, neden etiketi, not YOK) ve kaldır (D002, HASTALIK) olayları; sıra = yazım sırası
        assertThat(olay).extracting(o -> o.get("tur")).containsExactly("DEGISTI_BASKASINA", "KALDIRILDI");
        assertThat(olay).extracting(o -> o.get("makinist_id")).containsExactly(d1, d2);
        assertThat(olay).extracting(o -> o.get("neden_etiketi")).containsExactly("IZIN", "HASTALIK");
        assertThat(olay).extracting(o -> o.get("kapatildi_zaman")).containsOnlyNulls();
        AltinDosya.kontrol("atama-denetim-manuel", altin(kayit, olay));
    }

    @Test
    void otomatikAtama_asilVeYolculuk_anlikGoruntu() throws Exception {
        v.makinist("D007");
        v.makinist("D009");
        long pasif = v.makinist("D005", true, "PASIF");
        long e1 = v.sefer("E1", LocalDate.of(2026, 10, 14), "08:00", "09:30"); // Ankara -> Eskişehir
        v.sefer("S2", LocalDate.of(2026, 10, 14), "10:30", "15:00", v.eskisehir, v.ankara);
        v.seferEv("S3", LocalDate.of(2026, 10, 16), "08:00", "11:00");
        v.atama(e1, pasif, "ASIL");
        Tarayici t = atamaci();

        t.post("/api/atama/otomatik", "{\"bas\":\"2026-10-14\",\"bit\":\"2026-10-14\",\"uygula\":true,\"yolculuk\":true}").andExpect(status().isOk());
        t.post("/api/atama/otomatik", "{\"bas\":\"2026-10-16\",\"bit\":\"2026-10-16\",\"uygula\":true}").andExpect(status().isOk());

        List<Map<String, Object>> kayit = kayitlar("'ATAMA', 'OTOMATIK_ATAMA'");
        assertThat(kayit).extracting(k -> k.get("tur")).containsExactly("ATAMA", "ATAMA", "OTOMATIK_ATAMA", "ATAMA", "OTOMATIK_ATAMA");
        assertThat(olaylar()).isEmpty();
        AltinDosya.kontrol("atama-denetim-otomatik", altin(kayit, olaylar()));
    }

    private static final DateTimeFormatter GUN_AY = DateTimeFormatter.ofPattern("dd.MM");

    /** Olay özetindeki gerçek-bugüne göre değişen gün/ay, sabit yer tutucuya çevrilir (takas testleri gerçek saati kullanır). */
    private static List<Map<String, Object>> tarihsiz(List<Map<String, Object>> olaylar, LocalDate... gunler) {
        return olaylar.stream().map(o -> {
            Map<String, Object> kopya = new LinkedHashMap<>(o);
            String ozet = (String) o.get("ozet");
            for (int i = 0; i < gunler.length; i++) {
                ozet = ozet.replace(gunler[i].format(GUN_AY), "GUN" + i);
            }
            kopya.put("ozet", ozet);
            return kopya;
        }).toList();
    }

    @Test
    void takasOnayi_ikiAtamaDegistirKaydi_ortakTakasKimligi_olaylarVeAnlikGoruntu() throws Exception {
        long a = v.makinist("D001");
        long b = v.makinist("D002");
        LocalDate gunA = LocalDate.now(ZamanAyari.BOLGE).plusDays(3);
        LocalDate gunB = LocalDate.now(ZamanAyari.BOLGE).plusDays(4);
        long sa = v.seferEv("TA", gunA, "08:00", "09:00");
        long sb = v.seferEv("TB", gunB, "08:00", "09:00");
        long ataA = v.atama(sa, a, "ASIL");
        long ataB = v.atama(sb, b, "YARDIMCI");
        Tarayici ta = makinistGirisli(a, "D001");
        Tarayici tb = makinistGirisli(b, "D002");

        ta.post("/api/benim/takas", "{\"atamaId\":" + ataA + ",\"hedefAtamaId\":" + ataB + ",\"neden\":\"IZIN\",\"nedenNotu\":\"aile\"}").andExpect(status().isCreated());
        long talep = jdbc.queryForObject("SELECT id FROM takas_talebi", Long.class);
        tb.post("/api/benim/takas/" + talep + "/kabul", "").andExpect(status().isOk());
        atamaci().post("/api/takas/" + talep + "/onay", "{}").andExpect(status().isOk());

        List<Map<String, Object>> kayit = kayitlar("'ATAMA_DEGISTIR'");
        // BOŞLUK-1: iki kayıt, bu sırayla: önce A (yeni satır B'nin seferinde), sonra B; eski/yeni sicil ROL:SİCİL; ortak takas kimliği; neden + not
        assertThat(kayit).hasSize(2);
        assertThat(kayit.get(0)).containsEntry("makinist_id", a).containsEntry("sefer_id", sb).containsEntry("eski", "YARDIMCI:D002").containsEntry("yeni", "YARDIMCI:D001")
                .containsEntry("neden", "IZIN").containsEntry("neden_notu", "aile").containsEntry("kullanici_adi", "Ata Maci").containsEntry("varlik_tipi", "ATAMA");
        assertThat(kayit.get(1)).containsEntry("makinist_id", b).containsEntry("sefer_id", sa).containsEntry("eski", "ASIL:D001").containsEntry("yeni", "ASIL:D002")
                .containsEntry("neden", "IZIN").containsEntry("neden_notu", "aile").containsEntry("kullanici_adi", "Ata Maci").containsEntry("varlik_tipi", "ATAMA");
        assertThat((Long) kayit.get(0).get("varlik_id")).isLessThan((Long) kayit.get(1).get("varlik_id"));
        assertThat((String) kayit.get(0).get("ayrinti")).contains("\"takas\":" + talep);
        assertThat((String) kayit.get(1).get("ayrinti")).contains("\"takas\":" + talep);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM degisiklik_kaydi WHERE tur = 'TAKAS_ONAY'", Integer.class)).isEqualTo(1);

        // BOŞLUK-3: iki olay, A önce (açanın nedeni), B sonra (neden YOK)
        List<Map<String, Object>> olay = olaylar();
        assertThat(olay).extracting(o -> o.get("makinist_id")).containsExactly(a, b);
        assertThat(olay).extracting(o -> o.get("tur")).containsOnly("DEGISTI_BASKASINA");
        assertThat(olay).extracting(o -> o.get("neden_etiketi")).containsExactly("IZIN", null);
        AltinDosya.kontrol("atama-denetim-takas", altin(kayit, tarihsiz(olay, gunA, gunB)));
    }
}
