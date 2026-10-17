package com.makinistatama.makinist;

/** Hafif makinist kimliği (varlık/belge/izin yüklenmeden): hesap durum kapısı ve eş makinist adı için. */
public record MakinistKarti(long id, String sicil, String adSoyad, MakinistDurum durum, PersonelTuru tur) {
}
