package com.makinistatama.rapor;

/**
 * Makinistin aylık özeti ({@code /api/benim/ozet}): adalet raporunun KENDİ satırı (başkasının satırı yok, ücret/TL yok; OK-35: ortalama ve ortalamaya fark makinistin ucundan KALDIRILDI, yalnız atama yapan personel görür). {@code adaleteDahil=false}: makinist
 * o ay AKTIF değil, satır sıfırdır. Atamalar planlananı da içerir ({@code planlananDahil}: "gerçekleşen" değil, atanmış seferlerin toplamı); {@code kmBilinmeyenSefer}
 * km'si girilmemiş seferlerdir (km toplamına girmez).
 */
public record MakinistOzetDto(String ay, boolean adaleteDahil, boolean planlananDahil, int seferSayisi, double gorevSaat, double km, int kmBilinmeyenSefer, double geceSaat,
        int izinGunu, int yolculukSayisi) {
}
