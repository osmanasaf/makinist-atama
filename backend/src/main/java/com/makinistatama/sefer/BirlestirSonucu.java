package com.makinistatama.sefer;

import java.util.List;

/** Birleştirme çıktısı (MK-126): {@code sefer} birleşik parça 1 satırı; {@code eskiParcalar} birleştirmeden ÖNCEKİ parçalar (parça sırasıyla; atılan ara gecikmeler denetim ayrıntısı içindir). */
public record BirlestirSonucu(SeferDto sefer, List<Long> silinenSeferIdler, String eskiOzet, String yeniOzet, List<SeferDto> eskiParcalar) {
}
