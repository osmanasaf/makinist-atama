package com.makinistatama.atama;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;

import com.makinistatama.sefer.SeferDurumu;

/**
 * Makinistin kendi görev satırı ({@code benim} ekranı). {@code tarih}: planlanan kalkış günü (MK-3); {@code sira}: o günkü kalkış sırası (1 = ilk). {@code durum}: iptal sefer
 * listeden düşmez, "İPTAL" etiketiyle kalır. {@code esMakinist}: aynı seferdeki diğer görevlinin yalnız adı (varsa). {@code varisErtesiGun}: güncel varış planlanan
 * kalkış gününden sonra bir güne düşüyor. {@code gordu}: "gördüm" onayı durumu; {@code gorduZamani}: GORULDU iken onay anı; {@code ozet}: görevin güncel özeti
 * ({@link GorevOzeti}); {@code eskiOzet}: yalnız DEGISTI iken, onaylanan önceki özet (değişiklik "eski → yeni" gösterimi). {@code parcaNo/parcaSayisi}: F2c parça etiketi (parçasızda null).
 */
public record MakinistGorevDto(Long atamaId, Long seferId, LocalDate tarih, int sira, String trenNo, AtamaRol rol, SeferDurumu durum, String kalkisGar, String varisGar,
        OffsetDateTime planlananKalkis, OffsetDateTime planlananVaris, OffsetDateTime guncelKalkis, OffsetDateTime guncelVaris, int kalkisGecikmeDk, int varisGecikmeDk,
        boolean varisErtesiGun, String aracTipi, String hat, BigDecimal km, String esMakinist, GordumDurumu gordu, OffsetDateTime gorduZamani, String ozet, String eskiOzet,
        Integer parcaNo, Integer parcaSayisi) {
}
