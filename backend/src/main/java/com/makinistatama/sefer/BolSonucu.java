package com.makinistatama.sefer;

import java.util.List;

/** Bölme çıktısı (MK-128): {@code parcalar} parça sırasıyla (ilki = {@code parca1}, mevcut satır); {@code yeniIdler} = parça 2..N kimlikleri. Özetler denetim kaydı içindir. */
public record BolSonucu(SeferDto parca1, List<SeferDto> parcalar, String eskiOzet, String yeniOzet, List<Long> yeniIdler) {
}
