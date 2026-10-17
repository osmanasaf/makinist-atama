package com.makinistatama.atama;

import java.util.List;

/**
 * Yazma sonucu (MK-106): intikal + değişiklikten ÖNCE kırmızı olmayıp SONRA kırmızı olan atamalar (iptal / saat düzeltmesi; kural gereğidir, hata değil, atamacı farkında olmalı).
 * Ekleme sonucunda {@code yeniKirmizi} boştur (yazma anı denetimi iki yönlüdür: ENGEL yaratacak intikal yazılmaz).
 */
public record IntikalSonucuDto(IntikalDto intikal, List<KaldirSonucuDto.Etkilenen> yeniKirmizi) {
}
