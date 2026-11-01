package com.makinistatama.degisiklik;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.params.provider.Arguments.arguments;

import java.util.stream.Stream;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import com.makinistatama.Tarayici;
import com.makinistatama.TestTanimlari;
import com.makinistatama.sefer.TestExcel;
import com.makinistatama.TestVeritabani;

/**
 * NFR-12: her mutasyon ucu değişiklik kaydı üretir. Tablo: (ad, uç çağrısı, beklenen {@link DegisiklikTuru}). Yeni mutasyon ucu eklenince
 * her görev kendi satırını buraya ekler; çağrı gerekli ön veriyi kendi kurar.
 */
class MutasyonKayitTest extends TestVeritabani {

    @FunctionalInterface
    interface Uc {
        ResultActions cagir(Tarayici yonetici, JdbcTemplate jdbc) throws Exception;
    }

    static Stream<Arguments> vakalar() {
        return Stream.of(
                arguments("tanım ekle", (Uc) (t, j) -> t.post("/api/tanimlar", "{\"tip\":\"GAR\",\"ad\":\"Ankara\"}"), DegisiklikTuru.TANIM_EKLE),
                arguments("tanım yeniden adlandır", (Uc) (t, j) -> {
                    t.post("/api/tanimlar", "{\"tip\":\"GAR\",\"ad\":\"Ankra\"}");
                    return t.put("/api/tanimlar/1", "{\"ad\":\"Ankara\"}");
                }, DegisiklikTuru.TANIM_GUNCELLE),
                arguments("parametre değiştir", (Uc) (t, j) -> t.put("/api/parametre/dinlenme.ev_saat", "{\"deger\":\"10\",\"neden\":\"deneme\"}"), DegisiklikTuru.PARAMETRE_DEGISTI),
                arguments("uygulama ayarı değiştir", (Uc) (t, j) -> t.put("/api/ayar/gordum.kalem_gun", "{\"deger\":10,\"neden\":\"deneme\"}"), DegisiklikTuru.PARAMETRE_DEGISTI),
                arguments("kural şiddeti değiştir", (Uc) (t, j) -> t.put("/api/kural/K-20/siddet", "{\"siddet\":\"KAPALI\",\"neden\":\"deneme\"}"), DegisiklikTuru.PARAMETRE_DEGISTI),
                arguments("kural ekle", (Uc) (t, j) -> t.post("/api/ozel-kural", ozelKuralGovdesi(0, "8", "")), DegisiklikTuru.KURAL_EKLE),
                arguments("kural güncelle", (Uc) (t, j) -> {
                    t.post("/api/ozel-kural", ozelKuralGovdesi(0, "8", ""));
                    return t.put("/api/ozel-kural/1", ozelKuralGovdesi(0, "9", ",\"neden\":\"eşik gevşetildi\""));
                }, DegisiklikTuru.KURAL_GUNCELLE),
                arguments("profil uygula (F2)", (Uc) (t, j) -> t.post("/api/profil/uygula",
                        "{\"paket\":{\"format\":\"makinist-atama-profil\",\"profilSurumu\":1,\"ad\":\"Deneme\",\"parametreler\":{\"dinlenme.ev_saat\":\"10\"}},"
                                + "\"secilen\":[\"PARAMETRE:dinlenme.ev_saat\"],\"neden\":\"deneme\"}"), DegisiklikTuru.PROFIL_UYGULA),
                arguments("kurum adı yaz (F1)", (Uc) (t, j) -> t.put("/api/kurum", "{\"ad\":\"Örnek İşletme (demo)\"}"), DegisiklikTuru.KURUM_GUNCELLE),
                arguments("kurum logosu yükle (F1)", (Uc) (t, j) -> t.istek(MockMvcRequestBuilders.put("/api/kurum/logo").contentType("application/octet-stream")
                        .content(com.makinistatama.kurum.KurumGorselleri.png())), DegisiklikTuru.KURUM_LOGO),
                arguments("makinist Excel içe aktar (F1)", (Uc) (t, j) -> {
                    TestTanimlari.gar(j, "Ankara");
                    byte[] xlsx = com.makinistatama.makinist.TestMakinistExcel.kitap(java.util.List.<Object[]>of(com.makinistatama.makinist.TestMakinistExcel.makinist("M001", "Örnek Personel", "Ankara")),
                            java.util.List.of(), java.util.List.of());
                    return t.istek(MockMvcRequestBuilders.multipart("/api/makinist/ice-aktar").file(new MockMultipartFile("dosya", "m.xlsx", "application/octet-stream", xlsx)).param("onizle", "false"));
                }, DegisiklikTuru.MAKINIST_ICE_AKTAR),
                arguments("resmi tatil ekle (F5)", (Uc) (t, j) -> t.post("/api/resmi-tatil", "{\"tarih\":\"2026-10-29\",\"ad\":\"Cumhuriyet Bayramı\"}"), DegisiklikTuru.TATIL_EKLE),
                arguments("resmi tatil sil (F5)", (Uc) (t, j) -> {
                    t.post("/api/resmi-tatil", "{\"tarih\":\"2026-10-29\",\"ad\":\"Cumhuriyet Bayramı\"}");
                    return t.delete("/api/resmi-tatil/2026-10-29");
                }, DegisiklikTuru.TATIL_SIL),
                arguments("atama ekle", (Uc) (t, j) -> {
                    var v = new com.makinistatama.atama.AtamaVeri(j);
                    long m = v.makinist("D001");
                    long sf = v.sefer("T1", java.time.LocalDate.of(2026, 10, 12), "08:00", "10:00");
                    return t.post("/api/atama/sefer/" + sf, com.makinistatama.atama.AtamaVeri.ataGovdesi(m));
                }, DegisiklikTuru.ATAMA),
                arguments("atama değiştir", (Uc) (t, j) -> {
                    var v = new com.makinistatama.atama.AtamaVeri(j);
                    long m1 = v.makinist("D001");
                    long m2 = v.makinist("D002");
                    long sf = v.sefer("T1", java.time.LocalDate.of(2026, 10, 12), "08:00", "10:00");
                    t.post("/api/atama/sefer/" + sf, com.makinistatama.atama.AtamaVeri.ataGovdesi(m1));
                    return t.post("/api/atama/sefer/" + sf, "{\"makinistId\":" + m2 + ",\"neden\":\"HASTALIK\"}");
                }, DegisiklikTuru.ATAMA_DEGISTIR),
                arguments("atama kaldır", (Uc) (t, j) -> {
                    var v = new com.makinistatama.atama.AtamaVeri(j);
                    long m = v.makinist("D001");
                    long sf = v.sefer("T1", java.time.LocalDate.of(2026, 10, 12), "08:00", "10:00");
                    t.post("/api/atama/sefer/" + sf, com.makinistatama.atama.AtamaVeri.ataGovdesi(m));
                    return t.delete("/api/atama/sefer/" + sf + "?rol=ASIL&neden=IZIN");
                }, DegisiklikTuru.ATAMA_KALDIR),
                arguments("konaklama ekle (F2b)", (Uc) (t, j) -> {
                    var v = new com.makinistatama.atama.AtamaVeri(j);
                    long m = v.makinist("D001");
                    return t.post("/api/konaklama", "{\"makinistId\":" + m + ",\"garId\":" + v.eskisehir
                            + ",\"yerTipi\":\"OTEL\",\"giris\":\"2026-10-14T11:30:00+03:00\",\"cikis\":\"2026-10-15T06:00:00+03:00\"}");
                }, DegisiklikTuru.KONAKLAMA_EKLE),
                arguments("konaklama güncelle (F2b)", (Uc) (t, j) -> {
                    var v = new com.makinistatama.atama.AtamaVeri(j);
                    long m = v.makinist("D001");
                    t.post("/api/konaklama", "{\"makinistId\":" + m + ",\"garId\":" + v.eskisehir
                            + ",\"yerTipi\":\"OTEL\",\"giris\":\"2026-10-14T11:30:00+03:00\",\"cikis\":\"2026-10-15T06:00:00+03:00\"}");
                    return t.put("/api/konaklama/1", "{\"makinistId\":" + m + ",\"garId\":" + v.eskisehir
                            + ",\"yerTipi\":\"YATAKHANE\",\"giris\":\"2026-10-14T11:30:00+03:00\",\"cikis\":\"2026-10-15T06:00:00+03:00\",\"versiyon\":0}");
                }, DegisiklikTuru.KONAKLAMA_GUNCELLE),
                arguments("konaklama sil (F2b)", (Uc) (t, j) -> {
                    var v = new com.makinistatama.atama.AtamaVeri(j);
                    long m = v.makinist("D001");
                    t.post("/api/konaklama", "{\"makinistId\":" + m + ",\"garId\":" + v.eskisehir
                            + ",\"yerTipi\":\"OTEL\",\"giris\":\"2026-10-14T11:30:00+03:00\",\"cikis\":\"2026-10-15T06:00:00+03:00\"}");
                    return t.delete("/api/konaklama/1");
                }, DegisiklikTuru.KONAKLAMA_SIL),
                arguments("yolcu ekle (F2b)", (Uc) (t, j) -> {
                    var v = new com.makinistatama.atama.AtamaVeri(j);
                    long m = v.makinist("D001");
                    long sf = v.sefer("T1", java.time.LocalDate.of(2026, 10, 12), "08:00", "10:00");
                    return t.post("/api/atama/sefer/" + sf, "{\"makinistId\":" + m + ",\"rol\":\"YOLCU\"}");
                }, DegisiklikTuru.ATAMA),
                arguments("yolcu kaldır (F2b)", (Uc) (t, j) -> {
                    var v = new com.makinistatama.atama.AtamaVeri(j);
                    long m = v.makinist("D001");
                    long sf = v.sefer("T1", java.time.LocalDate.of(2026, 10, 12), "08:00", "10:00");
                    t.post("/api/atama/sefer/" + sf, "{\"makinistId\":" + m + ",\"rol\":\"YOLCU\"}");
                    return t.delete("/api/atama/sefer/" + sf + "?rol=YOLCU&makinistId=" + m + "&neden=IZIN");
                }, DegisiklikTuru.ATAMA_KALDIR),
                arguments("sefer iptal", (Uc) (t, j) -> {
                    var v = new com.makinistatama.atama.AtamaVeri(j);
                    long sf = v.sefer("T1", java.time.LocalDate.of(2026, 10, 12), "08:00", "10:00");
                    return t.post("/api/sefer/" + sf + "/iptal", "{\"neden\":\"IPTAL\"}");
                }, DegisiklikTuru.SEFER_IPTAL),
                arguments("sefer gecikme", (Uc) (t, j) -> {
                    var v = new com.makinistatama.atama.AtamaVeri(j);
                    long sf = v.sefer("T1", java.time.LocalDate.of(2026, 10, 12), "08:00", "10:00");
                    return t.post("/api/sefer/" + sf + "/gecikme", "{\"kalkisGecikmeDk\":45,\"neden\":\"GECIKME\"}");
                }, DegisiklikTuru.SEFER_GECIKME),
                arguments("otomatik atama (uygula)", (Uc) (t, j) -> {
                    var v = new com.makinistatama.atama.AtamaVeri(j);
                    v.makinist("D001");
                    v.sefer("T1", java.time.LocalDate.of(2026, 10, 12), "08:00", "10:00");
                    return t.post("/api/atama/otomatik", "{\"bas\":\"2026-10-12\",\"bit\":\"2026-10-12\",\"uygula\":true}");
                }, DegisiklikTuru.OTOMATIK_ATAMA),
                arguments("makinist ekle", (Uc) (t, j) -> t.post("/api/makinist", makinistGovdesi(j, "D001", 0)), DegisiklikTuru.MAKINIST_EKLE),
                arguments("makinist güncelle", (Uc) (t, j) -> {
                    t.post("/api/makinist", makinistGovdesi(j, "D001", 0));
                    return t.put("/api/makinist/1", makinistGovdesi(j, "D001", 0).replace("Demo Makinist", "Demo Makinist Yeni"));
                }, DegisiklikTuru.MAKINIST_GUNCELLE),
                arguments("izin ekle", (Uc) (t, j) -> {
                    t.post("/api/makinist", makinistGovdesi(j, "D001", 0));
                    return t.post("/api/makinist/1/izin", "{\"baslangic\":\"2026-10-20\",\"bitis\":\"2026-10-24\",\"tur\":\"YILLIK\"}");
                }, DegisiklikTuru.IZIN_EKLE),
                arguments("izin talebi", (Uc) (t, j) -> {
                    t.post("/api/makinist", makinistGovdesi(j, "D001", 0));
                    return t.post("/api/izin", "{\"makinistId\":1,\"baslangic\":\"2026-10-20\",\"bitis\":\"2026-10-24\",\"tur\":\"YILLIK\"}");
                }, DegisiklikTuru.IZIN_TALEP),
                arguments("izin onayı", (Uc) (t, j) -> {
                    t.post("/api/makinist", makinistGovdesi(j, "D001", 0));
                    // talebi başka biri açmış gibi (OK-4: kimse kendi talebini onaylayamaz)
                    j.update("INSERT INTO izin (makinist_id, baslangic, bitis, tur, durum, olusturan) VALUES (1, '2026-10-20', '2026-10-24', 'YILLIK', 'TALEP', 'baska kisi')");
                    return t.post("/api/izin/1/onay", "");
                }, DegisiklikTuru.IZIN_ONAY),
                arguments("izin reddi", (Uc) (t, j) -> {
                    t.post("/api/makinist", makinistGovdesi(j, "D001", 0));
                    t.post("/api/izin", "{\"makinistId\":1,\"baslangic\":\"2026-10-20\",\"bitis\":\"2026-10-24\",\"tur\":\"YILLIK\"}");
                    return t.post("/api/izin/1/red", "");
                }, DegisiklikTuru.IZIN_RED),
                arguments("sefer ekle", (Uc) (t, j) -> t.post("/api/sefer", seferGovdesi(j, "T1", 0)), DegisiklikTuru.SEFER_EKLE),
                arguments("sefer güncelle", (Uc) (t, j) -> {
                    t.post("/api/sefer", seferGovdesi(j, "T1", 0));
                    return t.put("/api/sefer/1", seferGovdesi(j, "T1", 0).replace("08:00", "08:30"));
                }, DegisiklikTuru.SEFER_GUNCELLE),
                arguments("sefer yükle (Excel)", (Uc) (t, j) -> {
                    TestTanimlari.gar(j, "Ankara");
                    TestTanimlari.gar(j, "Eskişehir");
                    TestTanimlari.hat(j, "Ankara–Eskişehir");
                    TestTanimlari.aracTipi(j, "YHT");
                    byte[] xlsx = TestExcel.sefer(java.util.List.<String[]>of(
                            new String[] { "12.10.2026", "T1", "Ankara", "08:00", "Eskişehir", "09:30", "YHT", "Ankara–Eskişehir" }));
                    return t.istek(MockMvcRequestBuilders.multipart("/api/sefer/yukle")
                            .file(new MockMultipartFile("dosya", "s.xlsx", "application/octet-stream", xlsx)).param("onizle", "false"));
                }, DegisiklikTuru.SEFER_YUKLE),
                // F2a: hesap olayları (kullanıcı yönetimi ve makinist hesabı)
                arguments("kullanıcı oluştur", (Uc) (t, j) -> t.post("/api/kullanicilar", "{\"eposta\":\"ata9@ornek.test\",\"adSoyad\":\"Ata Dokuz\",\"rol\":\"ATAMACI\",\"parola\":\"uzun-parola-12345\"}"), DegisiklikTuru.HESAP_ACILDI),
                arguments("kullanıcı güncelle", (Uc) (t, j) -> {
                    t.post("/api/kullanicilar", "{\"eposta\":\"ata9@ornek.test\",\"adSoyad\":\"Ata Dokuz\",\"rol\":\"ATAMACI\",\"parola\":\"uzun-parola-12345\"}");
                    return t.put("/api/kullanicilar/2", "{\"adSoyad\":\"Ata Dokuz\",\"rol\":\"ATAMACI\",\"aktif\":false}");
                }, DegisiklikTuru.HESAP_GUNCELLENDI),
                arguments("kullanıcı parolası sıfırla", (Uc) (t, j) -> {
                    t.post("/api/kullanicilar", "{\"eposta\":\"ata9@ornek.test\",\"adSoyad\":\"Ata Dokuz\",\"rol\":\"ATAMACI\",\"parola\":\"uzun-parola-12345\"}");
                    return t.post("/api/kullanicilar/2/parola", "{\"yeniParola\":\"yeni-uzun-parola-12\"}");
                }, DegisiklikTuru.HESAP_PAROLA_SIFIRLANDI),
                arguments("makinist hesabı aç", (Uc) (t, j) -> {
                    t.post("/api/makinist", makinistGovdesi(j, "D001", 0));
                    return t.post("/api/makinist/1/hesap", "");
                }, DegisiklikTuru.HESAP_ACILDI),
                arguments("makinist parolası sıfırla", (Uc) (t, j) -> {
                    t.post("/api/makinist", makinistGovdesi(j, "D001", 0));
                    t.post("/api/makinist/1/hesap", "");
                    return t.post("/api/makinist/1/hesap/parola-sifirla", "");
                }, DegisiklikTuru.HESAP_PAROLA_SIFIRLANDI),
                arguments("makinist kilidi aç", (Uc) (t, j) -> {
                    t.post("/api/makinist", makinistGovdesi(j, "D001", 0));
                    t.post("/api/makinist/1/hesap", "");
                    return t.post("/api/makinist/1/hesap/kilit-ac", "");
                }, DegisiklikTuru.HESAP_KILIT_ACILDI),
                arguments("kendi parolasını değiştir", (Uc) (t, j) -> t.post("/api/auth/parola",
                        "{\"eskiParola\":\"" + Tarayici.PAROLA + "\",\"yeniParola\":\"benim-yeni-parolam-9\"}"), DegisiklikTuru.PAROLA_DEGISTI),
                // F2a-3: takas talebi kararları (makinist uçları yönetici oturumuyla çağrılamaz: açılış / kabul / iptal / onay kayıtları takas/Takas*Test'te, kim ve varlıkla doğrulanır)
                arguments("takas talebi reddi (atamacı)", (Uc) (t, j) -> {
                    t.post("/api/makinist", makinistGovdesi(j, "D001", 0));
                    j.update("INSERT INTO takas_talebi (tur, durum, kaynak_atama_id, kaynak_makinist_id, kaynak_sefer_id, kaynak_ozet, neden, olusturan_kullanici_id, olusturma, son_kalkis) "
                            + "VALUES ('BIRAK', 'ATAMACI_ONAYI_BEKLIYOR', 1, 1, 1, 'ozet', 'IZIN', 1, now(), now() + interval '2 day')");
                    return t.post("/api/takas/1/red", "");
                }, DegisiklikTuru.TAKAS_RED),
                arguments("sefer böl (T3.4)", (Uc) (t, j) -> {
                    var v = new com.makinistatama.atama.AtamaVeri(j);
                    long sf = v.sefer("T1", java.time.LocalDate.of(2026, 10, 12), "08:00", "10:00");
                    long ara = TestTanimlari.gar(j, "Sivas");
                    return t.post("/api/atama/sefer/" + sf + "/bol", "{\"versiyon\":0,\"noktalar\":[{\"garId\":" + ara + ",\"varisSaat\":\"08:50\",\"kalkisSaat\":\"09:00\",\"km\":100}]}");
                }, DegisiklikTuru.SEFER_BOL),
                arguments("sefer birleştir (T5.3)", (Uc) (t, j) -> {
                    var v = new com.makinistatama.atama.AtamaVeri(j);
                    long sf = v.sefer("T1", java.time.LocalDate.of(2026, 10, 12), "08:00", "10:00");
                    long ara = TestTanimlari.gar(j, "Sivas");
                    t.post("/api/atama/sefer/" + sf + "/bol", "{\"versiyon\":0,\"noktalar\":[{\"garId\":" + ara + ",\"varisSaat\":\"08:50\",\"kalkisSaat\":\"09:00\",\"km\":100}]}");
                    long versiyon = j.queryForObject("SELECT versiyon FROM sefer WHERE id = ?", Long.class, sf);
                    return t.post("/api/atama/sefer/" + sf + "/birlestir", "{\"versiyon\":" + versiyon + "}");
                }, DegisiklikTuru.SEFER_BIRLESTIR),
                arguments("intikal ekle (İ-1)", (Uc) (t, j) -> intikalEkle(t, j), DegisiklikTuru.INTIKAL_EKLE),
                arguments("intikal güncelle (İ-1)", (Uc) (t, j) -> {
                    intikalEkle(t, j);
                    return t.put("/api/intikal/1", intikalGovdesi(j, "09:00", "10:30", 0));
                }, DegisiklikTuru.INTIKAL_GUNCELLE),
                arguments("intikal iptal (İ-1)", (Uc) (t, j) -> {
                    intikalEkle(t, j);
                    return t.delete("/api/intikal/1?neden=DIGER&nedenNotu=deneme&versiyon=0");
                }, DegisiklikTuru.INTIKAL_IPTAL),
                arguments("gar intikal süresi yaz (İ-2)", (Uc) (t, j) -> {
                    var v = new com.makinistatama.atama.AtamaVeri(j);
                    return t.put("/api/tanimlar/intikal-sure", "{\"garAId\":" + v.ankara + ",\"garBId\":" + v.eskisehir + ",\"dakika\":90}");
                }, DegisiklikTuru.INTIKAL_SURESI_DEGISTI),
                arguments("gar intikal süresi sil (İ-2)", (Uc) (t, j) -> {
                    var v = new com.makinistatama.atama.AtamaVeri(j);
                    t.put("/api/tanimlar/intikal-sure", "{\"garAId\":" + v.ankara + ",\"garBId\":" + v.eskisehir + ",\"dakika\":90}");
                    return t.delete("/api/tanimlar/intikal-sure?garAId=" + v.eskisehir + "&garBId=" + v.ankara);
                }, DegisiklikTuru.INTIKAL_SURESI_DEGISTI)
        );
    }

