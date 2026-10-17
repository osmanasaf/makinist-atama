package com.makinistatama.atama;

import java.time.LocalDate;
import java.util.List;

/**
 * Kadro ihtiyacı yanıtı (F3a, MK-79; OK-22 ile yeniden kuruldu). Hiçbir yerde sanal makinist kimliği/sicili yoktur. {@code simulasyon} yalnızca analitik istekte
 * {@code null}; {@code senaryo} sanal sefer verilmediyse {@code null}. Başlık bir TEK SAYI değil, doluluk eğrisidir ({@link Simulasyon}); eğri süre bütçesinden bağımsız ve
 * belirleyicidir, bütçe yalnız ek hesaplarda (iyileştirmeli, yolculuklu, senaryo eğrisi) geçerlidir. Excel, istemcinin bu yanıtı geri göndermesiyle üretilir (sunucu
 * yeniden hesaplamaz); alan sınırları {@code KadroExcel}'de doğrulanır.
 * {@code kumeKaynagi}: {@code PLAN} (kullanım mevcut kadronun gerçek simülasyon planından) ya da {@code AKIS} (yalnız analitik istekte, akış tahsisinden).
 */
public record KadroRaporuDto(Varsayim varsayim, List<TalepSatiri> talep, List<GarSatiri> garlar, Analitik analitik, List<Kume> kumeler, String kumeKaynagi, Yedek yedek,
        Simulasyon simulasyon, Senaryo senaryo) {

    public record Varsayim(LocalDate bas, LocalDate bit, int hafta, int gunSayisi, int devamsizlikYuzde, double kisiBasinaSaat, double yedekOran, int yedekMin,
            int sureSn, int aktifMakinist, int seferSayisi, double haftalikTalepSaat) {
    }

    /** Hat × araç tipi talep sınıfı. */
    public record TalepSatiri(String hat, String aracTipi, int seferSayisi, double haftalikSaat) {
    }

    /** Gar: kalkış garındaki haftalık talep, mevcut bağlı makinist ve (simülasyonda) dağılım noktasında eklenen genel makinist. */
    public record GarSatiri(String gar, double haftalikTalepSaat, int mevcutMakinist, int eklenen) {
    }

    /**
     * {@code gerekliAltSinir}: tüm talebi karşılayacak en az kişi (her makinist her bröveye sahip olsa bile), {@code tekBroveliGerekli}: sanal makinistin tek (araç, hat)
     * bröveli olduğu varsayımıyla sınıf başına toplam. {@code eksikAltSinir}: mevcut kadroya EK en az kişi.
     */
    public record Analitik(double kisiBasinaSaat, int gerekliAltSinir, int tekBroveliGerekli, int mevcutAsil, double eksikSaat, int eksikAltSinir,
            List<SinifSatiri> siniflar) {
    }

    public record SinifSatiri(String hat, String aracTipi, double haftalikSaat, int gerekliKisi, int mevcutYetkili, double eksikSaat, int eksikKisi) {
    }

    /**
     * Mevcut bröve kümesi (araç tipleri × hatlar): kişi, haftalık kullanılan saat ve kullanım % ve durum (BOL / NORMAL / SIKISIK). Simülasyonda kullanım mevcut kadronun
     * GERÇEK planından gelir (kişinin işlediği saat, kişinin kümesine); yalnız analitik istekte akış tahsisinden.
     */
    public record Kume(List<String> aracTipleri, List<String> hatlar, int mevcut, double kullanilanSaat, double kapasiteSaat, double kullanimYuzde, String durum) {
    }

    /** {@code ihtiyac}: günlük yedek ihtiyacı (operasyonel tampon, asıl kadrodan AYRI; devamsızlık içermez: devamsızlık yalnız simülasyonda sayılır, OK-22). */
    public record Yedek(int ihtiyac, int enYogunGunPlanliSefer) {
    }

    /** Bir plan koşusunun özeti: sefer sayısı, boş sefer, doluluk %. */
    public record Doluluk(String ad, String etiket, int seferSayisi, int bos, double dolulukYuzde, long sureMs) {
    }

    /**
     * Eğri noktası: {@code ek} genel makinist ile ulaşılan en az boş (zarf: ≤ ek kişiyle koşulmuş en iyi); {@code marjinal} önceki noktaya göre eklenen kişi başına kapanan
     * sefer (ilk nokta {@code null}).
     */
    public record EgriNokta(int ek, int bos, double dolulukYuzde, Double marjinal) {
    }

    /** Doluluk hedefi: {@code ulasildi=false} ise {@code ek=null}, {@code bos}/{@code dolulukYuzde} eğrinin en iyi noktasıdır. */
    public record Hedef(int yuzde, boolean ulasildi, Integer ek, int bos, double dolulukYuzde) {
    }

    /** Eklenen genel makinistlerin plandaki baskın kümesi (bağlı gar × araç tipi × hat): kişi, işlediği sefer, haftalık saat. */
    public record DagilimSatiri(String gar, String aracTipi, String hat, int kisi, int seferSayisi, double haftalikSaat) {
    }

    public record NedenSayisi(String neden, int adet) {
    }

    public record KalanBos(Long seferId, String trenNo, LocalDate tarih, String kalkisGar, String varisGar, String neden) {
    }

    /**
     * Simülasyon (sanal GENEL makinist: tarifedeki tüm bröveler; K-20 açık, iyileştirmesiz düz planlayıcı). {@code mevcut}: bugünkü kadroyla doluluk; {@code konumsuz}: aynı kadro,
     * K-20 kapalı (konum/yerleşimin payı); {@code iyilestirmeli}, {@code yolculuk}: ek hesaplar (süre bütçeli, hesaplanmadıysa {@code null} ve {@code atlananlar}'da).
     * {@code egri}/{@code hedefler}: süre bütçesinden bağımsız. {@code dagilim}: {@code dagilimEk} kişilik noktanın planına göre (%99 hedefi, ulaşılamadıysa en iyi nokta).
     * {@code kalanBoslar}: eğrinin en iyi noktasında kalan boşlar ve nedenleri. {@code tamamlandi}: istenen tüm ek hesaplar yapıldı.
     */
    public record Simulasyon(int mevcutAsil, Doluluk mevcut, Doluluk konumsuz, Doluluk iyilestirmeli, Doluluk yolculuk, List<EgriNokta> egri, List<Hedef> hedefler,
            String egriDurma, int egriKosuSayisi, int dagilimEk, String dagilimEtiketi, List<DagilimSatiri> dagilim, List<KalanBos> kalanBoslar, int kalanBosToplam,
            List<NedenSayisi> kalanNedenler, List<String> atlananlar, boolean tamamlandi, long sureMs) {
    }

    /** {@code tabanEk/senaryoEk}: aynı hedef için eğrinin +n değeri; {@code fark = senaryoEk − tabanEk} (ikisi de ulaşıldıysa). */
    public record HedefFark(int yuzde, Integer tabanEk, Integer senaryoEk, Integer fark) {
    }

    /**
     * "Ne olurdu": AYNI mevcut kadroyla taban ve senaryo koşusu. {@code bosFarki} = toplam boş farkı; {@code mevcutYeter} ancak bu artış gürültü eşiğinin
     * ({@code gurultuEsigi}: başlangıç tarihi birkaç gün kayınca da oynayan mertebe) altındaysa. {@code egriFarki}: süre yettiyse hedef bazında +n farkı, yoksa {@code null}.
     */
    public record Senaryo(int ekSeferSayisi, double ekHaftalikSaat, Doluluk taban, Doluluk senaryo, int bosFarki, int gurultuEsigi, boolean mevcutYeter, String gerekce,
            List<HedefFark> egriFarki, double ekTalepKisiEsdegeri, double atilKapasiteKisiEsdegeri, double tabanEksikSaat, double senaryoEksikSaat, boolean tamamlandi, String uyari) {
    }
}
