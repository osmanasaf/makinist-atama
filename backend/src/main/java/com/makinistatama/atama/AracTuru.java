package com.makinistatama.atama;

/** İntikalin aracı (V32 {@code intikal_arac_turu_ck}). Kural motoru bakmaz; yalnız kayıt ve gösterim. */
public enum AracTuru {
    SERVIS("Servis"),
    OTOBUS("Otobüs"),
    TAKSI("Taksi"),
    DIGER("Diğer");

    private final String etiket;

    AracTuru(String etiket) {
        this.etiket = etiket;
    }

    public String etiket() {
        return etiket;
    }
}
