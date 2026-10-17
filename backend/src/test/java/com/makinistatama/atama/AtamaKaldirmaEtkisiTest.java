package com.makinistatama.atama;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.makinistatama.Tarayici;
import com.makinistatama.TestVeritabani;
import com.makinistatama.sefer.SeferDto;
import com.makinistatama.sefer.SeferServisi;

/**
 * Faz 1 MK-5 bulgusu (F2b review): kaldırma ve ekleme sırasında BAŞKA atamanın kırmızı olması.
 * Karar: kaldırma etkisi kuralın gereğidir (gece sınıfı nöbet oranına bağlı; seyrelten görev çıkınca nöbet "gece" olur, K-18 ardışık gece haftası değişir), hata değil;
 * atamacıya kaldırma anında bildirilir. Ekleme etkisi temiz durumda YOKTUR; yalnız önceden var olan ihlalde K-18 işareti ilk gece nöbetinden bir sonrakine kayar.
 */
class AtamaKaldirmaEtkisiTest extends TestVeritabani {

    @Autowired AtamaRepository depo;
    @Autowired SeferServisi seferler;
    @Autowired KirmiziAtamaHesabi kirmizi;

    AtamaVeri v;
    Tarayici t;
    long m;

    @BeforeEach
    void kur() throws Exception {
        v = new AtamaVeri(jdbc);
        t = atamaci();
        m = v.makinist("D001");
    }

    private long ev(String ad, String gun, String kalkis, String varis) {
        return v.seferEv(ad, LocalDate.parse(gun), kalkis, varis);
    }

    private int kirmiziAtama() throws Exception {
        return json(t.get("/api/atama/kirmizi-sayisi?bas=2026-10-05&bit=2026-10-30").andReturn().getResponse().getContentAsString()).get("kirmiziAtama").asInt();
    }

    @Test
    void kaldir_seyrelticiGorevCikinca_digerAtamaYeniKirmizi_200veEtkilenenListelenir() throws Exception {
        long kopru = ev("KOPRU", "2026-10-17", "14:00", "21:00"); // gündüz görevi: gece oranını seyreltir (bu nöbet gündüz sayılır)
        long gece1 = ev("GECE1", "2026-10-17", "22:00", "23:00"); // aynı nöbet (1 s boşluk): toplam 8 s, gece 2 s = gündüz nöbeti
        long gece2 = ev("GECE2", "2026-10-20", "22:00", "23:00"); // sonraki hafta gece nöbeti
        v.atama(kopru, m, "ASIL");
        v.atama(gece1, m, "YARDIMCI");
        long a2 = v.atama(gece2, m, "ASIL");
        assertThat(kirmiziAtama()).isZero();

        t.delete("/api/atama/sefer/" + kopru + "?rol=ASIL&neden=IZIN").andExpect(status().isOk()).andExpect(jsonPath("$.yeniKirmizi", hasSize(1)))
                .andExpect(jsonPath("$.yeniKirmizi[0].atamaId").value(a2)).andExpect(jsonPath("$.yeniKirmizi[0].trenNo").value("GECE2"))
                .andExpect(jsonPath("$.yeniKirmizi[0].nedenler[0]").value(org.hamcrest.Matchers.containsString("ardışık gece haftası")));
        assertThat(kirmiziAtama()).isEqualTo(1); // yanıttaki uyarı gün listesiyle tutarlı
    }

