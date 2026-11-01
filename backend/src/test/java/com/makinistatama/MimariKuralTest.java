package com.makinistatama;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

/**
 * Mimari kurallar (MK-15): kaynak dosyalardaki import taraması, ArchUnit yok. Henüz var olmayan paketlerde boşuna geçer, paket doğunca devreye
 * girer. Kuralların kendisi de {@code kural*} testleriyle sınanır: ihlal içeren örnek kaynak verilince gerçekten yakalamalı.
 */
class MimariKuralTest {

    private static final String KOK = "com.makinistatama.";
    private static final Pattern IMPORT = Pattern.compile("^import\\s+(?:static\\s+)?([\\w.]+?)(\\.\\*)?;", Pattern.MULTILINE);

    /** A -> B: A'nın import edebildiği paketler (ya da "paket.Sinif"). {@code ortak} ve rol beyanı işaretleri (HERKESE_ACIK_ISARET) herkese açık. */
    private static final Map<String, Set<String>> YON = Map.ofEntries(
            Map.entry("ortak", Set.of()),
            Map.entry("kural", Set.of()),
            Map.entry("cikti", Set.of()),
            Map.entry("kullanici", Set.of()),
            Map.entry("guvenlik", Set.of("kullanici")),
            Map.entry("degisiklik", Set.of("guvenlik.OturumKullanicisi")),
            Map.entry("tanim", Set.of("degisiklik")),
            Map.entry("parametre", Set.of("degisiklik", "kural", "tanim")), // kural: ParametreServisi ham değerleri saf Parametreler kaydına çevirir; tanim: OzelKuralServisi kapsam kimliklerini doğrular (MK-24)
            Map.entry("kurum", Set.of("degisiklik")), // F1 MK-167: kurum adı + logo
            Map.entry("makinist", Set.of("tanim", "parametre", "degisiklik")),
            Map.entry("kurulum", Set.of("kurum", "tanim", "makinist", "sefer", "degisiklik")), // F1 MK-171: kurulum durumu veriden türetilir, hiçbir paket kurulum'u import etmez
            Map.entry("sefer", Set.of("tanim", "degisiklik", "parametre")), // parametre: yükleme parça süre sınırı ayarı (sefer.parca_azami_saat; F2c tur 2, MK-66)
            Map.entry("atama", Set.of("sefer", "makinist", "tanim", "parametre", "degisiklik", "kural", "kullanici")), // kullanici: gördüm görünürlüğü "hesap yok" (MK-33, yaprak paket)
            Map.entry("rapor", Set.of("atama", "sefer", "makinist", "tanim", "parametre", "degisiklik", "kural", "cikti")), // degisiklik: denetim raporu kaydı okur
            Map.entry("hesap", Set.of("kullanici", "makinist", "degisiklik", "guvenlik.KullaniciKimligi")), // MK-27: denetimli hesap sarmalayıcısı; hiçbir paketten import edilmez
            Map.entry("takas", Set.of("atama", "makinist", "sefer", "parametre", "degisiklik")), // MK-34: takas / bırakma talebi (kural motoru ve kullanici'yi görmez; kural kontrolü atama içinde)
            Map.entry("benim", Set.of("guvenlik.KullaniciKimligi", "makinist", "atama", "sefer", "rapor", "parametre", "takas", "konaklama")), // MK-28: makinist uçları (takas F2a-3; konaklama F2b OK-10: yalnız kendi kaydı)
            Map.entry("konaklama", Set.of("atama", "makinist", "tanim", "parametre", "degisiklik", "kural", "cikti")), // F2b MK-43: hiçbir paket konaklama'yı import etmez (benim hariç, OK-10)
            Map.entry("demo", Set.of("tanim", "makinist", "sefer", "parametre", "kullanici"))); // kullanici: demo makinist hesapları (yaprak paket, MK-33 ile aynı gerekçe)

    /** Rol beyanı işaretleri herkese açıktır (MK-37): controller'lar Rol import'una ihtiyaç duymaz. */
    private static final Set<String> HERKESE_ACIK_ISARET = Set.of("guvenlik.SadeceYonetici", "guvenlik.Personel", "guvenlik.HerkesGiris", "guvenlik.Acik", "guvenlik.SadeceMakinist");

