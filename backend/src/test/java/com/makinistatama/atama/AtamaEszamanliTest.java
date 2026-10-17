package com.makinistatama.atama;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.junit.jupiter.api.Test;

import com.makinistatama.Tarayici;
import com.makinistatama.TestVeritabani;

/** MK-7: aynı makinistin çakışan iki seferine eşzamanlı atama: tam bir başarı, diğeri 422 (kontrol-sonra-yaz yarışı kapalı). */
class AtamaEszamanliTest extends TestVeritabani {

    static final LocalDate GUN = LocalDate.of(2026, 10, 12);

    @Test
    void ayniMakinistCakisanIkiSefer_ikiIsParcacigi_birBasari_birKuralIhlali() throws Exception {
        AtamaVeri v = new AtamaVeri(jdbc);
        long m = v.makinist("D001");
        long s1 = v.sefer("T1", GUN, "08:00", "10:00");
        long s2 = v.sefer("T2", GUN, "09:00", "11:00");
        Tarayici t1 = atamaci();
        Tarayici t2 = atamaci();

        CyclicBarrier baslangic = new CyclicBarrier(2);
        ExecutorService havuz = Executors.newFixedThreadPool(2);
        try {
            List<Future<Integer>> sonuclar = new ArrayList<>();
            for (Object[] is : new Object[][] { { t1, s1 }, { t2, s2 } }) {
                Callable<Integer> c = () -> {
                    baslangic.await();
                    return ((Tarayici) is[0]).post("/api/atama/sefer/" + is[1], AtamaVeri.ataGovdesi(m)).andReturn().getResponse().getStatus();
                };
                sonuclar.add(havuz.submit(c));
            }
            List<Integer> durumlar = new ArrayList<>();
            for (Future<Integer> f : sonuclar) {
                durumlar.add(f.get());
            }
            assertThat(durumlar).containsExactlyInAnyOrder(201, 422);
        } finally {
            havuz.shutdownNow();
        }
        assertThat(jdbc.queryForObject("SELECT count(*) FROM atama WHERE makinist_id = ?", Integer.class, m)).isEqualTo(1);
    }

    @Test
    void ayniSeferAyniRol_iseIsParcacigi_birBasari_digeri409() throws Exception {
        AtamaVeri v = new AtamaVeri(jdbc);
        long m1 = v.makinist("D001");
        long m2 = v.makinist("D002");
        long s = v.sefer("T1", GUN, "08:00", "10:00");
        Tarayici t1 = atamaci();
        Tarayici t2 = atamaci();

        CyclicBarrier baslangic = new CyclicBarrier(2);
        ExecutorService havuz = Executors.newFixedThreadPool(2);
        try {
            Future<Integer> f1 = havuz.submit(() -> {
                baslangic.await();
                return t1.post("/api/atama/sefer/" + s, AtamaVeri.ataGovdesi(m1)).andReturn().getResponse().getStatus();
            });
            Future<Integer> f2 = havuz.submit(() -> {
                baslangic.await();
                return t2.post("/api/atama/sefer/" + s, AtamaVeri.ataGovdesi(m2)).andReturn().getResponse().getStatus();
            });
            assertThat(List.of(f1.get(), f2.get())).containsExactlyInAnyOrder(201, 409);
        } finally {
            havuz.shutdownNow();
        }
        assertThat(jdbc.queryForObject("SELECT count(*) FROM atama WHERE sefer_id = ?", Integer.class, s)).isEqualTo(1);
    }
}
