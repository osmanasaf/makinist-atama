package com.makinistatama.rapor;

/**
 * Makinistin aylık özeti ({@code /api/benim/ozet}): adalet raporunun KENDİ satırı ve ortalama (başkasının satırı yok, ücret/TL yok). {@code adaleteDahil=false}: makinist
 * o ay AKTIF değil, satır sıfırdır. Atamalar planlananı da içerir ({@code planlananDahil}: "gerçekleşen" değil, atanmış seferlerin toplamı); {@code kmBilinmeyenSefer}
 * km'si girilmemiş seferlerdir (km toplamına girmez). {@code farkYuzde}: görev saatinin ortalamadan farkı (ortalama 0 ise null).
 */
public record MakinistOzetDto(String ay, boolean adaleteDahil, boolean planlananDahil, int seferSayisi, double gorevSaat, double km, int kmBilinmeyenSefer, double geceSaat,
        int izinGunu, double ortalamaSaat, Double farkYuzde, int yolculukSayisi) {
}