    private static final List<String> SAFA_YASAK = List.of("org.springframework.", "jakarta.", "tools.jackson.", "com.fasterxml.",
            "java.io.", "java.nio.file.", "java.net.", "java.sql.", "javax.sql.");

    /** Kaynak: {@code "paket/Sinif.java"} (KOK'e göre) -> içerik. Dönüş: ihlal listesi (boşsa temiz). */
    static List<String> ihlaller(Map<String, String> kaynaklar) {
        Set<String> entityler = new HashSet<>();
        kaynaklar.forEach((yol, icerik) -> {
            if (icerik.contains("@Entity")) {
                entityler.add(paketi(yol) + "." + sinifi(yol));
            }
        });
        List<String> ihlal = new ArrayList<>();
        kaynaklar.forEach((yol, icerik) -> {
            String paket = paketi(yol);
            String sinif = sinifi(yol);
            if (paket.isEmpty()) {
                return; // uygulama başlatıcı sınıfı
            }
            if (!YON.containsKey(paket)) {
                ihlal.add(yol + ": paket yön tablosunda yok");
            }
            Set<String> izin = YON.getOrDefault(paket, Set.of());

            // saf bileşenler çerçeveye/IO'ya bağlanmaz: kural paketi, otomatik atama çekirdeği (MK-8), yolculuk önerisi çekirdeği (F2b, MK-41), sefer parça hesabı (MK-120/121)
            if (paket.equals("kural") || (paket.equals("atama") && Set.of("OtomatikAtama", "YolculukOnerisi", "Iyilestirme", "KadroAnalitigi", "KadroArama").contains(sinif))
                    || (paket.equals("sefer") && sinif.equals("ParcaHesabi"))) {
                SAFA_YASAK.stream().filter(icerik::contains).forEach(y -> ihlal.add(yol + ": saf bileşen '" + y + "' kullanamaz"));
            }

            Matcher m = IMPORT.matcher(icerik);
            while (m.find()) {
                String ad = m.group(1);
                if (!ad.startsWith(KOK)) {
                    continue;
                }
                if (m.group(2) != null) {
                    ihlal.add(yol + ": joker (*) proje import'u yasak: " + ad);
                    continue;
                }
                String[] parca = ad.substring(KOK.length()).split("\\.");
                int i = 0;
                while (i < parca.length && !Character.isUpperCase(parca[i].charAt(0))) {
                    i++;
                }
                String hedefPaket = String.join(".", Arrays.copyOfRange(parca, 0, i));
                String hedefSinif = i < parca.length ? parca[i] : "";
                String hedef = hedefPaket + "." + hedefSinif;

                if (hedefPaket.equals(paket)) {
                    // aynı paket: serbest
                } else if (paket.equals("kural")) {
                    ihlal.add(yol + ": kural paketi başka paketi import edemez: " + ad);
                } else if (hedefPaket.equals("ortak") || HERKESE_ACIK_ISARET.contains(hedef)) {
                    // herkese açık
                } else if (!(izin.contains(hedefPaket) || izin.contains(hedef))) {
                    ihlal.add(yol + ": yön tablosu dışı import (" + paket + " -> " + hedefPaket + "): " + ad);
                }
                if (sinif.endsWith("Controller") && (hedefSinif.endsWith("Repository") || entityler.contains(hedef))) {
                    ihlal.add(yol + ": controller repository/entity import edemez: " + ad);
                }
                if (!hedefPaket.equals(paket) && hedefSinif.endsWith("Repository")) {
                    ihlal.add(yol + ": başka paketin repository'si kullanılamaz: " + ad);
                }
            }

            if (icerik.contains("aktifDetaylar()")) { // F3b MK-83: havuz türü zorunlu; sessiz "hepsi" aşırı yüklemesi geri gelmesin
                ihlal.add(yol + ": aktifDetaylar() tür parametresiz çağrılamaz/tanımlanamaz (MK-83)");
            }

            for (String yasak : List.of("@OneToMany", "@ManyToMany", "@OneToOne", "@ManyToOne", "FetchType.EAGER")) {
                if (icerik.contains(yasak)) {
                    ihlal.add(yol + ": " + yasak + " yasak (düz Long FK)");
                }
            }
        });
        return ihlal;
    }

