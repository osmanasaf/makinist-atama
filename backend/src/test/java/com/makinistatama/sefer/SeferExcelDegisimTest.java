package com.makinistatama.sefer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayOutputStream;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.makinistatama.ortak.AramaMetni;
import com.makinistatama.ortak.UygulamaHatasi;
import com.makinistatama.ortak.ZamanAyari;
import com.makinistatama.sefer.SeferExcelOkuyucu.Sonuc;
import com.makinistatama.sefer.SeferExcelOkuyucu.Tanimlar;
import com.makinistatama.sefer.SeferExcelOkuyucu.YuklemeSatiri;

/** F2c T2.1 (MK-61): "Değişim Noktaları" ikinci sayfası; saf okuyucu, DB'siz. */
class SeferExcelDegisimTest {

    static final Tanimlar TANIMLAR = new Tanimlar(
            Map.of(AramaMetni.katla("Ankara"), 1L, AramaMetni.katla("Sivas"), 2L, AramaMetni.katla("Kars"), 3L, AramaMetni.katla("Erzincan"), 4L),
            Map.of(AramaMetni.katla("Ankara–Kars"), 10L, AramaMetni.katla("Sivas–Kars"), 11L),
            Map.of(AramaMetni.katla("YHT"), 20L, AramaMetni.katla("DM"), 21L));

    static String[] ana(String tarih, String tren, String kalkis, String varis, String km) {
        return new String[] { tarih, tren, "Ankara", kalkis, "Kars", varis, "YHT", "Ankara–Kars", km };
    }

    static String[] nokta(String tren, String sira, String gar, String varis, String kalkis, String km) {
        return new String[] { tren, sira, gar, varis, kalkis, km };
    }

    static String saat(java.time.Instant an) {
        ZonedDateTime z = an.atZone(ZamanAyari.BOLGE);
        return z.toLocalDate() + " " + z.toLocalTime();
    }

    static Sonuc oku(List<String[]> ana, List<String[]> noktalar) {
        return SeferExcelOkuyucu.oku(TestExcel.sefer(TestExcel.BASLIK, ana, noktalar), TANIMLAR);
    }

    @Test
    void ankaraSivasKars_ikiParca_kmKumulatif_etiketVeTrenGunu() {
        Sonuc s = oku(List.<String[]>of(ana("12.10.2026", "DE-1503", "08:00", "20:10", "1103")), List.<String[]>of(nokta("DE-1503", "1", "Sivas", "13:30", "13:40", "603")));

        assertThat(s.hatalar()).isEmpty();
        assertThat(s.gecerli()).hasSize(2);
        YuklemeSatiri p1 = s.gecerli().get(0);
        YuklemeSatiri p2 = s.gecerli().get(1);
        assertThat(p1.parcaNo()).isEqualTo(1);
        assertThat(p1.parcaSayisi()).isEqualTo(2);
        assertThat(p1.kalkisGarId()).isEqualTo(1L);
        assertThat(p1.varisGarId()).isEqualTo(2L);
        assertThat(saat(p1.kalkis())).isEqualTo("2026-10-12 08:00");
        assertThat(saat(p1.varis())).isEqualTo("2026-10-12 13:30");
        assertThat(p1.km()).isEqualByComparingTo("603");
        assertThat(p2.parcaNo()).isEqualTo(2);
        assertThat(p2.kalkisGarId()).isEqualTo(2L);
        assertThat(p2.varisGarId()).isEqualTo(3L);
        assertThat(saat(p2.kalkis())).isEqualTo("2026-10-12 13:40");
        assertThat(saat(p2.varis())).isEqualTo("2026-10-12 20:10");
        assertThat(p2.km()).isEqualByComparingTo("500");
        assertThat(s.gecerli()).allSatisfy(p -> {
            assertThat(p.satirNo()).isEqualTo(2);
            assertThat(p.trenGunu()).isEqualTo(LocalDate.of(2026, 10, 12));
        });
    }

