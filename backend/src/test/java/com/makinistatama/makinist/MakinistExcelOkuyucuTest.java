package com.makinistatama.makinist;

import static com.makinistatama.makinist.TestMakinistExcel.makinist;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayInputStream;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

import com.makinistatama.makinist.MakinistExcelOkuyucu.Sonuc;
import com.makinistatama.makinist.MakinistExcelOkuyucu.Tanimlar;
import com.makinistatama.ortak.AramaMetni;
import com.makinistatama.ortak.UygulamaHatasi;

/** F1 / MK-170: makinist Excel okuyucu (saf dosya doğrulaması). Hata matrisi AK-1.3: her durum AYRI bir testtir. */
class MakinistExcelOkuyucuTest {

    private static final LocalDate BUGUN = LocalDate.of(2026, 11, 1);
    private static final Tanimlar TANIMLAR = new Tanimlar(
            Map.of(AramaMetni.katla("Ankara"), 1L, AramaMetni.katla("Eskişehir"), 2L),
            Map.of(AramaMetni.katla("Ankara–Eskişehir"), 10L),
            Map.of(AramaMetni.katla("YHT"), 20L, AramaMetni.katla("DM"), 21L));

    private static Sonuc oku(List<Object[]> m) {
        return oku(m, List.of(), List.of());
    }

    private static Sonuc oku(List<Object[]> m, List<Object[]> arac, List<Object[]> hat) {
        return MakinistExcelOkuyucu.oku(TestMakinistExcel.kitap(m, arac, hat), TANIMLAR, BUGUN);
    }

    private static List<Object[]> satirlar(Object[]... s) {
        return List.of(s);
    }

    private static void tekHata(Sonuc s, String sayfa, int satirNo, String parca) {
        assertThat(s.hatalar()).hasSize(1);
        var h = s.hatalar().getFirst();
        assertThat(h.sayfa()).isEqualTo(sayfa);
        assertThat(h.satirNo()).isEqualTo(satirNo);
        assertThat(h.neden()).contains(parca);
    }

    @Test
    void gecerliDosya_uchSayfa_tumAlanlar() {
        Sonuc s = oku(satirlar(makinist("d001", " Ayşe Yılmaz ", "Makinist", "ANKARA", "Aktif", "31.12.2030", "15.5.2029", LocalDate.of(2031, 1, 2), "01.02.2026"),
                makinist("K001", "Kenan Kara", "Kondüktör", "eskisehir", "Askıda", "31.12.2030", "31.12.2030", "31.12.2030", null)),
                satirlar(new Object[] { "D001", "yht", "01.03.2026" }, new Object[] { "D001", "DM", LocalDate.of(2026, 3, 2) }),
                satirlar(new Object[] { "D001", "Ankara–Eskişehir", "01.03.2026", "01.09.2026" }));

        assertThat(s.hatalar()).isEmpty();
        assertThat(s.toplam()).isEqualTo(2);
        assertThat(s.gecerli()).hasSize(2);
        var d = s.gecerli().getFirst();
        assertThat(d.satirNo()).isEqualTo(2);
        var i = d.istek();
        assertThat(i.sicil()).as("kırpılmış, büyük harf").isEqualTo("D001");
        assertThat(i.adSoyad()).isEqualTo("Ayşe Yılmaz");
        assertThat(i.tur()).isEqualTo(PersonelTuru.MAKINIST);
        assertThat(i.durum()).isEqualTo(MakinistDurum.AKTIF);
        assertThat(i.bagliGarId()).as("Türkçe-duyarsız gar adı").isEqualTo(1L);
        assertThat(i.ehliyetBitis()).isEqualTo(LocalDate.of(2030, 12, 31));
        assertThat(i.saglikBitis()).isEqualTo(LocalDate.of(2029, 5, 15));
        assertThat(i.psikoteknikBitis()).as("Excel tarih hücresi").isEqualTo(LocalDate.of(2031, 1, 2));
        assertThat(i.sonSurus()).isEqualTo(LocalDate.of(2026, 2, 1));
        assertThat(i.broveAracTipleri()).extracting(MakinistIstegi.BroveAracTipiIstegi::aracTipiId).containsExactlyInAnyOrder(20L, 21L);
        assertThat(i.broveHatlar()).hasSize(1);
        assertThat(i.broveHatlar().getFirst().sonSurus()).isEqualTo(LocalDate.of(2026, 9, 1));
        var k = s.gecerli().get(1).istek();
        assertThat(k.tur()).isEqualTo(PersonelTuru.KONDUKTOR);
        assertThat(k.durum()).isEqualTo(MakinistDurum.ASKIDA);
        assertThat(k.bagliGarId()).isEqualTo(2L);
        assertThat(s.eksikTanimlar()).isEmpty();
    }

