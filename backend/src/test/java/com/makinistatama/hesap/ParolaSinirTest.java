package com.makinistatama.hesap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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

/**
 * 05-review Tur 2 INFO (W1 kenar etkileri): (a) anonim yanlış girişler oturumu açık kullanıcının parola değişimini kilitlemez (ayrı sınır, V18); (b) eşzamanlı yanlış eski parola
 * patlamasında sınır gevşemez: tam 5 deneme işlenir.
 */
class ParolaSinirTest extends TestVeritabani {

    private static String govde(String eski, String yeni) {
        return "{\"eskiParola\":\"" + eski + "\",\"yeniParola\":\"" + yeni + "\"}";
    }

    @Test
    void anonimYanlisGirisler_oturumluKullanicinParolaDegisiminiKilitlemez() throws Exception {
        long id = new AtamaVeri(jdbc).makinist("D001");
        Tarayici oturumlu = makinistGirisli(id, "D001");
        for (int i = 0; i < 5; i++) {
            new Tarayici(mvc).giris("d001", "yanlis-parola-" + i).andExpect(status().isUnauthorized());
        }
        new Tarayici(mvc).giris("d001", Tarayici.PAROLA).andExpect(status().isTooManyRequests()); // giriş kilitli

        oturumlu.post("/api/auth/parola", govde(Tarayici.PAROLA, "benim-yeni-parolam-9")).andExpect(status().isNoContent());
    }

    @Test
    void yirmiDortParalelYanlisEskiParola_tamBesiIslenir() throws Exception {
        long id = new AtamaVeri(jdbc).makinist("D001");
        makinistGirisli(id, "D001");
        int is = 24;
        List<Tarayici> oturumlar = new ArrayList<>();
        for (int i = 0; i < is; i++) {
            oturumlar.add(Tarayici.girisYapmis(mvc, "d001", Tarayici.PAROLA));
        }
        CyclicBarrier baslangic = new CyclicBarrier(is);
        ExecutorService havuz = Executors.newFixedThreadPool(is);
        List<Integer> kodlar = new ArrayList<>();
        try {
            List<Future<Integer>> isler = new ArrayList<>();
            for (int i = 0; i < is; i++) {
                Tarayici o = oturumlar.get(i);
                String yanlis = "yanlis-parola-" + i;
                isler.add(havuz.submit(() -> {
                    baslangic.await();
                    return o.post("/api/auth/parola", govde(yanlis, "benim-yeni-parolam-9")).andReturn().getResponse().getStatus();
                }));
            }
            for (Future<Integer> f : isler) {
                kodlar.add(f.get());
            }
        } finally {
            havuz.shutdownNow();
        }
        assertThat(kodlar.stream().filter(k -> k == 400).count()).as("işlenen deneme %s", kodlar).isEqualTo(5);
        assertThat(kodlar.stream().filter(k -> k == 429).count()).as("%s", kodlar).isEqualTo(is - 5);
    }
}