    @Test
    void gecedeAsanTren_parca2ErtesiGununTarihiniAlir_trenGunuZincirinIlkGunu() {
        Sonuc s = oku(List.<String[]>of(ana("12.10.2026", "DE-1601", "20:00", "09:10", "1103")), List.<String[]>of(nokta("DE-1601", "1", "Sivas", "02:30", "02:40", "603")));

        assertThat(s.hatalar()).isEmpty();
        assertThat(s.gecerli()).extracting(p -> p.tarih() + " " + saat(p.kalkis()) + " > " + saat(p.varis()) + " " + p.trenGunu()).containsExactly(
                "2026-10-12 2026-10-12 20:00 > 2026-10-13 02:30 2026-10-12", "2026-10-13 2026-10-13 02:40 > 2026-10-13 09:10 2026-10-12");
    }

    @Test
    void ucParca_hatVeAracTipiBaslangicNoktasindanGelir_bosIseSatirinki() {
        List<String[]> noktalar = List.of(
                new String[] { "DE-1503", "1", "Sivas", "13:30", "13:40", "603", "Sivas–Kars", "DM" },
                nokta("DE-1503", "2", "Erzincan", "16:00", "16:10", "800"));
        Sonuc s = oku(List.<String[]>of(ana("12.10.2026", "DE-1503", "08:00", "20:10", "1103")), noktalar);

        assertThat(s.hatalar()).isEmpty();
        assertThat(s.gecerli()).extracting(YuklemeSatiri::parcaNo, YuklemeSatiri::parcaSayisi, YuklemeSatiri::hatId, YuklemeSatiri::aracTipiId, p -> p.km().intValue())
                .containsExactly(org.assertj.core.groups.Tuple.tuple(1, 3, 10L, 20L, 603), org.assertj.core.groups.Tuple.tuple(2, 3, 11L, 21L, 197),
                        org.assertj.core.groups.Tuple.tuple(3, 3, 10L, 20L, 303));
    }

    @Test
    void kmBosSa_ilgiliParcalarNull() {
        Sonuc s = oku(List.<String[]>of(ana("12.10.2026", "DE-1503", "08:00", "20:10", null)), List.<String[]>of(nokta("DE-1503", "1", "Sivas", "13:30", "13:40", "603")));

        assertThat(s.gecerli()).extracting(p -> p.km() == null ? null : p.km().intValue()).containsExactly(603, null);
    }

    @Test
    void sayfaYoksa_akisBugunkuGibi_vePlanlanmamisTrenParcalanmaz() {
        Sonuc yok = SeferExcelOkuyucu.oku(TestExcel.sefer(List.<String[]>of(ana("12.10.2026", "DE-1503", "08:00", "20:10", "1103"))), TANIMLAR);
        assertThat(yok.gecerli()).singleElement().satisfies(p -> assertThat(p.parcaNo()).isNull());
        assertThat(yok.engelli()).isFalse();
    }

    @Test
    void trenNoYazimFarki_buyukKucukHarfVeBoslukDuyarsiz_eslesir_kayitSeferlerYazimiyla() {
        // büyük/küçük harf, baş/son/ara boşluk, NBSP: aynı tren (W: sessizce parçasız yüklenmemeli)
        for (String nokta : List.of("de-1503", " DE-1503 ", "De-1503", "DE -1503", "DE- 1503")) {
            Sonuc s = oku(List.<String[]>of(ana("12.10.2026", "DE-1503", "08:00", "20:10", "1103")), List.<String[]>of(nokta(nokta, "1", "Sivas", "13:30", "13:40", "603")));
            assertThat(s.hatalar()).as(nokta).isEmpty();
            assertThat(s.engelli()).as(nokta).isFalse();
            assertThat(s.gecerli()).as(nokta).extracting(YuklemeSatiri::trenNo, YuklemeSatiri::parcaNo).containsExactly(org.assertj.core.groups.Tuple.tuple("DE-1503", 1),
                    org.assertj.core.groups.Tuple.tuple("DE-1503", 2));
        }
    }

