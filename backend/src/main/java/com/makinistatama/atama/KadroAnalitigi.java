package com.makinistatama.atama;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import com.makinistatama.atama.OtomatikAtama.SeferGirdisi;
import com.makinistatama.kural.Parametreler;
import com.makinistatama.kural.Siddet;

/**
 * Kadro ihtiyacı analitiği (F3a-1, MK-78): SAF (Spring/JPA/IO yok). Talep, kişi başı haftalık saat {@code U} ve GEVŞETİLMİŞ akış modeli: yalnız saat/yetki (K-05/K-06
 * kartezyen tanımı); konum, dinlenme, nöbet yok. Bu yüzden sonuç bir ALT SINIRDIR; gerçek ihtiyacı simülasyon ({@link KadroArama}) verir.
 */
public final class KadroAnalitigi {

    private static final double EPS = 1e-9;

    /** Talep sınıfı: hat × araç tipi. */
    public record Sinif(long hatId, long aracTipiId) implements Comparable<Sinif> {
        @Override
        public int compareTo(Sinif o) {
            return Comparator.comparingLong(Sinif::hatId).thenComparingLong(Sinif::aracTipiId).compare(this, o);
        }
    }

    /** Makinist imzası: yetkili olduğu araç tipleri ve hatlar (sıralı). Sınıf imzaya bağlanır ⇔ imza HEM aracı HEM hattı içerir. */
    public record Imza(List<Long> aracTipleri, List<Long> hatlar) implements Comparable<Imza> {
        @Override
        public int compareTo(Imza o) {
            int c = liste(aracTipleri, o.aracTipleri);
            return c != 0 ? c : liste(hatlar, o.hatlar);
        }

        private static int liste(List<Long> a, List<Long> b) {
            for (int i = 0; i < Math.min(a.size(), b.size()); i++) {
                int c = Long.compare(a.get(i), b.get(i));
                if (c != 0) {
                    return c;
                }
            }
            return Integer.compare(a.size(), b.size());
        }

        boolean kapsar(Sinif s) {
            return aracTipleri.contains(s.aracTipiId()) && hatlar.contains(s.hatId());
        }
    }

    /**
     * Akış sonucu: sınıf başına karşılanmayan saat ({@code eksik}), imza başına taşınan saat ({@code kullanilan}). Toplam eksik tektir (maksimum akış tektir); hangi sınıfta
     * kaldığı sıralı BFS'e göre belirleyicidir.
     */
    public record Akis(Map<Sinif, Double> eksik, Map<Imza, Double> kullanilan) {
        public double eksikToplam() {
            return eksik.values().stream().mapToDouble(Double::doubleValue).sum();
        }
    }

    private KadroAnalitigi() {
    }

    /**
     * Kişi başı haftalık saat: {@code min(haftalik.max_saat, gunluk.max_saat × (7 − ceil(hafta_tatili.min_saat / 24))) × (1 − devamsızlık)}. Parametreden okunur.
     * K-14 (haftalık üst sınır) KAPALI ise motor 45 saatin üstüne atayabilir; haftalık terim yoktur: {@code gunluk.max_saat × (7 − tatil günü) × (1 − devamsızlık)} (MK-97).
     */
    public static double kisiBasinaSaat(Parametreler p, int devamsizlikYuzde) {
        double haftalik = p.siddet("K-14") == Siddet.KAPALI ? Double.MAX_VALUE : p.oran("haftalik.max_saat").doubleValue();
        int tatilGun = (int) Math.ceil(p.oran("hafta_tatili.min_saat").doubleValue() / 24 - EPS);
        double gunluk = p.oran("gunluk.max_saat").doubleValue() * Math.max(0, 7 - tatilGun);
        return Math.min(haftalik, gunluk) * (1 - devamsizlikYuzde / 100.0);
    }

    /** Sınıf başına haftalık talep saati: toplam çalışma süresi × 7 / gün sayısı. */
    public static Map<Sinif, Double> haftalikTalep(List<SeferGirdisi> seferler, int gunSayisi) {
        Map<Sinif, Double> dakika = new TreeMap<>();
        for (SeferGirdisi s : seferler) {
            dakika.merge(new Sinif(s.gorev().hatId(), s.gorev().aracTipiId()), (double) s.dakika(), Double::sum);
        }
        Map<Sinif, Double> saat = new TreeMap<>();
        dakika.forEach((k, v) -> saat.put(k, v / 60.0 * 7 / gunSayisi));
        return saat;
    }

