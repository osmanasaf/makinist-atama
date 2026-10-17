package com.makinistatama.rapor;

import java.time.LocalDate;

/** Bir günün yedek durumu: {@code gorevli} bilgi amaçlı (o gün görevi olan AKTIF makinist); {@code mevcut} izinli ve görevlilerin dışındakilerdir. */
public record YedekGunDto(LocalDate tarih, int planliSefer, int ihtiyac, int mevcut, int acik, int izinli, int gorevli) {
}