    @Test
    void trenNoTurkceHarfler_yerelAyardanBagimsiz_ayniKatlama() {
        // tren no ASCII varsayılmaz: İ/I/ı/i tek harf (AramaMetni.katla); varsayılan yerel tr-TR de olsa en-US de olsa sonuç aynı
        java.util.Locale eski = java.util.Locale.getDefault();
        try {
            for (java.util.Locale yerel : List.of(java.util.Locale.forLanguageTag("tr-TR"), java.util.Locale.ROOT, java.util.Locale.US)) {
                java.util.Locale.setDefault(yerel);
                for (String[] cift : new String[][] { { "KİZ-1", "kiz-1" }, { "KİZ-1", "KIZ-1" }, { "kız-1", "KIZ-1" }, { "kız-1", "KİZ-1" }, { "İZBAN-7", "izban-7" } }) {
                    Sonuc s = oku(List.<String[]>of(ana("12.10.2026", cift[0], "08:00", "20:10", "1103")), List.<String[]>of(nokta(cift[1], "1", "Sivas", "13:30", "13:40", "603")));
                    assertThat(s.gecerli()).as(yerel + " " + cift[0] + "/" + cift[1]).hasSize(2).allSatisfy(p -> assertThat(p.trenNo()).isEqualTo(cift[0]));
                    assertThat(s.engelli()).isFalse();
                }
            }
        } finally {
            java.util.Locale.setDefault(eski);
        }
    }

    @Test
    void karsiligiOlmayanNoktaSatiri_engelleyici_sayfaVeTrenNoTasir() {
        Sonuc s = oku(List.<String[]>of(ana("12.10.2026", "DE-1503", "08:00", "20:10", "1103")), List.<String[]>of(nokta("DE-1530", "1", "Sivas", "13:30", "13:40", "603")));

        assertThat(s.engelli()).isTrue();
        assertThat(s.hatalar()).singleElement().satisfies(h -> {
            assertThat(h.sayfa()).isEqualTo("Değişim Noktaları");
            assertThat(h.satirNo()).isEqualTo(2);
            assertThat(h.trenNo()).isEqualTo("DE-1530");
            assertThat(h.neden()).contains("Değişim Noktaları satır 2").contains("Seferler sayfasında yok");
        });
    }

    @Test
    void parcaSuresiUstSiniri_tamSinirKabul_ustuEngelleyici() {
        // 08:00 -> 00:00 (ertesi gün) tam 16 s: kabul; ana sefer 08:00 -> 06:00 = 22 s
        Sonuc tam = SeferExcelOkuyucu.oku(TestExcel.sefer(TestExcel.BASLIK, List.<String[]>of(ana("12.10.2026", "DE-1503", "08:00", "06:00", "1103")),
                List.<String[]>of(nokta("DE-1503", "1", "Sivas", "00:00", "00:10", "603"))), TANIMLAR, 16);
        assertThat(tam.hatalar()).isEmpty();
        assertThat(tam.engelli()).isFalse();
        assertThat(tam.gecerli()).hasSize(2);

        // 16 s 10 dk: engelleyici, tren yüklenmez, mesaj parça no + süre + ayar adını söyler
        Sonuc uzun = SeferExcelOkuyucu.oku(TestExcel.sefer(TestExcel.BASLIK, List.<String[]>of(ana("12.10.2026", "DE-1503", "08:00", "06:00", "1103")),
                List.<String[]>of(nokta("DE-1503", "1", "Sivas", "00:10", "00:20", "603"))), TANIMLAR, 16);
        assertThat(uzun.engelli()).isTrue();
        assertThat(uzun.gecerli()).isEmpty();
        assertThat(uzun.hatalar()).singleElement().satisfies(h -> assertThat(h.neden()).contains("1. parça 16 s 10 dk").contains("en çok 16 s").contains("sefer.parca_azami_saat"));
    }

    @Test
    void saatYazimHatasi_varis0730YerineOnUc30_sessizKabulEdilmez() {
        // Sivas varışı 13:30 yerine 07:30 yazılmış: 1. parça ertesi güne taşar (23 s 30 dk); sınırsız okumada (24 s) kabul edilirdi
        List<String[]> ana = List.<String[]>of(ana("12.10.2026", "DE-1503", "08:00", "20:10", "1103"));
        List<String[]> yanlis = List.<String[]>of(nokta("DE-1503", "1", "Sivas", "07:30", "13:40", "603"));
        assertThat(SeferExcelOkuyucu.oku(TestExcel.sefer(TestExcel.BASLIK, ana, yanlis), TANIMLAR).engelli()).as("sınırsız").isFalse();
        Sonuc s = SeferExcelOkuyucu.oku(TestExcel.sefer(TestExcel.BASLIK, ana, yanlis), TANIMLAR, 16);
        assertThat(s.engelli()).isTrue();
        assertThat(s.gecerli()).isEmpty();
    }