    /** İntikal ön verisi: makinist D001 (Ankara bağlı), Ankara -> Eskişehir servisi. */
    static ResultActions intikalEkle(Tarayici t, JdbcTemplate j) throws Exception {
        return t.post("/api/intikal", intikalGovdesi(j, "07:00", "08:30", null));
    }

    static String intikalGovdesi(JdbcTemplate j, String kalkis, String varis, Integer versiyon) {
        var v = new com.makinistatama.atama.AtamaVeri(j);
        long m = j.queryForList("SELECT id FROM makinist WHERE sicil = 'D001'", Long.class).stream().findFirst().orElseGet(() -> v.makinist("D001"));
        return "{\"makinistId\":" + m + ",\"kalkisGarId\":" + v.ankara + ",\"varisGarId\":" + v.eskisehir + ",\"kalkis\":\"2026-10-12T" + kalkis + ":00+03:00\",\"varis\":\"2026-10-12T"
                + varis + ":00+03:00\",\"aracTuru\":\"SERVIS\"" + (versiyon == null ? "" : ",\"versiyon\":" + versiyon) + "}";
    }

    static String ozelKuralGovdesi(long versiyon, String esik, String ek) {
        return "{\"ad\":\"Günlük sınır\",\"olcu\":\"TOPLAM_GOREV_SURESI\",\"pencere\":\"GUN\",\"karsilastirma\":\"EN_FAZLA\",\"esik\":" + esik
                + ",\"siddet\":\"ENGEL\",\"gerekce\":\"deneme\",\"gecerlilikBas\":\"2026-01-01\",\"versiyon\":" + versiyon + ek + "}";
    }