    /** {@code ceil(saat / u)}: tam bölünmede yukarı yuvarlanmaz. */
    public static int kisi(double saat, double u) {
        return saat <= EPS ? 0 : (int) Math.ceil(saat / u - 1e-7);
    }

    /**
     * Gevşetilmiş maksimum akış (Edmonds–Karp, BFS artırma; düğüm sayısı birkaç düzine): kaynak → sınıf (talep) → imza (imza sınıfı kapsıyorsa) → hedef (kişi × u).
     * Sıralamalar tamdır (TreeMap), sonuç belirleyicidir.
     */
    public static Akis akis(Map<Sinif, Double> talep, Map<Imza, Integer> kume, double u) {
        List<Sinif> siniflar = new ArrayList<>(new TreeMap<>(talep).keySet());
        // en az sınıf kapsayan (en uzman) imza önce: esnek (çok bröveli) kişiler olabildiğince boş kalır; kullanım yüzdeleri bu yüzden anlamlıdır
        List<Imza> imzalar = new ArrayList<>(new TreeMap<>(kume).keySet());
        imzalar.sort(Comparator.comparingLong((Imza i) -> talep.keySet().stream().filter(i::kapsar).count()).thenComparing(Comparator.naturalOrder()));
        int n = siniflar.size();
        int m = imzalar.size();
        int kaynak = 0;
        int hedef = n + m + 1;
        double[][] kap = new double[hedef + 1][hedef + 1];
        for (int i = 0; i < n; i++) {
            kap[kaynak][1 + i] = talep.get(siniflar.get(i));
            for (int j = 0; j < m; j++) {
                if (imzalar.get(j).kapsar(siniflar.get(i))) {
                    kap[1 + i][1 + n + j] = Double.MAX_VALUE / 4;
                }
            }
        }
        for (int j = 0; j < m; j++) {
            kap[1 + n + j][hedef] = kume.get(imzalar.get(j)) * u;
        }
        double[][] akis = new double[hedef + 1][hedef + 1];
        while (true) {
            int[] ata = new int[hedef + 1];
            java.util.Arrays.fill(ata, -1);
            ata[kaynak] = kaynak;
            ArrayDeque<Integer> kuyruk = new ArrayDeque<>(List.of(kaynak));
            while (!kuyruk.isEmpty() && ata[hedef] < 0) {
                int x = kuyruk.poll();
                for (int y = 0; y <= hedef; y++) {
                    if (ata[y] < 0 && kap[x][y] - akis[x][y] > EPS) {
                        ata[y] = x;
                        kuyruk.add(y);
                    }
                }
            }
            if (ata[hedef] < 0) {
                break;
            }
            double bant = Double.MAX_VALUE;
            for (int y = hedef; y != kaynak; y = ata[y]) {
                bant = Math.min(bant, kap[ata[y]][y] - akis[ata[y]][y]);
            }
            for (int y = hedef; y != kaynak; y = ata[y]) {
                akis[ata[y]][y] += bant;
                akis[y][ata[y]] -= bant;
            }
        }
        Map<Sinif, Double> eksik = new TreeMap<>();
        for (int i = 0; i < n; i++) {
            eksik.put(siniflar.get(i), Math.max(0, talep.get(siniflar.get(i)) - akis[kaynak][1 + i]));
        }
        Map<Imza, Double> kullanilan = new TreeMap<>();
        for (int j = 0; j < m; j++) {
            kullanilan.put(imzalar.get(j), akis[1 + n + j][hedef]);
        }
        return new Akis(eksik, kullanilan);
    }

    /** Günlük yedek ihtiyacı: {@code max(yedek.min, ceil(yedek.oran × planlı sefer))}; {@code YedekServisi}'nin formülü (KadroYedekEsitligiTest sürüklenmeyi yakalar). */
    public static int yedekGunIhtiyaci(BigDecimal oran, int min, int planliSefer) {
        return Math.max(min, oran.multiply(BigDecimal.valueOf(planliSefer)).setScale(0, RoundingMode.CEILING).intValue());
    }

    /** Günlerin en büyük günlük ihtiyacı. */
    public static int yedekIhtiyaci(BigDecimal oran, int min, List<Integer> gunlukPlanliSefer) {
        return gunlukPlanliSefer.stream().mapToInt(g -> yedekGunIhtiyaci(oran, min, g)).max().orElse(0);
    }
}