    @Test
    void turVeDurumBos_makinistVeAktifSayilir_turSutunuYokOlabilir() {
        Sonuc s = oku(satirlar(makinist("D001", "Ayşe", "Ankara")));
        assertThat(s.gecerli().getFirst().istek().tur()).isEqualTo(PersonelTuru.MAKINIST);
        assertThat(s.gecerli().getFirst().istek().durum()).isEqualTo(MakinistDurum.AKTIF);

        List<String> baslik = List.of("Sicil", "Ad Soyad", "Bağlı Gar", "Ehliyet Bitiş", "Sağlık Bitiş", "Psikoteknik Bitiş");
        Sonuc dar = MakinistExcelOkuyucu.oku(TestMakinistExcel.kitap(baslik, satirlar(new Object[] { "D002", "Veli", "Ankara", "31.12.2030", "31.12.2030", "31.12.2030" }), null, null), TANIMLAR, BUGUN);
        assertThat(dar.hatalar()).isEmpty();
        assertThat(dar.gecerli()).hasSize(1);
    }

    // ---------------------------------------------------------------- AK-1.3 hata matrisi (11 durum, ayrı ayrı)

    @Test
    void hata01_bosSicil() {
        tekHata(oku(satirlar(makinist(null, "Ayşe", "Ankara"))), "Makinistler", 2, "Sicil boş");
    }

    @Test
    void hata02_sicil20KarakteriAsar() {
        Sonuc s = oku(satirlar(makinist("S".repeat(21), "Ayşe", "Ankara")));
        tekHata(s, "Makinistler", 2, "en fazla 20");
        assertThat(oku(satirlar(makinist("S".repeat(20), "Ayşe", "Ankara"))).hatalar()).as("sınır dahil").isEmpty();
    }

    @Test
    void hata03_adBos() {
        tekHata(oku(satirlar(makinist("D001", "  ", "Ankara"))), "Makinistler", 2, "Ad soyad boş");
    }

    @Test
    void hata04_bilinmeyenGar_veEksikTanimListesi() {
        Sonuc s = oku(satirlar(makinist("D001", "Ayşe", "Sivas"), makinist("D002", "Veli", "Sivas"), makinist("D003", "Can", "Ankara")));
        assertThat(s.hatalar()).hasSize(2);
        assertThat(s.hatalar()).allSatisfy(h -> assertThat(h.neden()).contains("Bağlı gar bilinmiyor: 'Sivas'"));
        assertThat(s.gecerli()).hasSize(1);
        assertThat(s.eksikTanimlar()).as("benzersiz").containsExactly(new MakinistExcelOkuyucu.EksikTanim("GAR", "Sivas"));
    }

    @Test
    void hata05_gecersizDurum() {
        tekHata(oku(satirlar(makinist("D001", "Ayşe", null, "Ankara", "Tatil", "31.12.2030", "31.12.2030", "31.12.2030", null))), "Makinistler", 2, "Durum geçersiz");
    }

    @Test
    void hata06_bozukTarih() {
        tekHata(oku(satirlar(makinist("D001", "Ayşe", null, "Ankara", null, "31.02.2030", "31.12.2030", "31.12.2030", null))), "Makinistler", 2, "Ehliyet bitişi geçersiz");
        tekHata(oku(satirlar(makinist("D001", "Ayşe", null, "Ankara", null, "31.12.2030", "yarın", "31.12.2030", null))), "Makinistler", 2, "Sağlık raporu bitişi geçersiz");
        tekHata(oku(satirlar(makinist("D001", "Ayşe", null, "Ankara", null, "31.12.2030", "31.12.2030", "31.12.2030", "bozuk"))), "Makinistler", 2, "Son sürüş geçersiz");
        tekHata(oku(satirlar(makinist("D001", "Ayşe", null, "Ankara", null, "01.01.1999", "31.12.2030", "31.12.2030", null))), "Makinistler", 2, "arasında olmalı");
        tekHata(oku(satirlar(makinist("D001", "Ayşe", null, "Ankara", null, "01.01.2100", "31.12.2030", "31.12.2030", null))), "Makinistler", 2, "arasında olmalı");
    }

    @Test
    void hata07_eksikZorunluBelgeTarihi() {
        Sonuc s = oku(satirlar(makinist("D001", "Ayşe", null, "Ankara", null, null, null, "31.12.2030", null)));
        assertThat(s.hatalar()).hasSize(1);
        assertThat(s.hatalar().getFirst().neden()).contains("Ehliyet bitişi boş").contains("Sağlık raporu bitişi boş");
        assertThat(s.gecerli()).isEmpty();
    }

