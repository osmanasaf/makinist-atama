package com.makinistatama.takas;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.junit.jupiter.api.Test;

import com.makinistatama.Tarayici;

/** FR-M15 / MK-34/4: eşzamanlı yollar. Kilit sırası makinist → talep → atama: kilitlenme (500 / ISLEM_SURUYOR) çıkmamalı, sonuç tek ve tutarlı olmalı. */
class TakasYarisTest extends TakasTabani {

    private static <T> List<T> ayniAnda(List<Callable<T>> isler) throws Exception {
        CyclicBarrier baslangic = new CyclicBarrier(isler.size());
        ExecutorService havuz = Executors.newFixedThreadPool(isler.size());
        try {
            List<Future<T>> sonuclar = new ArrayList<>();
            for (Callable<T> is : isler) {
                sonuclar.add(havuz.submit(() -> {
                    baslangic.await();
                    return is.call();
                }));
            }
            List<T> cikti = new ArrayList<>();
            for (Future<T> f : sonuclar) {
                cikti.add(f.get());
            }
            return cikti;
        } finally {
            havuz.shutdownNow();
        }
    }

    @Test
    void ayniAtamadaEszamanliIkiAcis_birBasari_birTALEP_ACIK() throws Exception {
        Sahne s = sahne();
        Tarayici t1 = giris("D001");
        Tarayici t2 = giris("D001");
        List<Integer> kodlar = ayniAnda(List.of(
                () -> t1.post("/api/benim/takas", acGovdesi(s.ataA(), s.ataB(), "IZIN", null)).andReturn().getResponse().getStatus(),
                () -> t2.post("/api/benim/takas", acGovdesi(s.ataA(), s.ataB(), "IZIN", null)).andReturn().getResponse().getStatus()));
        assertThat(kodlar).containsExactlyInAnyOrder(201, 409);
        assertThat(sayi("SELECT count(*) FROM takas_talebi")).isEqualTo(1);
    }

    @Test
    void ayniHedefeEszamanliIkiFarkliAcan_birBasari_digeriHedefSuzgecindenDuserYaDa409() throws Exception {
        Sahne s = sahne();
        long c = hesapli("D003");
        long ataC = atama(sefer("TC", gun(5), "08:00", "09:00"), c, "ASIL");
        Tarayici ta = giris("D001");
        Tarayici tc = giris("D003");
        List<Integer> kodlar = ayniAnda(List.of(
                () -> ta.post("/api/benim/takas", acGovdesi(s.ataA(), s.ataB(), "IZIN", null)).andReturn().getResponse().getStatus(),
                () -> tc.post("/api/benim/takas", acGovdesi(ataC, s.ataB(), "IZIN", null)).andReturn().getResponse().getStatus()));
        assertThat(kodlar.stream().filter(k -> k == 201).count()).as("%s", kodlar).isEqualTo(1);
        assertThat(kodlar).allMatch(k -> k == 201 || k == 404 || k == 409);
        assertThat(sayi("SELECT count(*) FROM takas_talebi WHERE hedef_atama_id = ?", s.ataB())).isEqualTo(1);
    }

    @Test
    void ikiAtamaciAyniAndaOnaylar_birIkiYuzYuz_digeri409_atamalarBirKezDegisir() throws Exception {
        Sahne s = sahne();
        long talep = id(giris("D001").post("/api/benim/takas", acGovdesi(s.ataA(), s.ataB(), "IZIN", null)));
        giris("D002").post("/api/benim/takas/" + talep + "/kabul", "");
        Tarayici a1 = atamaci();
        Tarayici a2 = atamaci();
        List<Integer> kodlar = ayniAnda(List.of(
                () -> a1.post("/api/takas/" + talep + "/onay", "{}").andReturn().getResponse().getStatus(),
                () -> a2.post("/api/takas/" + talep + "/onay", "{}").andReturn().getResponse().getStatus()));
        assertThat(kodlar).containsExactlyInAnyOrder(200, 409);
        assertThat(sayi("SELECT count(*) FROM degisiklik_kaydi WHERE tur = 'TAKAS_ONAY'")).isEqualTo(1);
        assertThat(sayi("SELECT count(*) FROM degisiklik_kaydi WHERE tur = 'ATAMA_DEGISTIR'")).isEqualTo(2);
        assertThat(sayi("SELECT count(*) FROM atama")).isEqualTo(2);
    }

