package com.makinistatama.rapor;

import java.time.OffsetDateTime;
import java.util.List;

/** Günlük yedek makinist ihtiyacı (FR-11). {@code oran} ve {@code min} kaynaksız yer tutucu parametrelerdir (ekranda etiketlenir). */
public record YedekRaporuDto(String ay, OffsetDateTime uretimZamani, String ureten, int aktifMakinist, double oran, int min, int eksikGunSayisi, int toplamAcik,
        List<YedekGunDto> gunler) {
}