    @Test
    void hata08_dosyadaTekrarSicil_ilkSatirGecerliKalir() {
        Sonuc s = oku(satirlar(makinist("D001", "Ayşe", "Ankara"), makinist("d001", "Ayşe İkinci", "Ankara")));
        tekHata(s, "Makinistler", 3, "dosyada tekrar");
        assertThat(s.hatalar().getFirst().neden()).contains("ilk satır 2");
        assertThat(s.gecerli()).hasSize(1);
        assertThat(s.gecerli().getFirst().istek().adSoyad()).isEqualTo("Ayşe");
    }

    @Test
    void hata09_broveSicilDosyadaYok() {
        Sonuc s = oku(satirlar(makinist("D001", "Ayşe", "Ankara")), satirlar(new Object[] { "D999", "YHT", "01.03.2026" }), List.of());
        tekHata(s, "Araç Tipi Bröveleri", 2, "Makinistler sayfasında");
        assertThat(s.gecerli().getFirst().istek().broveAracTipleri()).isEmpty();
        // geçersiz makinist satırının bröveleri de sahipsizdir
        Sonuc t = oku(satirlar(makinist("D002", "Veli", "Sivas")), List.of(), satirlar(new Object[] { "D002", "Ankara–Eskişehir", "01.03.2026", null }));
        assertThat(t.hatalar()).hasSize(2);
        assertThat(t.hatalar().get(1).sayfa()).isEqualTo("Hat Bröveleri");
    }

    @Test
    void hata10_kondukturdeBrove() {
        Sonuc s = oku(satirlar(makinist("K001", "Kenan", "Kondüktör", "Ankara", null, "31.12.2030", "31.12.2030", "31.12.2030", null)),
                satirlar(new Object[] { "K001", "YHT", "01.03.2026" }), satirlar(new Object[] { "K001", "Ankara–Eskişehir", "01.03.2026", null }));
        assertThat(s.hatalar()).hasSize(2);
        assertThat(s.hatalar()).allSatisfy(h -> assertThat(h.neden()).contains("Kondüktör kartında bröve"));
        assertThat(s.gecerli()).as("kondüktör brövesiz yüklenir").hasSize(1);
        assertThat(s.gecerli().getFirst().istek().broveAracTipleri()).isEmpty();
    }

    @Test
    void hata11_ayniAracTipiIkiKez() {
        Sonuc s = oku(satirlar(makinist("D001", "Ayşe", "Ankara")), satirlar(new Object[] { "D001", "YHT", "01.03.2026" }, new Object[] { "d001", "yht", "02.03.2026" }), List.of());
        tekHata(s, "Araç Tipi Bröveleri", 3, "iki kez");
        assertThat(s.gecerli().getFirst().istek().broveAracTipleri()).hasSize(1);
        Sonuc h = oku(satirlar(makinist("D001", "Ayşe", "Ankara")), List.of(),
                satirlar(new Object[] { "D001", "Ankara–Eskişehir", "01.03.2026", null }, new Object[] { "D001", "Ankara–Eskişehir", "02.03.2026", null }));
        tekHata(h, "Hat Bröveleri", 3, "iki kez");
    }

    @Test
    void broveTanimiBilinmiyor_eksikTanimaAracTipiVeHatGirer() {
        Sonuc s = oku(satirlar(makinist("D001", "Ayşe", "Ankara")), satirlar(new Object[] { "D001", "TCDD-Yok", "01.03.2026" }),
                satirlar(new Object[] { "D001", "Sivas–Kars", "01.03.2026", null }));
        assertThat(s.eksikTanimlar()).containsExactly(new MakinistExcelOkuyucu.EksikTanim("ARAC_TIPI", "TCDD-Yok"), new MakinistExcelOkuyucu.EksikTanim("HAT", "Sivas–Kars"));
    }

    @Test
    void gecmisBitisTarihi_gecerli_ama_uyariVar() {
        Sonuc s = oku(satirlar(makinist("D001", "Ayşe", null, "Ankara", null, "31.12.2025", "31.12.2030", "31.12.2030", null)));
        assertThat(s.hatalar()).isEmpty();
        assertThat(s.gecerli()).hasSize(1);
        assertThat(s.gecerli().getFirst().uyarilar()).hasSize(1).first().asString().contains("Ehliyet bitişi").contains("31.12.2025").contains("süresi dolmuş");
    }