    private static String paketi(String yol) {
        int son = yol.lastIndexOf('/');
        return son < 0 ? "" : yol.substring(0, son).replace('/', '.');
    }

    private static String sinifi(String yol) {
        return yol.substring(yol.lastIndexOf('/') + 1).replace(".java", "");
    }

    // ---------------------------------------------------------------- gerçek kaynak ağacı

    @Test
    void gercekKaynaklar_kurallaraUyar() throws IOException {
        Path kok = Path.of("src/main/java/com/makinistatama");
        Map<String, String> kaynaklar = new TreeMap<>();
        try (Stream<Path> dosyalar = Files.walk(kok)) {
            for (Path p : dosyalar.filter(f -> f.toString().endsWith(".java")).toList()) {
                kaynaklar.put(kok.relativize(p).toString().replace('\\', '/'), Files.readString(p));
            }
        }
        assertThat(kaynaklar).as("kaynak ağacı bulunamadı: çalışma dizini backend/ olmalı").isNotEmpty();
        assertThat(ihlaller(kaynaklar)).isEmpty();
    }

    // ---------------------------------------------------------------- kuralların kendisi (ihlali yakalar mı?)

    private static List<String> ihlalle(String yol, String icerik) {
        Map<String, String> k = new HashMap<>();
        k.put(yol, icerik);
        return ihlaller(k);
    }

    @Test
    void havuzTuruZorunlu_argumansizAktifDetaylarYakalanir() {
        assertThat(ihlalle("atama/AtamaServisi.java", "class A { void f() { makinistler.aktifDetaylar(); } }")).isNotEmpty();
        assertThat(ihlalle("rapor/YedekServisi.java", "class A { void f() { makinistler.aktifDetaylar(PersonelTuru.MAKINISTLER); } }")).isEmpty();
    }

    @Test
    void kural_temizKaynakGecer() {
        assertThat(ihlalle("makinist/MakinistServisi.java", """
                package com.makinistatama.makinist;
                import com.makinistatama.tanim.TanimServisi;
                import com.makinistatama.ortak.UygulamaHatasi;
                import com.makinistatama.guvenlik.SadeceYonetici;
                import org.springframework.stereotype.Service;
                """)).isEmpty();
    }

    @Test
    void kural_saflikVeKuralPaketiBagimsizligi() {
        assertThat(ihlalle("kural/KuralMotoru.java", "import org.springframework.stereotype.Component;")).isNotEmpty();
        assertThat(ihlalle("kural/KuralMotoru.java", "import java.io.File;")).isNotEmpty();
        assertThat(ihlalle("kural/KuralMotoru.java", "import jakarta.persistence.Entity;")).isNotEmpty();
        assertThat(ihlalle("kural/KuralMotoru.java", "import com.makinistatama.ortak.UygulamaHatasi;")).isNotEmpty();
        assertThat(ihlalle("kural/KuralMotoru.java", "import java.time.Instant;\nimport java.util.List;")).isEmpty();
        assertThat(ihlalle("atama/OtomatikAtama.java", "import org.springframework.stereotype.Service;")).isNotEmpty();
        assertThat(ihlalle("atama/OtomatikAtama.java", "import com.makinistatama.kural.KuralMotoru;")).isEmpty();
        assertThat(ihlalle("atama/YolculukOnerisi.java", "import org.springframework.stereotype.Service;")).isNotEmpty(); // F2b: yolculuk önerisi çekirdeği de saf
        assertThat(ihlalle("atama/YolculukOnerisi.java", "import com.makinistatama.kural.KuralMotoru;")).isEmpty();
        assertThat(ihlalle("atama/Iyilestirme.java", "import org.springframework.stereotype.Service;")).isNotEmpty(); // F2d: iyileştirme döngüsü de saf (MK-66)
        assertThat(ihlalle("atama/AtamaServisi.java", "import org.springframework.stereotype.Service;")).isEmpty();
    }

