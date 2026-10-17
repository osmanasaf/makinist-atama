package com.makinistatama.sefer;

/** Seferin tren türü (isteğe bağlı; boş = belirsiz). Değerler {@code kural.Kapsam.TREN_TURLERI} ile aynıdır ({@code TrenTuruSabitleriTest}, MK-23). */
public enum TrenTuru {
    YUK("Yük"), YOLCU("Yolcu");

    private final String ad;

    TrenTuru(String ad) {
        this.ad = ad;
    }

    public String ad() {
        return ad;
    }

    /** Excel hücresi: "Yük" / "Yolcu" (Türkçe-duyarsız) ya da ham kod; tanınmazsa {@code null}. */
    static TrenTuru metinden(String metin) {
        String k = com.makinistatama.ortak.AramaMetni.katla(metin);
        for (TrenTuru t : values()) {
            if (com.makinistatama.ortak.AramaMetni.katla(t.ad).equals(k) || t.name().equalsIgnoreCase(metin)) {
                return t;
            }
        }
        return null;
    }
}