    @Test
    void sablonOrnekSatiri_ORNEKOnekli_yuklenemez() throws Exception {
        byte[] sablon = MakinistExcelOkuyucu.sablon();
        try (XSSFWorkbook k = new XSSFWorkbook(new ByteArrayInputStream(sablon))) {
            assertThat(k.getNumberOfSheets()).isEqualTo(3);
            assertThat(k.getSheetAt(0).getSheetName()).isEqualTo("Makinistler");
            assertThat(k.getSheetAt(1).getSheetName()).isEqualTo("Araç Tipi Bröveleri");
            assertThat(k.getSheetAt(2).getSheetName()).isEqualTo("Hat Bröveleri");
            List<String> b = new ArrayList<>();
            k.getSheetAt(0).getRow(0).forEach(c -> b.add(c.getStringCellValue()));
            assertThat(b).isEqualTo(TestMakinistExcel.MAKINIST_BASLIK);
            assertThat(k.getSheetAt(0).getRow(1).getCell(0).getStringCellValue()).startsWith("ORNEK-");
            assertThat(k.getSheetAt(1).getRow(1).getCell(0).getStringCellValue()).startsWith("ORNEK-");
        }
        Sonuc s = MakinistExcelOkuyucu.oku(sablon, TANIMLAR, BUGUN);
        assertThat(s.gecerli()).as("silinmeden yüklenen örnek satır gerçek makinist olamaz").isEmpty();
        assertThat(s.hatalar()).anySatisfy(h -> assertThat(h.neden()).contains("örnek satır"));
    }

    // ---------------------------------------------------------------- sınırlar ve dosya güvenliği (AK-1.5)

    @Test
    void makinistSayfasi5000SatiriAsarsa_400() {
        List<Object[]> cok = new ArrayList<>();
        for (int i = 0; i < 5_001; i++) {
            cok.add(makinist("S" + i, "Ad " + i, "Ankara"));
        }
        assertThatThrownBy(() -> oku(cok)).isInstanceOf(UygulamaHatasi.class).hasMessageContaining("'Makinistler' sayfasında en çok 5000 satır")
                .satisfies(e -> assertThat(((UygulamaHatasi) e).kod()).isEqualTo("GECERSIZ_DOSYA"));
        cok.removeLast();
        assertThat(oku(cok).gecerli()).as("tam 5000 satır kabul").hasSize(5_000);
    }

    @Test
    void broveSayfasi20000SatiriAsarsa_400() {
        List<Object[]> brove = new ArrayList<>();
        for (int i = 0; i < 20_001; i++) {
            brove.add(new Object[] { "D001", "YHT", "01.03.2026" });
        }
        assertThatThrownBy(() -> oku(satirlar(makinist("D001", "Ayşe", "Ankara")), brove, List.of())).isInstanceOf(UygulamaHatasi.class)
                .hasMessageContaining("en çok 20000 satır");
    }

    @Test
    void xlsMetinBosVeEksikSayfa_400() {
        byte[] xls = { (byte) 0xD0, (byte) 0xCF, 0x11, (byte) 0xE0, (byte) 0xA1, (byte) 0xB1, 0x1A, (byte) 0xE1, 0, 0 };
        assertThatThrownBy(() -> MakinistExcelOkuyucu.oku(xls, TANIMLAR, BUGUN)).isInstanceOf(UygulamaHatasi.class).hasMessageContaining(".xlsx");
        assertThatThrownBy(() -> MakinistExcelOkuyucu.oku("merhaba".getBytes(), TANIMLAR, BUGUN)).isInstanceOf(UygulamaHatasi.class);
        assertThatThrownBy(() -> MakinistExcelOkuyucu.oku(new byte[0], TANIMLAR, BUGUN)).isInstanceOf(UygulamaHatasi.class);
        assertThatThrownBy(() -> MakinistExcelOkuyucu.oku(com.makinistatama.sefer.TestExcel.sefer(List.of()), TANIMLAR, BUGUN)).isInstanceOf(UygulamaHatasi.class)
                .hasMessageContaining("'Makinistler' sayfası yok");
    }

    @Test
    void eksikZorunluSutun_400() {
        List<String> baslik = List.of("Sicil", "Ad Soyad", "Bağlı Gar", "Ehliyet Bitiş", "Sağlık Bitiş");
        assertThatThrownBy(() -> MakinistExcelOkuyucu.oku(TestMakinistExcel.kitap(baslik, List.of(), null, null), TANIMLAR, BUGUN)).isInstanceOf(UygulamaHatasi.class)
                .hasMessageContaining("eksik sütun: Psikoteknik Bitiş");
    }

    @Test
    void bosSatirlarAtlanir_toplamDoluSatirdir() {
        Sonuc s = oku(satirlar(makinist("D001", "Ayşe", "Ankara"), new Object[] {}, makinist("D002", "Veli", "Ankara")));
        assertThat(s.toplam()).isEqualTo(2);
        assertThat(s.gecerli()).extracting(MakinistExcelOkuyucu.Satir::satirNo).containsExactly(2, 4);
    }
}