    @Test
    void sayfaSirasiTers_anlasilirMesaj() throws Exception {
        byte[] d;
        try (var kitap = new org.apache.poi.xssf.usermodel.XSSFWorkbook(); ByteArrayOutputStream cikti = new ByteArrayOutputStream()) {
            TestExcel.sayfaYaz(kitap.createSheet("Değişim Noktaları"), TestExcel.DEGISIM_BASLIK, List.<String[]>of(nokta("DE-1503", "1", "Sivas", "13:30", "13:40", "603")));
            TestExcel.sayfaYaz(kitap.createSheet("Seferler"), TestExcel.BASLIK, List.<String[]>of(ana("12.10.2026", "DE-1503", "08:00", "20:10", "1103")));
            kitap.write(cikti);
            d = cikti.toByteArray();
        }
        assertThatThrownBy(() -> SeferExcelOkuyucu.oku(d, TANIMLAR)).isInstanceOf(UygulamaHatasi.class).hasMessageContaining("Sayfa sırası ters").hasMessageContaining("'Seferler' olmalı");
    }

    @Test
    void hataSatirlari_sayfaAdiVeTrenNoTasir() {
        // Seferler'de bilinmeyen gar + Değişim Noktaları'nda hatalı gar: iki hata, ikisi de kendi sayfası ve tren no ile (UX-1)
        Sonuc s = oku(List.<String[]>of(new String[] { "12.10.2026", "T-1", "Yok Gar", "08:00", "Kars", "10:00", "YHT", "Ankara–Kars", "10" }, ana("12.10.2026", "DE-1503", "08:00", "20:10", "1103")),
                List.<String[]>of(nokta("DE-1503", "1", "Sivass", "13:30", "13:40", "603")));

        assertThat(s.hatalar()).extracting(h -> h.sayfa() + "|" + h.satirNo() + "|" + h.trenNo()).containsExactlyInAnyOrder("Seferler|2|T-1", "Seferler|3|DE-1503", "Değişim Noktaları|2|DE-1503");
        assertThat(s.engelli()).isFalse(); // trenin kendi hatalı verisi trenin yüklenmemesiyle çözülür; engelleyici değil
    }

    @Test
    void degisimHatalari_trenSessizceParcasizYuklenmez_nedenSatirOnekiTasir() {
        List<String[]> ana = List.<String[]>of(ana("12.10.2026", "DE-1503", "08:00", "20:10", "1103"));
        Map<String, List<String[]>> hataliSenaryolar = new java.util.LinkedHashMap<>();
        hataliSenaryolar.put("sıra 1'den başlayıp", List.<String[]>of(nokta("DE-1503", "2", "Sivas", "13:30", "13:40", "603")));
        hataliSenaryolar.put("Gar bilinmiyor", List.<String[]>of(nokta("DE-1503", "1", "Sivass", "13:30", "13:40", "603")));
        hataliSenaryolar.put("tekrar ediyor", List.<String[]>of(nokta("DE-1503", "1", "Sivas", "13:30", "13:40", "603"), nokta("DE-1503", "1", "Erzincan", "16:00", "16:10", "800")));
        hataliSenaryolar.put("Kalkış saati varış saatinden önce", List.<String[]>of(nokta("DE-1503", "1", "Sivas", "13:30", "13:10", "603")));
        hataliSenaryolar.put("Sıra geçersiz", List.<String[]>of(nokta("DE-1503", "a", "Sivas", "13:30", "13:40", "603")));
        hataliSenaryolar.put("KM sıra ilerledikçe azalamaz", List.<String[]>of(nokta("DE-1503", "1", "Sivas", "13:30", "13:40", "900"), nokta("DE-1503", "2", "Erzincan", "16:00", "16:10", "800")));
        hataliSenaryolar.put("KM tutarsız", List.<String[]>of(nokta("DE-1503", "1", "Sivas", "13:30", "13:40", "1200"))); // satır KM'si 1103 < 1200
        hataliSenaryolar.put("en çok 5 değişim noktası", List.of(
                nokta("DE-1503", "1", "Sivas", "09:00", "09:10", "100"), nokta("DE-1503", "2", "Sivas", "10:00", "10:10", "200"), nokta("DE-1503", "3", "Sivas", "11:00", "11:10", "300"),
                nokta("DE-1503", "4", "Sivas", "12:00", "12:10", "400"), nokta("DE-1503", "5", "Sivas", "13:00", "13:10", "500"), nokta("DE-1503", "6", "Sivas", "14:00", "14:10", "600")));

        hataliSenaryolar.forEach((beklenen, noktalar) -> {
            Sonuc s = oku(ana, noktalar);
            assertThat(s.gecerli()).as(beklenen).isEmpty(); // sessizce parçasız yüklenmez
            assertThat(s.hatalar()).as(beklenen).anySatisfy(h -> assertThat(h.neden()).contains("Değişim Noktaları satır").contains(beklenen));
        });
    }

