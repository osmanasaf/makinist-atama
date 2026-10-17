package com.makinistatama.atama;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.LongSupplier;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;

import com.makinistatama.TestVeritabani;
import com.makinistatama.atama.OtomatikAtama.Atandi;
import com.makinistatama.atama.OtomatikAtama.SeferGirdisi;
import com.makinistatama.demo.SabitTarihliDemo;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * QA (F3a): ek hesapların süre bütçesi SONSUZ (donmuş saat) iken sonuç makineden bağımsızdır ve belirleyicidir (NFR-02 gerçek koşulu); dağılım noktasındaki sanal makinistlerin gerçek iş
 * yükü ölçülür (yönetici için "inandırıcılık"). OK-22 sonrası başlık (eğri) zaten süreden bağımsızdır; bu test ek hesapların da sonsuz bütçede tamamlanıp birebir tekrarlandığını sınar.
 */
class KadroQaTest extends TestVeritabani {

    static final LocalDate BAS = LocalDate.of(2026, 10, 12);

    @Autowired
    ApplicationContext ctx;
    @Autowired
    ObjectMapper json;
    @Autowired
    KadroTahminServisi servis;

    private LongSupplier eskiSaat;
    private KadroTahminServisi hedef;

    @BeforeEach
    void kur() {
        SabitTarihliDemo.yukle(ctx);
        hedef = org.springframework.test.util.AopTestUtils.getTargetObject(servis); // @Transactional proxy: alan hedef nesnede
        eskiSaat = hedef.nanoSaat;
    }

    @AfterEach
    void geriAl() {
        hedef.nanoSaat = eskiSaat; // tekil bean: diğer testler gerçek saati kullanır
    }

    private String sabitJson(KadroRaporuDto r) {
        ObjectNode n = (ObjectNode) json.valueToTree(r);
        sureleriSil(n);
        return n.toString();
    }

    private static void sureleriSil(JsonNode n) {
        if (n instanceof ObjectNode o) {
            o.remove("sureMs");
            o.properties().forEach(e -> sureleriSil(e.getValue()));
        } else if (n.isArray()) {
            n.forEach(KadroQaTest::sureleriSil);
        }
    }

    @Test
    void sonsuzButce_demoDortHafta_ikiKosuBirebir_tamamlandi_veSanalMakinistYuku() {
        hedef.nanoSaat = () -> 0L; // süre bütçesi hiç dolmaz
        KadroIstegi istek = new KadroIstegi(BAS, 4, 13, true, false, 30, false, null);

        long t0 = System.nanoTime();
        KadroTahminServisi.Ic a = servis.hesaplaIc(istek);
        long ms = (System.nanoTime() - t0) / 1_000_000;
        KadroTahminServisi.Ic b = servis.hesaplaIc(istek);

        KadroRaporuDto.Simulasyon s = a.rapor().simulasyon();
        System.out.println("QA sonsuz bütçe 4 hafta: " + ms + " ms | mevcut " + s.mevcut() + " | hedefler " + s.hedefler() + " | dağılım (" + s.dagilimEtiketi() + ") " + s.dagilimEk() + " kişi");
        assertThat(s.tamamlandi()).as("bütçe sonsuzken ek hesaplar da tamamlanır").isTrue();
        assertThat(sabitJson(b.rapor())).as("NFR-02: bütçeye takılmayan koşu birebir aynı").isEqualTo(sabitJson(a.rapor()));

        // dağılım noktasındaki sanal makinist başına gerçek iş yükü (haftalık saat)
        Map<Long, Double> saat = new HashMap<>();
        Map<Long, SeferGirdisi> sefer = new HashMap<>();
        a.seferler().forEach(g -> sefer.put(g.seferId(), g));
        for (Atandi x : a.baslikSonuc().atananlar()) {
            if (x.makinistId() < 0) {
                saat.merge(x.makinistId(), sefer.get(x.seferId()).dakika() / 60.0, Double::sum);
            }
        }
        double haftalikOrt = saat.values().stream().mapToDouble(d -> d / 4.0).average().orElse(0);
        double enAz = saat.values().stream().mapToDouble(d -> d / 4.0).min().orElse(0);
        long sanalToplam = a.baslikKadro().stream().filter(m -> m.id() < 0).count();
        System.out.println("QA sanal makinist: " + sanalToplam + " kişi, iş alan " + saat.size() + ", haftalık ortalama " + String.format("%.1f", haftalikOrt) + " sa (en az "
                + String.format("%.1f", enAz) + ")");
        assertThat(sanalToplam).isEqualTo(s.dagilimEk());
        assertThat(saat).as("her sanal makinist en az bir sefer almış (aksi halde 'gereksiz ek' sayılır)").hasSize((int) sanalToplam);
    }