    /** MK-98 (R-5): etki ufku kural penceresinden türetilir; ard_hafta_max=6 iken 38 gün uzaktaki 7. gece haftası da kaldırma sonrası yeni kırmızı olarak bildirilir (eski sabit 35 gün görmezdi). */
    @Test
    void kaldir_ufukKuralPenceresindenGelir_38GunUzaktakiGeceHaftasiBildirilir() throws Exception {
        yonetici().put("/api/parametre/gece.ard_hafta_max", "{\"deger\":\"6\",\"neden\":\"deneme\"}").andExpect(status().isOk());
        long kopru = ev("KOPRU", "2026-10-17", "14:00", "21:00");
        long gece1 = ev("GECE1", "2026-10-17", "22:00", "23:00");
        v.atama(kopru, m, "ASIL");
        v.atama(gece1, m, "YARDIMCI");
        long sonAtama = 0;
        for (int hafta = 0; hafta < 6; hafta++) { // 20 Eki, 27 Eki, ..., 24 Kas: ardışık 6 gece haftası + dilüe edilmiş ilk hafta
            sonAtama = v.atama(ev("G" + hafta, LocalDate.of(2026, 10, 20).plusDays(7L * hafta).toString(), "22:00", "23:00"), m, "ASIL");
        }
        assertThat(json(t.get("/api/atama/kirmizi-sayisi?bas=2026-10-05&bit=2026-11-30").andReturn().getResponse().getContentAsString()).get("kirmiziAtama").asInt()).isZero();

        t.delete("/api/atama/sefer/" + kopru + "?rol=ASIL&neden=IZIN").andExpect(status().isOk()).andExpect(jsonPath("$.yeniKirmizi", hasSize(1)))
                .andExpect(jsonPath("$.yeniKirmizi[0].atamaId").value(sonAtama)).andExpect(jsonPath("$.yeniKirmizi[0].trenNo").value("G5"));
    }

    /**
     * Review W3: K-07/K-08 ay ölçeklidir (18 ay). Son sürüş 2026-01-01; R (2026-10-17) aynı hatta A'yı (2028-03-01) 18 ay kuralından kurtarıyor. R kaldırılınca A yeni kırmızı olur;
     * ~15 günlük pencere bunu görmezdi.
     */
    @Test
    void kaldir_K07_ayOlcekliSonrakiSurusBildirilir() throws Exception {
        jdbc.update("UPDATE makinist SET son_surus = '2026-01-01' WHERE id = ?", m);
        jdbc.update("UPDATE brove_hat SET son_surus = '2026-01-01' WHERE makinist_id = ?", m);
        long r = ev("R", "2026-10-17", "10:00", "12:00");
        long a = ev("A", "2028-03-01", "10:00", "12:00");
        v.atama(r, m, "ASIL");
        long aId = v.atama(a, m, "ASIL");
        assertThat(json(t.get("/api/atama/kirmizi-sayisi?bas=2028-03-01&bit=2028-03-01").andReturn().getResponse().getContentAsString()).get("kirmiziAtama").asInt()).isZero();

        t.delete("/api/atama/sefer/" + r + "?rol=ASIL&neden=IZIN").andExpect(status().isOk()).andExpect(jsonPath("$.yeniKirmizi", hasSize(1)))
                .andExpect(jsonPath("$.yeniKirmizi[0].atamaId").value(aId)).andExpect(jsonPath("$.yeniKirmizi[0].nedenler[0]").value(org.hamcrest.Matchers.containsString("Hat son sürüşü")));
    }

    /** K-08 (hatlar arası genel son sürüş): R başka hatta, A bu hatta; K-07 gevşetilip yalnız K-08 sınanır. */
    @Test
    void kaldir_K08_ayOlcekliSonrakiSurusBildirilir() throws Exception {
        yonetici().put("/api/parametre/hat.ara_max_ay", "{\"deger\":\"120\",\"neden\":\"deneme\"}").andExpect(status().isOk());
        jdbc.update("UPDATE makinist SET son_surus = '2026-01-01' WHERE id = ?", m);
        jdbc.update("UPDATE brove_hat SET son_surus = '2026-01-01' WHERE makinist_id = ?", m);
        jdbc.update("INSERT INTO brove_hat (makinist_id, hat_id, sinav_tarihi, son_surus) VALUES (?, ?, '2024-01-01', '2026-01-01')", m, v.hat2);
        long r = ev("R", "2026-10-17", "10:00", "12:00");
        jdbc.update("UPDATE sefer SET hat_id = ? WHERE id = ?", v.hat2, r);
        long a = ev("A", "2028-03-01", "10:00", "12:00");
        v.atama(r, m, "ASIL");
        long aId = v.atama(a, m, "ASIL");
        assertThat(json(t.get("/api/atama/kirmizi-sayisi?bas=2028-03-01&bit=2028-03-01").andReturn().getResponse().getContentAsString()).get("kirmiziAtama").asInt()).isZero();

        t.delete("/api/atama/sefer/" + r + "?rol=ASIL&neden=IZIN").andExpect(status().isOk()).andExpect(jsonPath("$.yeniKirmizi", hasSize(1)))
                .andExpect(jsonPath("$.yeniKirmizi[0].atamaId").value(aId)).andExpect(jsonPath("$.yeniKirmizi[0].nedenler[0]").value(org.hamcrest.Matchers.containsString("Son sürüş")));
    }