    @Test
    void kabulIleIptalEszamanli_kilitlenmeYok_sonDurumIptal() throws Exception {
        Sahne s = sahne();
        Tarayici ta = giris("D001");
        Tarayici tb = giris("D002");
        long talep = id(ta.post("/api/benim/takas", acGovdesi(s.ataA(), s.ataB(), "IZIN", null)));
        List<Integer> kodlar = ayniAnda(List.of(
                () -> tb.post("/api/benim/takas/" + talep + "/kabul", "").andReturn().getResponse().getStatus(),
                () -> ta.post("/api/benim/takas/" + talep + "/iptal", "").andReturn().getResponse().getStatus()));
        assertThat(kodlar).allMatch(k -> k == 200 || k == 409);
        assertThat(kodlar.get(1)).as("iptal her sırada başarılı").isEqualTo(200);
        assertThat(jdbc.queryForObject("SELECT durum FROM takas_talebi WHERE id = ?", String.class, talep)).isEqualTo("IPTAL");
    }

    @Test
    void acmaIleOnayEszamanli_kilitlenmeYok() throws Exception {
        Sahne s = sahne();
        long c = hesapli("D003");
        long ataC = atama(sefer("TC", gun(5), "08:00", "09:00"), c, "ASIL");
        long talep = id(giris("D001").post("/api/benim/takas", acGovdesi(s.ataA(), s.ataB(), "IZIN", null)));
        giris("D002").post("/api/benim/takas/" + talep + "/kabul", "");
        Tarayici atamaci = atamaci();
        Tarayici tc = giris("D003");
        List<Integer> kodlar = ayniAnda(List.of(
                () -> atamaci.post("/api/takas/" + talep + "/onay", "{}").andReturn().getResponse().getStatus(),
                () -> tc.post("/api/benim/takas", acGovdesi(ataC, s.ataB(), "IZIN", null)).andReturn().getResponse().getStatus()));
        assertThat(kodlar.get(0)).as("onay %s", kodlar).isEqualTo(200);
        assertThat(kodlar.get(1)).as("açış ya talep onaylanmadan önce süzgeçte düşer (404) ya da atama değişmiş (404/409) ", kodlar).isIn(201, 404, 409);
        assertThat(sayi("SELECT count(*) FROM atama")).isGreaterThanOrEqualTo(3);
    }

    /** W1: 14 eşzamanlı BAYAT kabul havuzu (varsayılan 10 bağlantı) tüketmemeli: ikinci bağlantı gerekmez, hepsi hızlı 409 ve kapanış kalıcı. */
    @Test
    void eszamanliBayatKabuller_havuzuKilitlemez_hepsi409_talepler_kapanir() throws Exception {
        int n = 14;
        List<Callable<Integer>> isler = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            long a = hesapli("PA" + i);
            long b = hesapli("PB" + i);
            long ataA = atama(sefer("PTA" + i, gun(3), "08:00", "09:00"), a, "ASIL");
            long sb = sefer("PTB" + i, gun(4), "08:00", "09:00");
            long ataB = atama(sb, b, "YARDIMCI");
            long talep = id(giris("PA" + i).post("/api/benim/takas", acGovdesi(ataA, ataB, "IZIN", null)));
            jdbc.update("UPDATE sefer SET kalkis_gecikme_dk = 25, varis_gecikme_dk = 25 WHERE id = ?", sb); // talep bayatlar
            Tarayici tb = giris("PB" + i);
            isler.add(() -> tb.post("/api/benim/takas/" + talep + "/kabul", "").andReturn().getResponse().getStatus());
        }
        long bas = System.nanoTime();
        List<Integer> kodlar = ayniAnda(isler);
        long ms = (System.nanoTime() - bas) / 1_000_000;
        assertThat(kodlar).as("%s", kodlar).containsOnly(409);
        assertThat(ms).as("toplam süre ms").isLessThan(5_000);
        assertThat(sayi("SELECT count(*) FROM takas_talebi WHERE durum = 'BAYAT'")).isEqualTo(n);
        assertThat(sayi("SELECT count(*) FROM degisiklik_kaydi WHERE tur = 'TAKAS_BAYAT'")).isEqualTo(n);
    }
}