    @Test
    void bosDegisimSayfasi_sorunDegil() {
        byte[] d = TestExcel.sefer(TestExcel.BASLIK, List.<String[]>of(ana("12.10.2026", "DE-1503", "08:00", "20:10", "1103")), List.of());
        assertThat(SeferExcelOkuyucu.oku(d, TANIMLAR).gecerli()).hasSize(1);
    }

    @Test
    void degisimSayfasindaEksikKolon_gecersizDosya() throws Exception {
        try (var kitap = new org.apache.poi.xssf.usermodel.XSSFWorkbook(); var cikti = new ByteArrayOutputStream()) {
            TestExcel.sayfaYaz(kitap.createSheet("Seferler"), TestExcel.BASLIK, List.<String[]>of(ana("12.10.2026", "DE-1503", "08:00", "20:10", "1103")));
            TestExcel.sayfaYaz(kitap.createSheet("Değişim Noktaları"), List.of("Tren No", "Sıra", "Gar"), List.of());
            kitap.write(cikti);
            assertThatThrownBy(() -> SeferExcelOkuyucu.oku(cikti.toByteArray(), TANIMLAR)).isInstanceOf(UygulamaHatasi.class).hasMessageContaining("eksik sütun").hasMessageContaining("Varış Saat");
        }
    }

    @Test
    void sablon_bosDegisimSayfasiylaYuklenebilir() {
        Sonuc s = SeferExcelOkuyucu.oku(SeferExcelOkuyucu.sablon(), new Tanimlar(Map.of(AramaMetni.katla("Ankara"), 1L, AramaMetni.katla("Eskişehir"), 2L),
                Map.of(AramaMetni.katla("Ankara–Eskişehir"), 10L), Map.of(AramaMetni.katla("YHT"), 20L)));
        assertThat(s.hatalar()).isEmpty();
        assertThat(s.gecerli()).hasSize(1);
    }

    // ------------------------------------------------------------------ boyut / zip güvenliği (NFR-02)

    /** İki sayfalı sahte zip kabuğu: gerçek kitap gerekmez, denetim POI'den ÖNCE çalışmalı. */
    private static byte[] ikiSayfaZip(int ikinciTekrar, java.util.function.IntFunction<String> ikinciParca) throws Exception {
        ByteArrayOutputStream b = new ByteArrayOutputStream();
        try (java.util.zip.ZipOutputStream z = new java.util.zip.ZipOutputStream(b)) {
            z.putNextEntry(new java.util.zip.ZipEntry("xl/worksheets/sheet1.xml"));
            z.write("<row r=\"1\"><c/></row>".getBytes());
            z.closeEntry();
            z.putNextEntry(new java.util.zip.ZipEntry("xl/worksheets/sheet2.xml"));
            for (int i = 1; i <= ikinciTekrar; i++) {
                z.write(ikinciParca.apply(i).getBytes());
            }
            z.closeEntry();
        }
        return b.toByteArray();
    }