    @Test
    void kural_parcaHesabiSaf() { // MK-120/121: sefer.ParcaHesabi çerçeveye/IO'ya bağlanmaz; aynı paketin öbür sınıfları serbest kalır
        assertThat(ihlalle("sefer/ParcaHesabi.java", "import org.springframework.stereotype.Component;")).isNotEmpty();
        assertThat(ihlalle("sefer/ParcaHesabi.java", "import jakarta.persistence.Entity;")).isNotEmpty();
        assertThat(ihlalle("sefer/ParcaHesabi.java", "import java.io.File;")).isNotEmpty();
        assertThat(ihlalle("sefer/ParcaHesabi.java", "import java.time.Instant;\nimport java.util.List;\nimport com.makinistatama.ortak.ZamanAyari;")).isEmpty();
        assertThat(ihlalle("sefer/SeferServisi.java", "import org.springframework.stereotype.Service;")).isEmpty();
    }

    @Test
    void kural_controllerRepositoryVeEntityGoremez() {
        Map<String, String> k = new HashMap<>();
        k.put("sefer/Sefer.java", "@Entity class Sefer {}");
        k.put("sefer/SeferController.java",
                "import com.makinistatama.sefer.SeferRepository;\nimport com.makinistatama.sefer.Sefer;\nimport com.makinistatama.sefer.SeferServisi;");
        assertThat(ihlaller(k)).hasSize(2);
    }

    @Test
    void kural_baskaPaketinRepositorysiKullanilamaz() {
        assertThat(ihlalle("atama/AtamaServisi.java", "import com.makinistatama.makinist.MakinistRepository;")).isNotEmpty();
        assertThat(ihlalle("atama/AtamaServisi.java", "import com.makinistatama.atama.AtamaRepository;")).isEmpty();
    }

    @Test
    void kural_yonTablosu() {
        assertThat(ihlalle("makinist/MakinistServisi.java", "import com.makinistatama.atama.AtamaServisi;")).isNotEmpty(); // makinist -> atama yok
        assertThat(ihlalle("sefer/SeferServisi.java", "import com.makinistatama.atama.AtamaServisi;")).isNotEmpty();       // sefer -> atama yok
        assertThat(ihlalle("atama/AtamaServisi.java", "import com.makinistatama.sefer.SeferServisi;")).isEmpty();
        assertThat(ihlalle("rapor/BelgeDurumuServisi.java", "import com.makinistatama.atama.AtamaServisi;")).isEmpty();
        assertThat(ihlalle("kullanici/KullaniciServisi.java", "import com.makinistatama.guvenlik.OturumKullanicisi;")).isNotEmpty();
        assertThat(ihlalle("degisiklik/DegisiklikServisi.java", "import com.makinistatama.guvenlik.OturumKullanicisi;")).isEmpty();
        assertThat(ihlalle("degisiklik/DegisiklikServisi.java", "import com.makinistatama.guvenlik.KullaniciKimligi;")).isNotEmpty();
        assertThat(ihlalle("bilinmeyen/X.java", "class X {}")).isNotEmpty();
        assertThat(ihlalle("sefer/SeferServisi.java", "import com.makinistatama.tanim.*;")).isNotEmpty(); // joker yasak
        assertThat(ihlalle("sefer/SeferYuklemeServisi.java", "import com.makinistatama.parametre.AyarServisi;")).isEmpty(); // MK-66: sefer -> parametre (parça süre sınırı ayarı)
        assertThat(ihlalle("parametre/AyarServisi.java", "import com.makinistatama.sefer.SeferServisi;")).isNotEmpty(); // ters yön yok
    }

