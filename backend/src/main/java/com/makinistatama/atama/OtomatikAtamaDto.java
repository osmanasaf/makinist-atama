package com.makinistatama.atama;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * Otomatik atama sonucu (önizleme ve uygulama aynı biçim). {@code bosKalanlar}: kimsenin uygun olmadığı seferler (ekranda kırmızı + neden).
 * Zaten ASIL'ı olan ve iptal seferlere dokunulmaz, listede yoktur. {@code gecmisSayisi}: kalkışı geçmişte kalan (bitmiş ya da başlamış), atlanan seferler;
 * "boş" sayılmaz, {@code bosSayisi}'na girmez.
 */
public record OtomatikAtamaDto(LocalDate bas, LocalDate bit, boolean uygulandi, int atananSayisi, int bosSayisi, int gecmisSayisi,
        List<Atanan> atananlar, List<BosSefer> bosKalanlar, Iyilestirme iyilestirme) {

    /** Eski (iyileştirmesiz) 8 bileşenli kurucu: {@code iyilestirme} null. */
    public OtomatikAtamaDto(LocalDate bas, LocalDate bit, boolean uygulandi, int atananSayisi, int bosSayisi, int gecmisSayisi, List<Atanan> atananlar,
            List<BosSefer> bosKalanlar) {
        this(bas, bit, uygulandi, atananSayisi, bosSayisi, gecmisSayisi, atananlar, bosKalanlar, null);
    }

    /**
     * İyileştirme bilgisi (F2d, MK-71): yalnız {@code iyilestir} açıkken dolu. {@code tabanBosSayisi}: bugünkü planın boş sayısı (yalnız önizlemede; uygulamada tur 0
     * koşulmaz, null); "X sefer daha doldu" = {@code tabanBosSayisi - bosSayisi}. {@code anahtar}: {@code uygula}ya geri gönderilir. {@code durma}: aramanın bitiş nedeni ({@link Iyilestirme#ara}: BOS_YOK, BOS_SIFIR, TUR_SINIRI, SURE); anahtarlı uygulamada null.
     */
    public record Iyilestirme(Integer tabanBosSayisi, int tur, long sureMs, IyilestirmeAnahtari anahtar, String durma) {
    }

    /** {@code yolculuk}: doluysa makinist bu seferden önce, bu seferde yolcu olarak gider (F2b); yoksa null. */
    public record Atanan(Long seferId, String trenNo, LocalDate tarih, OffsetDateTime kalkis, OffsetDateTime varis, String kalkisGar, String varisGar,
            Long makinistId, String sicil, String ad, YolculukSefer yolculuk) {
    }

    /** Yolculuk yapılacak sefer özeti. */
    public record YolculukSefer(Long seferId, String trenNo, OffsetDateTime kalkis, OffsetDateTime varis, String kalkisGar, String varisGar) {
    }

    public record BosSefer(Long seferId, String trenNo, LocalDate tarih, OffsetDateTime kalkis, OffsetDateTime varis, String kalkisGar, String varisGar,
            String neden, List<String> ornekler) {
    }
}
