package com.makinistatama.kural;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * Kuralin ihtiyac duydugu makinist kart bilgisi (saf veri). Belge tarihleri bos (null) ise "girilmemis"
 * sayilir ve ilgili kural ihlal uretir. {@code izinler} tum durumlarda gelir; yalniz ONAY sayilir (K-09),
 * izin gunleri baslangic ve bitis DAHIL. {@code kartSonSurus} kartta girilen genel baslangic degeridir. {@code tur}: K-05..K-08 (brove) yalniz MAKINIST icin calisir (F3b, MK-84).
 */
public record MakinistBilgisi(long id, long bagliGarId, Durum durum,
                              LocalDate ehliyetBitis, LocalDate saglikBitis, LocalDate psikoteknikBitis,
                              Map<Long, LocalDate> aracTipiSinavlari, Map<Long, HatBrove> hatBroveleri,
                              LocalDate kartSonSurus, List<Izin> izinler, Tur tur) {

    public enum Durum { AKTIF, ASKIDA, PASIF }

    /** F3b (MK-82): kartın türü; {@code makinist.PersonelTuru}'nun saf ikizi (paket yönü: kural başka paketi görmez; adları {@code PersonelTuruSabitleriTest} eşler). */
    public enum Tur { MAKINIST, KONDUKTOR }

    public enum IzinDurumu { TALEP, ONAY, RED }

    /** Hat brovesi: sinav tarihi ve (bos olabilir) o hattaki son surus. */
    public record HatBrove(LocalDate sinavTarihi, LocalDate sonSurus) {
    }

    public record Izin(LocalDate baslangic, LocalDate bitis, IzinDurumu durum) {
    }

    public MakinistBilgisi {
        aracTipiSinavlari = Map.copyOf(aracTipiSinavlari);
        hatBroveleri = Map.copyOf(hatBroveleri);
        izinler = List.copyOf(izinler);
    }

    /** Eski 10 bileşenli kurucu: tür MAKINIST (F3b öncesi çağıranlar ve testler değişmeden derlenir). */
    public MakinistBilgisi(long id, long bagliGarId, Durum durum, LocalDate ehliyetBitis, LocalDate saglikBitis, LocalDate psikoteknikBitis,
            Map<Long, LocalDate> aracTipiSinavlari, Map<Long, HatBrove> hatBroveleri, LocalDate kartSonSurus, List<Izin> izinler) {
        this(id, bagliGarId, durum, ehliyetBitis, saglikBitis, psikoteknikBitis, aracTipiSinavlari, hatBroveleri, kartSonSurus, izinler, Tur.MAKINIST);
    }
}
