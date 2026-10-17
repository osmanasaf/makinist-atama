package com.makinistatama.kural;

import com.makinistatama.kural.ZamanHesabi.Aralik;
import java.util.ArrayList;
import java.util.List;

/**
 * Bir degerlendirmenin ortak onhesabi (MK-4): siralama, nobetler ve calisma araliklari {@link KuralMotoru#degerlendir}'de
 * BIR kez uretilir, Dinlenme/CalismaSuresi/Gece kurallari paylasir. {@code tumNobet}/{@code siraliTum} aday dahil,
 * {@code digerNobet} aday haric; {@code araliklar}/{@code adaysiz} bunlarin calisma araliklaridir
 * ({@code nobet.bekleme_sayilir}).
 */
record Onhesap(List<Gorev> siraliTum, List<Nobet> tumNobet, List<Nobet> digerNobet, List<Aralik> araliklar,
               List<Aralik> adaysiz, Aralik alan, Nobet adayNobet) {

    static Onhesap of(KuralBaglami b) {
        Parametreler p = b.parametreler();
        long ayirma = p.dakika("nobet.ayirma_saat");
        boolean bekleme = p.bayrak("nobet.bekleme_sayilir");
        List<Gorev> siraliDiger = new ArrayList<>(b.digerGorevler());
        siraliDiger.sort(NobetHesabi.SIRA);
        List<Gorev> siraliTum = new ArrayList<>(siraliDiger);
        int yer = 0; // aday, kendinden buyuk ilk goreve kadar atlanarak yerlestirilir (NobetHesabi.SIRA)
        while (yer < siraliTum.size() && NobetHesabi.SIRA.compare(siraliTum.get(yer), b.aday()) <= 0) {
            yer++;
        }
        siraliTum.add(yer, b.aday());
        List<Nobet> tum = NobetHesabi.gruplaSirali(siraliTum, ayirma);
        List<Nobet> diger = NobetHesabi.gruplaSirali(siraliDiger, ayirma);
        Nobet n = NobetHesabi.nobetiBul(tum, b.aday());
        Aralik alan = bekleme ? new Aralik(n.baslangic(), n.bitis()) : new Aralik(b.aday().baslangic(), b.aday().bitis());
        return new Onhesap(siraliTum, tum, diger, NobetHesabi.araliklar(tum, bekleme), NobetHesabi.araliklar(diger, bekleme), alan, n);
    }
}
