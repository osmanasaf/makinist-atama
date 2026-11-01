package com.makinistatama.kural;

import com.makinistatama.kural.ZamanHesabi.Aralik;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * Şeffaf yorgunluk özeti (F3, MK-175): üç HAM değer, bileşik puan ve model YOK. Saf (DB'siz, parametre okuma yalnız nöbet/gece tanımı için). Pencere
 * {@code [referans - pencereGun, referans]}; nöbet "pencerede" sayılır, başlangıcı pencereye düşüyorsa. (a) toplam çalışma: nöbetlerin çalışma aralıklarının ({@code nobet.bekleme_sayilir},
 * sayılmayan yolculuk düşer) pencereyle kesişimi, dakika; (b) gece nöbeti sayısı ({@link GeceKurallari#geceMi}, kural motoruyla aynı tanım); (c) pencerede ardışık iki nöbet
 * arasındaki en kısa dinlenme (önceki bitiş -> sonraki başlangıç), dakika; tek nöbet varsa {@code null}. Bilgi amaçlıdır; bilimsel bir yorgunluk modeli değildir.
 */
public final class YorgunlukHesabi {

    private YorgunlukHesabi() {
    }

    /** {@code enKisaDinlenmeDk}: pencerede en az iki nöbet yoksa {@code null}. */
    public record Sonuc(long toplamDk, int geceNobetSayisi, Long enKisaDinlenmeDk) {
    }

    public static Sonuc hesapla(List<Gorev> tumGorevler, Instant referans, Parametreler p, int pencereGun) {
        Instant bas = referans.minus(Duration.ofDays(pencereGun));
        List<Nobet> nobetler = NobetHesabi.grupla(tumGorevler, p.dakika("nobet.ayirma_saat"));
        long toplam = 0;
        for (Aralik a : NobetHesabi.araliklar(nobetler, p.bayrak("nobet.bekleme_sayilir"))) {
            toplam += ZamanHesabi.kesisim(a.bas(), a.bit(), bas, referans);
        }
        int gece = 0;
        Nobet onceki = null;
        Long enKisa = null;
        for (Nobet n : nobetler) {
            if (n.baslangic().isBefore(bas) || n.baslangic().isAfter(referans)) {
                continue;
            }
            if (GeceKurallari.geceMi(GeceKurallari.ozet(n, p), p)) {
                gece++;
            }
            if (onceki != null) {
                long bosluk = Duration.between(onceki.bitis(), n.baslangic()).toMinutes();
                enKisa = enKisa == null ? bosluk : Math.min(enKisa, bosluk);
            }
            onceki = n;
        }
        return new Sonuc(toplam, gece, enKisa);
    }
}
