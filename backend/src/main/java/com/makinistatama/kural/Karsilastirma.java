package com.makinistatama.kural;

/** "En fazla" (eşit uygun, aşan ihlal) / "en az" (eşit uygun, altı ihlal). "En az" yalnız azalan ölçülerde izinlidir ({@link Olcu#azalan()}). */
public enum Karsilastirma {
    EN_FAZLA("en fazla", "Ölçülen değer bu sınırı aşarsa kural ihlal edilir (sınıra eşit olmak uygundur)."),
    EN_AZ("en az", "Ölçülen değer bu sınırın altına düşerse kural ihlal edilir (sınıra eşit olmak uygundur).");

    private final String ad;
    private final String aciklama;

    Karsilastirma(String ad, String aciklama) {
        this.ad = ad;
        this.aciklama = aciklama;
    }

    public String ad() {
        return ad;
    }

    public String aciklama() {
        return aciklama;
    }
}