    @Test
    void kisaButce_dortHafta_baslikAyniEkHesaplarAtlanirVeIsaretli() {
        // 5 sn bütçe, her okumada 10 sn ilerleyen sahte saat (duvar saatinden bağımsız, belirleyici): eğri bütçeden bağımsız; süre yetmeyen ek hesaplar atlanır ve açıkça listelenir (sonuç "kısmi" diye gizlenmez)
        java.util.concurrent.atomic.AtomicLong saat = new java.util.concurrent.atomic.AtomicLong();
        hedef.nanoSaat = () -> saat.addAndGet(10_000_000_000L);
        KadroRaporuDto.Simulasyon s = servis.hesapla(new KadroIstegi(BAS, 4, 13, true, false, 5, false, null)).simulasyon();
        System.out.println("QA 5 sn: mevcut " + s.mevcut() + " | atlanan " + s.atlananlar());
        assertThat(s.egri()).isNotEmpty();
        assertThat(s.tamamlandi()).isFalse();
        assertThat(s.atlananlar()).isNotEmpty();
    }

    @Test
    void sonsuzButce_edDortSeferGunde_dortHafta_senaryoBelirleyici() {
        hedef.nanoSaat = () -> 0L;
        long hat = jdbc.queryForObject("SELECT id FROM tanim WHERE tip = 'HAT' AND ad LIKE 'Ankara%Sivas'", Long.class);
        long ed = jdbc.queryForObject("SELECT id FROM tanim WHERE tip = 'ARAC_TIPI' AND ad = 'Elektrikli Dizi'", Long.class);
        long ankara = jdbc.queryForObject("SELECT id FROM tanim WHERE tip = 'GAR' AND ad = 'Ankara'", Long.class);
        long sivas = jdbc.queryForObject("SELECT id FROM tanim WHERE tip = 'GAR' AND ad = 'Sivas'", Long.class);
        KadroIstegi istek = new KadroIstegi(BAS, 4, 13, true, false, 30, false, List.of(
                new KadroIstegi.SanalSefer(hat, ed, ankara, sivas, List.of(java.time.LocalTime.of(6, 0), java.time.LocalTime.of(14, 0)), 330, null, null, null),
                new KadroIstegi.SanalSefer(hat, ed, sivas, ankara, List.of(java.time.LocalTime.of(10, 0), java.time.LocalTime.of(18, 0)), 330, null, null, null)));
        KadroRaporuDto a = servis.hesapla(istek);
        KadroRaporuDto b = servis.hesapla(istek);
        System.out.println("QA senaryo (ED +4/gün, 4 hafta, sonsuz bütçe): " + a.senaryo() + " | mevcut " + a.simulasyon().mevcut());
        assertThat(a.senaryo().ekSeferSayisi()).isEqualTo(112);
        assertThat(a.senaryo().tamamlandi()).isTrue();
        assertThat(a.senaryo().egriFarki()).hasSize(3);
        assertThat(a.senaryo().senaryo().bos() - a.senaryo().taban().bos()).isEqualTo(a.senaryo().bosFarki());
        assertThat(sabitJson(b)).isEqualTo(sabitJson(a));
    }
}
