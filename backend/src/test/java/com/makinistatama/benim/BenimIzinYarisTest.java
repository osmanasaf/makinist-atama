package com.makinistatama.benim;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.junit.jupiter.api.Test;

import com.makinistatama.Tarayici;
import com.makinistatama.TestVeritabani;
import com.makinistatama.atama.AtamaVeri;
import com.makinistatama.ortak.ZamanAyari;

/** 05-review Tur 2 W2: açık izin talebi sınırı (5) eşzamanlı isteklerle aşılmaz: makinist satırı kilidi (MK-7) sayım ile yazımı serileştirir. */
class BenimIzinYarisTest extends TestVeritabani {

    @Test
    void onIkiParalelTalep_enCokBesiKabul() throws Exception {
        long m = new AtamaVeri(jdbc).makinist("D001");
        makinistGirisli(m, "D001");
        LocalDate bas = LocalDate.now(ZamanAyari.BOLGE).plusDays(10);
        List<Tarayici> oturumlar = new ArrayList<>();
        for (int i = 0; i < 12; i++) {
            oturumlar.add(Tarayici.girisYapmis(mvc, "d001", Tarayici.PAROLA));
        }
        CyclicBarrier baslangic = new CyclicBarrier(12);
        ExecutorService havuz = Executors.newFixedThreadPool(12);
        List<Integer> kodlar = new ArrayList<>();
        try {
            List<Future<Integer>> isler = new ArrayList<>();
            for (int i = 0; i < 12; i++) {
                String gun = bas.plusDays(3L * i).toString();
                Tarayici o = oturumlar.get(i);
                isler.add(havuz.submit(() -> {
                    baslangic.await();
                    return o.post("/api/benim/izinler", "{\"baslangic\":\"" + gun + "\",\"bitis\":\"" + gun + "\",\"tur\":\"YILLIK\"}").andReturn().getResponse().getStatus();
                }));
            }
            for (Future<Integer> f : isler) {
                kodlar.add(f.get());
            }
        } finally {
            havuz.shutdownNow();
        }
        assertThat(kodlar.stream().filter(k -> k == 201).count()).as("%s", kodlar).isEqualTo(5);
        assertThat(kodlar.stream().filter(k -> k == 409).count()).as("%s", kodlar).isEqualTo(7);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM izin WHERE makinist_id = ? AND durum = 'TALEP'", Integer.class, m)).isEqualTo(5);
    }
}
