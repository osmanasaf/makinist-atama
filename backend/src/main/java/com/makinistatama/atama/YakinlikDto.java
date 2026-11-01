package com.makinistatama.atama;

import java.util.List;

import com.makinistatama.kural.Yakinlik;
import com.makinistatama.kural.YorgunlukHesabi;

/**
 * Sınıra yakınlık ve yorgunluk BİLGİSİ yanıtı (F3, MK-174): {@code AdayDto}/{@code GunListesiDto}'dan AYRI uçların gövdesi. {@code marjAktif=false}: hesap yapılmadı,
 * {@code satirlar} boş. {@code esikler}: yorgunluk vurgu eşikleri (ekranda yazar). Yalnız personel görür; makinist uçlarında (/api/benim/**) bu veri yoktur (OK-35).
 * {@code makinistId}: aday ucunda aday, gün ucunda atanan makinist; {@code atamaId}: yalnız gün ucunda dolu.
 */
public record YakinlikDto(boolean marjAktif, Esikler esikler, List<Satir> satirlar) {

    /** Yorgunluk vurgu eşikleri ({@code yorgunluk.*} ayarları): pencere (gün), pencere çalışma süresi (saat), gece nöbeti adedi, en kısa dinlenme (saat). */
    public record Esikler(int pencereGun, int toplamSaat, int geceAdet, int dinlenmeSaat) {
    }

    public record Isaret(String kod, double olculen, double sinir, double kalan, String birim, String metin) {

        static Isaret of(Yakinlik y) {
            return new Isaret(y.kod(), y.olculen(), y.sinir(), y.kalan(), y.birim(), y.metin());
        }
    }

    /** Üç ham değer; bileşik puan YOK. {@code enKisaDinlenmeDk}: pencerede tek nöbet varsa null. */
    public record Yorgunluk(int pencereGun, long toplamDk, int geceNobetSayisi, Long enKisaDinlenmeDk) {

        static Yorgunluk of(YorgunlukHesabi.Sonuc s, int pencereGun) {
            return new Yorgunluk(pencereGun, s.toplamDk(), s.geceNobetSayisi(), s.enKisaDinlenmeDk());
        }
    }

    public record Satir(Long makinistId, Long atamaId, List<Isaret> yakinlik, Yorgunluk yorgunluk) {
    }
}
