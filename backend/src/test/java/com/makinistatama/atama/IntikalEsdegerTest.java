package com.makinistatama.atama;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.makinistatama.TestVeritabani;
import com.makinistatama.atama.BaglamYukleyici.Yuk;
import com.makinistatama.kural.Gorev;
import com.makinistatama.makinist.MakinistDto;
import com.makinistatama.makinist.MakinistServisi;

/**
 * Serbest intikal (MK-105, NFR-01/NFR-03/NFR-04): (1) intikal tablosu BOŞKEN {@code SatirKaynagi.aralik} ile {@code AtamaRepository.aralik} aynı (kural çıktıları birebir aynı kalır);
 * (2) {@code AtamaRepository.aralik}'ı üretim kodunda yalnız {@code SatirKaynagi} çağırır (üçüncü çağıran intikali sessiz kaçırır); (3) kimlik güvenliği: {@code atamaId = null} olan iki
 * intikal ve takasın sentetik {@code atamaId = -1} adayı aynı yükte ayrı görevlerdir.
 */
class IntikalEsdegerTest extends TestVeritabani {

    static final LocalDate GUN = LocalDate.of(2026, 10, 14);

    @Autowired
    AtamaRepository depo;

    @Autowired
    SatirKaynagi kaynak;

    @Autowired
    BaglamYukleyici yukleyici;

    @Autowired
    MakinistServisi makinistler;

    AtamaVeri v;
    long m;

    @BeforeEach
    void kur() {
        v = new AtamaVeri(jdbc);
        m = v.makinist("D001");
    }

    @Test
    void bosIntikalTablosu_satirKaynagiRepositoryIleAyni() {
        v.atama(v.seferEv("E1", GUN, "08:00", "09:30"), m, "ASIL");
        v.atama(v.sefer("E2", GUN.plusDays(1), "08:00", "09:30"), m, "YOLCU");

        List<AtamaSatiri> beklenen = depo.aralik(List.of(m), GUN, GUN.plusDays(5));

        assertThat(beklenen).hasSize(2);
        assertThat(kaynak.aralik(List.of(m), GUN, GUN.plusDays(5))).containsExactlyInAnyOrderElementsOf(beklenen);
        assertThat(kaynak.aralik(List.of(), GUN, GUN)).isEmpty();
    }

    @Test
    void intikalGunPenceresindeDahil_gunDisindaDegil_iptalYok() {
        v.intikal(m, v.ankara, v.eskisehir, GUN, "07:00", "09:00");
        v.intikal(m, v.ankara, v.eskisehir, GUN.plusDays(1), "07:00", "09:00");
        long iptal = v.intikal(m, v.ankara, v.eskisehir, GUN, "10:00", "11:00");
        jdbc.update("UPDATE intikal SET durum = 'IPTAL' WHERE id = ?", iptal);

        List<AtamaSatiri> gun = kaynak.aralik(List.of(m), GUN, GUN);

        assertThat(gun).hasSize(1);
        AtamaSatiri s = gun.getFirst();
        assertThat(s.intikal()).isTrue();
        assertThat(s.atamaId()).isNull();
        assertThat(s.seferId()).isEqualTo(-1L);
        assertThat(s.rol()).isEqualTo(AtamaRol.YOLCU);
        assertThat(s.yolculuk()).isTrue();
        assertThat(s.trenNo()).isEqualTo("İntikal");
        assertThat(s.tarih()).isEqualTo(GUN);
        assertThat(kaynak.aralik(List.of(m), GUN, GUN.plusDays(1))).hasSize(2);
    }

    @Test
    void kimlikGuvenligi_ikiIntikalVeTakasAdayiAyriGorev() {
        v.intikal(m, v.ankara, v.eskisehir, GUN, "07:00", "08:00"); // id 1 -> seferId -1
        v.intikal(m, v.eskisehir, v.ankara, GUN, "09:00", "10:00"); // id 2 -> seferId -2
        MakinistDto dto = makinistler.detaylar(List.of(m)).getFirst();
        Yuk y = yukleyici.yukleAralik(GUN, GUN, List.of(dto));
        List<AtamaSatiri> satirlar = y.satirlar(m);

        Gorev ilk = y.gorev(satirlar.stream().filter(s -> s.seferId() == -1L).findFirst().orElseThrow());
        Gorev ikinci = y.gorev(satirlar.stream().filter(s -> s.seferId() == -2L).findFirst().orElseThrow());
        // takasın sentetik adayı: atamaId = -1, seferId gerçek (kimlik çakışması denemesi: intikal #1 aynı sayı)
        AtamaSatiri takasAdayi = new AtamaSatiri(-1L, 99L, AtamaRol.YOLCU, m, GUN, "T", v.ankara, v.eskisehir, 0L, 0L, satirlar.getFirst().planlananKalkis(),
                satirlar.getFirst().planlananVaris(), 0, 0, null, 0, null, null, null, false, false);
        Gorev takas = y.gorev(takasAdayi);

        assertThat(ilk.seferId()).isEqualTo(-1L);
        assertThat(ikinci.seferId()).isEqualTo(-2L);
        assertThat(ilk.varisGarId()).isEqualTo(v.eskisehir);
        assertThat(ikinci.varisGarId()).isEqualTo(v.ankara);
        assertThat(takas.seferId()).isEqualTo(99L);
    }

    @Test
    void kaynakTaramasi_atamaRepositoryAralikYalnizSatirKaynaginda() throws IOException {
        Pattern alan = Pattern.compile("AtamaRepository\\s+(\\w+)");
        List<String> ihlaller = new ArrayList<>();
        try (Stream<Path> dosyalar = Files.walk(Path.of("src/main/java"))) {
            for (Path p : dosyalar.filter(f -> f.toString().endsWith(".java")).toList()) {
                String icerik = Files.readString(p);
                Matcher a = alan.matcher(icerik);
                while (a.find()) {
                    if (Pattern.compile("\\b" + a.group(1) + "\\.aralik\\(").matcher(icerik).find() && !p.getFileName().toString().equals("SatirKaynagi.java")) {
                        ihlaller.add(p.getFileName() + ": " + a.group(1) + ".aralik(");
                    }
                }
            }
        }
        assertThat(ihlaller).as("AtamaRepository.aralik yalnız SatirKaynagi'nda çağrılır (MK-105)").isEmpty();
    }
}