    static String makinistGovdesi(JdbcTemplate j, String sicil, long versiyon) {
        long gar = TestTanimlari.gar(j, "Ankara");
        return "{\"sicil\":\"" + sicil + "\",\"adSoyad\":\"Demo Makinist 01\",\"bagliGarId\":" + gar + ",\"durum\":\"AKTIF\",\"ehliyetBitis\":\"2030-01-01\","
                + "\"saglikBitis\":\"2027-06-01\",\"psikoteknikBitis\":\"2028-01-01\",\"versiyon\":" + versiyon + "}";
    }

    static String seferGovdesi(JdbcTemplate j, String tren, long versiyon) {
        return "{\"trenNo\":\"" + tren + "\",\"tarih\":\"2026-10-12\",\"kalkisGarId\":" + TestTanimlari.gar(j, "Ankara") + ",\"kalkisSaat\":\"08:00\","
                + "\"varisGarId\":" + TestTanimlari.gar(j, "Eskişehir") + ",\"varisSaat\":\"10:00\",\"aracTipiId\":" + TestTanimlari.aracTipi(j, "YHT")
                + ",\"hatId\":" + TestTanimlari.hat(j, "Ankara–Eskişehir") + ",\"versiyon\":" + versiyon + "}";
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("vakalar")
    void mutasyon_kayitUretir(String ad, Uc uc, DegisiklikTuru beklenen) throws Exception {
        Tarayici y = yonetici();

        uc.cagir(y, jdbc).andReturn();

        assertThat(jdbc.queryForObject("SELECT count(*) FROM degisiklik_kaydi WHERE tur = ?", Integer.class, beklenen.name()))
                .as("%s için %s kaydı", ad, beklenen).isGreaterThanOrEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT kullanici_adi FROM degisiklik_kaydi WHERE tur = ? LIMIT 1", String.class, beklenen.name()))
                .isEqualTo("Yöne Tici");
    }
}