    @Test
    void kaldir_etkisizseYine204() throws Exception {
        long s1 = ev("S1", "2026-10-14", "10:00", "12:00");
        v.atama(s1, m, "ASIL");
        t.delete("/api/atama/sefer/" + s1 + "?rol=ASIL&neden=IZIN").andExpect(status().isNoContent());
    }

    /** Ekleme tarafı: önceden K-18 ihlali olan (SQL ile yazılmış) takvimde aday gece görevi, sonraki nöbeti kendi nöbetine katar; ihlal aynı kalır, yalnız işaret kayar. */
    @Test
    void ekle_oncedenIhlalVarsaIsaretKayar_kirmiziSayisiArtmaz() throws Exception {
        v.atama(ev("A", "2026-10-05", "04:00", "05:40"), m, "ASIL"); // 1. hafta gece nöbeti
        v.atama(ev("B", "2026-10-12", "04:00", "05:40"), m, "ASIL"); // 2. hafta gece (zaten ihlal: 2. ardışık)
        v.atama(ev("C", "2026-10-13", "00:30", "05:30"), m, "ASIL");
        assertThat(kirmiziAtama()).isEqualTo(1);

        long aday = ev("ADAY", "2026-10-11", "20:30", "01:30"); // B ile aynı nöbet olur (boşluk 2,5 s); B'nin haftası 1. haftaya geçer
        t.post("/api/atama/sefer/" + aday, "{\"makinistId\":" + m + ",\"gerekce\":\"deneme\"}").andExpect(status().isCreated());
        assertThat(kirmiziAtama()).isEqualTo(1); // ihlal sürüyor (2 ardışık gece haftası), işaret C'ye kaydı
    }

    // ------------------------------------------------------------------ rastgele (temiz takvim)

    static final LocalDate BAS = LocalDate.of(2026, 10, 5);
    static final String[] SAATLER = { "00:30", "04:00", "05:00", "07:55", "10:00", "13:00", "18:00", "20:30", "22:00", "23:30" };
    static final int[] SURELER = { 60, 100, 180, 300, 420 };

    private Map<Long, Boolean> kirmiziHarita() {
        List<SeferDto> liste = seferler.aralikListesi(BAS.minusDays(2), BAS.plusDays(25));
        Map<Long, List<Atama>> am = depo.findBySeferIdIn(liste.stream().map(SeferDto::id).toList()).stream().collect(Collectors.groupingBy(Atama::seferId));
        Map<Long, Boolean> sonuc = new java.util.HashMap<>();
        kirmizi.hesapla(liste, am).forEach((id, s) -> sonuc.put(id, s.kirmizi()));
        return sonuc;
    }

    private static Set<Long> yeniKirmizilar(Map<Long, Boolean> once, Map<Long, Boolean> sonra) {
        Set<Long> yeni = new HashSet<>();
        sonra.forEach((id, k) -> {
            if (k && Boolean.FALSE.equals(once.get(id))) {
                yeni.add(id);
            }
        });
        return yeni;
    }