    @Test
    void ikinciSayfa_satirSiniriniAsarsa_POI_acilmadan_reddedilir() throws Exception {
        byte[] cok = ikiSayfaZip(SeferExcelOkuyucu.EN_FAZLA_SATIR + 5, i -> "<row r=\"" + i + "\"><c r=\"A" + i + "\"><v>1</v></c></row>");

        assertThatThrownBy(() -> SeferExcelOkuyucu.oku(cok, TANIMLAR)).isInstanceOf(UygulamaHatasi.class).hasMessageContaining("en çok 20000 satır");
    }

    @Test
    void ikinciSayfa_zipBombasi_POI_acilmadan_reddedilir() throws Exception {
        String hucre = "<c><is><t>" + "a".repeat(64) + "</t></is></c>";
        byte[] bomba = ikiSayfaZip(400_000, i -> hucre);
        assertThat(bomba.length).isLessThan((int) SeferExcelOkuyucu.EN_FAZLA_BAYT);

        assertThatThrownBy(() -> SeferExcelOkuyucu.oku(bomba, TANIMLAR)).isInstanceOf(UygulamaHatasi.class).hasMessageContaining("açıldığında çok büyük");
    }

    @Test
    void acilmisToplamSatir_20000i_asarsa_reddedilir() {
        // 3.400 satır x 6 parça = 20.400 sefer satırı (dosya sınırın çok altında)
        List<String[]> anaSatirlar = new ArrayList<>();
        for (int i = 0; i < 3_400; i++) {
            anaSatirlar.add(ana("12.10.2026", "X", "01:00", "23:00", "1000"));
        }
        List<String[]> noktalar = List.of(nokta("X", "1", "Sivas", "03:00", "03:10", "100"), nokta("X", "2", "Sivas", "05:00", "05:10", "200"),
                nokta("X", "3", "Sivas", "07:00", "07:10", "300"), nokta("X", "4", "Sivas", "09:00", "09:10", "400"), nokta("X", "5", "Sivas", "11:00", "11:10", "500"));
        byte[] dosya = TestExcel.sefer(TestExcel.BASLIK, anaSatirlar, noktalar);

        assertThatThrownBy(() -> SeferExcelOkuyucu.oku(dosya, TANIMLAR)).isInstanceOf(UygulamaHatasi.class).hasMessageContaining("Parçalar açıldığında");
    }

    /** F2c tur 3 (review INFO): Seferler'de yalnız harf farkıyla ayrışan iki yazım + Değişim Noktaları -> belirsiz eşleşme ENGELLEYİCİ (ana sayfa davranışı yine değişmez: değişim verisi yokken uyarı yok). */
    @Test
    void yalnizHarfFarkliIkiTrenNo_degisimVerisiVarken_engelleyiciHata() {
        List<String[]> ana = List.<String[]>of(ana("12.10.2026", "DE-1503", "08:00", "20:10", "1103"), ana("12.10.2026", "de-1503", "09:00", "21:10", "1103"));
        Sonuc s = oku(ana, List.<String[]>of(nokta("DE-1503", "1", "Sivas", "13:30", "13:40", "603")));

        assertThat(s.engelli()).isTrue();
        assertThat(s.hatalar()).anySatisfy(h -> assertThat(h.neden()).contains("aynı tren farklı yazılmış olabilir"));
        assertThat(s.hatalar()).anySatisfy(h -> assertThat(h.trenNo()).isIn("DE-1503", "de-1503"));

        // aynı yazımın iki günü belirsizlik değildir
        Sonuc ayni = oku(List.<String[]>of(ana("12.10.2026", "DE-1503", "08:00", "20:10", "1103"), ana("13.10.2026", "DE-1503", "08:00", "20:10", "1103")),
                List.<String[]>of(nokta("DE-1503", "1", "Sivas", "13:30", "13:40", "603")));
        assertThat(ayni.engelli()).isFalse();
        assertThat(ayni.gecerli()).hasSize(4);

        // Değişim sayfası yoksa ya da boşsa akış bugünküyle aynı (Faz 1 eşdeğerliği)
        assertThat(SeferExcelOkuyucu.oku(TestExcel.sefer(TestExcel.BASLIK, ana), TANIMLAR).engelli()).isFalse();
        assertThat(oku(ana, List.of()).engelli()).isFalse();
    }
}