    @Test
    void kural_ozelKuralYeniKenarlar() {
        assertThat(ihlalle("parametre/OzelKuralServisi.java", "import com.makinistatama.tanim.TanimServisi;")).isEmpty(); // MK-24: parametre -> tanim
        assertThat(ihlalle("parametre/OzelKuralServisi.java", "import com.makinistatama.makinist.MakinistServisi;")).isNotEmpty();
        assertThat(ihlalle("parametre/ParametreServisi.java", "import com.makinistatama.atama.AtamaServisi;")).isNotEmpty(); // önizleme atama'da kalır
        assertThat(ihlalle("atama/OzelKuralOnizleme.java", "import com.makinistatama.parametre.OzelKuralServisi;")).isEmpty();
        assertThat(ihlalle("sefer/SeferServisi.java", "import com.makinistatama.kural.Kapsam;")).isNotEmpty(); // MK-23: tren türü String, sefer kural'ı görmez
        assertThat(ihlalle("tanim/TanimServisi.java", "import com.makinistatama.parametre.OzelKuralServisi;")).isNotEmpty();
        assertThat(ihlalle("kural/Kapsam.java", "import tools.jackson.databind.annotation.JsonDeserialize;")).isNotEmpty(); // Kapsam anotasyonsuz
        assertThat(ihlalle("kural/Kapsam.java", "import com.fasterxml.jackson.annotation.JsonProperty;")).isNotEmpty();
        assertThat(ihlalle("kural/Kapsam.java", "import java.util.Set;")).isEmpty();
        assertThat(ihlalle("kural/OzelKuralDegerlendirici.java", "import com.makinistatama.ortak.UygulamaHatasi;")).isNotEmpty();
    }

    @Test
    void kural_hesapBenimKenarlari() {
        assertThat(ihlalle("hesap/HesapServisi.java", """
                import com.makinistatama.kullanici.KullaniciServisi;
                import com.makinistatama.degisiklik.DegisiklikServisi;
                import com.makinistatama.guvenlik.KullaniciKimligi;
                """)).isEmpty();
        assertThat(ihlalle("hesap/HesapServisi.java", "import com.makinistatama.atama.AtamaServisi;")).isNotEmpty(); // hesap -> atama yok
        assertThat(ihlalle("hesap/HesapServisi.java", "import com.makinistatama.guvenlik.OturumKullanicisi;")).isNotEmpty(); // yalnız guvenlik.KullaniciKimligi
        assertThat(ihlalle("guvenlik/AuthController.java", "import com.makinistatama.hesap.HesapServisi;")).isNotEmpty(); // guvenlik -> hesap yok
        assertThat(ihlalle("makinist/MakinistServisi.java", "import com.makinistatama.kullanici.KullaniciServisi;")).isNotEmpty(); // makinist -> kullanici yok
        assertThat(ihlalle("kullanici/KullaniciServisi.java", "import com.makinistatama.degisiklik.DegisiklikServisi;")).isNotEmpty(); // kullanici -> degisiklik yok
        assertThat(ihlalle("benim/BenimServisi.java", "import com.makinistatama.degisiklik.DegisiklikServisi;")).isNotEmpty(); // benim -> degisiklik yok
        assertThat(ihlalle("rapor/BelgeDurumuServisi.java", "import com.makinistatama.hesap.HesapServisi;")).isNotEmpty();
    }

    /** YON tablosu bir paket döngüsü içermemeli (MK-37): "paket.Sinif" girişlerinde yalnız paket kısmı kenardır. */
    @Test
    void yonTablosuDongusuzdur() {
        Map<String, Set<String>> graf = new HashMap<>();
        YON.forEach((p, hedefler) -> graf.put(p, hedefler.stream().map(h -> h.contains(".") ? h.substring(0, h.indexOf('.')) : h).filter(h -> !h.equals(p)).collect(java.util.stream.Collectors.toSet())));
        Set<String> bitti = new HashSet<>();
        for (String p : graf.keySet()) {
            assertThat(donguVar(p, graf, bitti, new java.util.ArrayDeque<>())).as("paket döngüsü: %s", p).isFalse();
        }
    }

    private static boolean donguVar(String p, Map<String, Set<String>> graf, Set<String> bitti, java.util.Deque<String> yol) {
        if (yol.contains(p)) {
            return true;
        }
        if (bitti.contains(p)) {
            return false;
        }
        yol.push(p);
        for (String h : graf.getOrDefault(p, Set.of())) {
            if (donguVar(h, graf, bitti, yol)) {
                return true;
            }
        }
        yol.pop();
        bitti.add(p);
        return false;
    }

    @Test
    void kural_entityIliskileriYasak() {
        assertThat(ihlalle("sefer/Sefer.java", "@Entity class Sefer { @OneToMany List<X> x; }")).isNotEmpty();
        assertThat(ihlalle("sefer/Sefer.java", "@Entity class Sefer { Long kalkisGarId; }")).isEmpty();
    }
}