    /**
     * Yalnız API ile kurulan (kural dışı satırı olmayan) takvimde: kabul edilen her ekleme hiçbir atamayı yeni kırmızı yapmaz (MK-5, rastgele sefer dizisi, kabul/ret karışık);
     * her kaldırmada yanıt, gün listesi hesabındaki "yeni kırmızı" kümesiyle BİREBİR aynıdır (boşsa 204, doluysa 200).
     */
    @Test
    void temizTakvimde_eklemeYeniKirmiziYapmaz_kaldirmaYaniti_hesapla_tutarli() throws Exception {
        Random r = new Random(5);
        int kabul = 0;
        int etkili = 0;
        Map<Long, Boolean> hazir = null; // önceki işlemin "sonra" haritası: veri değişmediyse sonraki "önce" ile aynıdır (ret edilen POST atama satırı yazmaz)
        for (int tur = 0; tur < 100; tur++) {
            long mk = v.makinist("R" + tur);
            List<long[]> yazilan = new ArrayList<>(); // {seferId}
            for (int i = 0; i < 12; i++) {
                LocalDate gun = BAS.plusDays(r.nextInt(21));
                String kalkis = SAATLER[r.nextInt(SAATLER.length)];
                String varis = LocalTime.parse(kalkis).plusMinutes(SURELER[r.nextInt(SURELER.length)]).toString();
                long sefer = v.sefer("R" + tur + "_" + i, gun, kalkis, varis, r.nextInt(3) == 0 ? v.eskisehir : v.ankara, r.nextInt(3) == 0 ? v.eskisehir : v.ankara);
                Map<Long, Boolean> once = hazir != null ? hazir : kirmiziHarita();
                if (t.post("/api/atama/sefer/" + sefer, "{\"makinistId\":" + mk + ",\"gerekce\":\"deneme\"}").andReturn().getResponse().getStatus() != 201) {
                    continue;
                }
                kabul++;
                yazilan.add(new long[] { sefer });
                Map<Long, Boolean> sonra = kirmiziHarita();
                hazir = sonra;
                assertThat(yeniKirmizilar(once, sonra)).as("tur %d: kabul edilen ekleme başka atamayı yeni kırmızı yaptı", tur).isEmpty();
                assertThat(sonra.values()).as("tur %d: temiz takvimde kırmızı olmamalı", tur).doesNotContain(true);
            }
            for (int k = 0; k < 3 && !yazilan.isEmpty(); k++) { // her makinistten birkaç rastgele kaldırma
                long sil = yazilan.remove(r.nextInt(yazilan.size()))[0];
                Map<Long, Boolean> once = hazir != null ? hazir : kirmiziHarita();
                var yanit = t.delete("/api/atama/sefer/" + sil + "?rol=ASIL&neden=IZIN").andReturn().getResponse();
                Map<Long, Boolean> sonra = kirmiziHarita();
                hazir = sonra;
                Set<Long> beklenen = yeniKirmizilar(once, sonra);
                if (beklenen.isEmpty()) {
                    assertThat(yanit.getStatus()).isEqualTo(204);
                } else {
                    etkili++;
                    assertThat(yanit.getStatus()).isEqualTo(200);
                    Set<Long> donen = new HashSet<>();
                    json(yanit.getContentAsString()).get("yeniKirmizi").forEach(n -> donen.add(n.get("atamaId").asLong()));
                    assertThat(donen).as("tur %d: yanıt, hesaplanan yeni kırmızılarla aynı olmalı", tur).isEqualTo(beklenen);
                }
                if (!beklenen.isEmpty()) { // kırmızı oluştu: diğer makinistlerin turları "temiz takvim" varsayımıyla sürsün diye bu makinistin satırları silinir
                    jdbc.update("DELETE FROM atama WHERE makinist_id = ?", mk);
                    hazir = null; // veri değişti
                    break;
                }
            }
        }
        assertThat(kabul).isGreaterThan(300);
        assertThat(etkili).as("kaldırma etkisi en az birkaç kez sınanmalı").isGreaterThan(0);
    }

    private static tools.jackson.databind.JsonNode json(String s) {
        return new tools.jackson.databind.json.JsonMapper().readTree(s);
    }
}
