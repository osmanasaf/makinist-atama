package com.makinistatama.rapor;

/**
 * Adalet raporu satırı (bir AKTIF makinist, bir ay). {@code gorevSaat}: iptal olmayan seferlerin çalışma aralığının (gecikme, hazırlık/alıkoyma dahil) ay içindeki payı, ASIL ve YARDIMCI birlikte.
 * {@code farkYuzde}: ortalamadan % fark (ortalama 0 ise null); {@code sifirSaat}: o ay hiç görev almamış. F2b: {@code seferSayisi} yalnız sürüş seferleridir; {@code yolculukSayisi}: yolcu olarak gidilen sefer sayısı (süre/gece puantajla aynı hesapta {@code yolculuk.calisma_sayilir} ile katılır, km hiçbir zaman).
 */
public record AdaletSatiriDto(Long makinistId, String sicil, String adSoyad, int seferSayisi, double gorevSaat, double km, int kmBilinmeyenSefer, double geceSaat,
        int izinGunu, Double farkYuzde, boolean sifirSaat, int yolculukSayisi) {
}
