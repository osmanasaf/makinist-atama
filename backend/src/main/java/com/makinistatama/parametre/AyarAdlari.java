package com.makinistatama.parametre;

import java.util.Map;

/**
 * Ayar, parametre ve kural şiddetlerinin okunur adları (Ayarlar ekranı satır başlığı, denetim raporu "hangi ayar değişti"). Anahtar ya da kural kodu → ad.
 * Listede olmayan anahtar kendi metniyle döner (yeni ayar eklenip ad unutulursa ekran bozulmaz; {@code AyarKatalogTest} her tohum satırının adını arar).
 */
public final class AyarAdlari {

    private static final Map<String, String> ADLAR = Map.ofEntries(
            // kural parametreleri
            Map.entry("adalet.olcut", "Adil dağıtım ölçütü"),
            Map.entry("belge.sinav_gecerlilik_ay", "Araç ve hat sınavı geçerlilik süresi"),
            Map.entry("belge.uyari_gun", "Belge bitişi uyarı süresi"),
            Map.entry("brove.surus_ara_max_ay", "Hiç sürüş yapmadan geçebilecek en uzun süre"),
            Map.entry("dinlenme.dis_merkez_saat", "Dış merkezde dinlenme süresi"),
            Map.entry("dinlenme.ev_saat", "Bağlı garda dinlenme süresi"),
            Map.entry("gece.ard_hafta_max", "Üst üste gece haftası sayısı"),
            Map.entry("gece.baslangic", "Gece penceresi başlangıcı"),
            Map.entry("gece.bitis", "Gece penceresi bitişi"),
            Map.entry("gece.gorev_orani", "Gece görevi sayılma oranı"),
            Map.entry("gece.hafta_min_gorev", "Gece haftası için en az gece nöbeti"),
            Map.entry("gece.max_saat", "Gece görevi azami süresi"),
            Map.entry("gorev.alikoyma_dk", "Varıştan sonra alıkoyma süresi"),
            Map.entry("gorev.hazirlik_dk", "Kalkıştan önce hazırlık süresi"),
            Map.entry("gunluk.max_saat", "Günlük görev süresi üst sınırı"),
            Map.entry("haftalik.max_saat", "Haftalık görev süresi üst sınırı"),
            Map.entry("haftalik.normal_saat", "Haftalık normal çalışma süresi"),
            Map.entry("hafta_tatili.min_saat", "Haftalık kesintisiz dinlenme (hafta tatili)"),
            Map.entry("hat.ara_max_ay", "Bir hatta sürüş yapmadan geçebilecek en uzun süre"),
            Map.entry("nobet.asgari_aktarma_dk", "Nöbet içinde asgari aktarma süresi"),
            Map.entry("nobet.ayirma_saat", "Nöbet ayırma süresi"),
            Map.entry("nobet.bekleme_sayilir", "Nöbet içi beklemeyi çalışmadan say"),
            Map.entry("puantaj.asgari_gunluk_saat", "Puantajda asgari günlük süre"),
            Map.entry("puantaj.fazla_mesai_carpani", "Fazla mesai ücret çarpanı"),
            Map.entry("puantaj.gece_bitis", "Puantaj gece saati bitişi"),
            Map.entry("puantaj.gece_tazminat_orani", "Gece saati tazminat oranı"),
            Map.entry("puantaj.km_birim_tutari", "Kilometre başına tutar"),
            Map.entry("puantaj.saat_ucreti", "Saat ücreti"),
            Map.entry("sefer.max_saat", "Tek sefer süresi üst sınırı"),
            Map.entry("yedek.min", "Günlük asgari yedek makinist sayısı"),
            Map.entry("yedek.oran", "Yedek makinist oranı"),
            Map.entry("yillik.fazla_mesai_max_saat", "Yıllık fazla mesai üst sınırı"),
            // uygulama ayarları
            Map.entry("gordum.kalem_gun", "Elinden alınan görev bildiriminin ekranda kalma süresi"),
            Map.entry("gordum.uyari_saat", "\"Gördüm\" vurgusu için kalkışa kalan süre"),
            Map.entry("iyilestir.sure_sn", "Daha çok sefer doldurma süre bütçesi"),
            Map.entry("iyilestir.tur", "Daha çok sefer doldurmada denenen plan sayısı"),
            Map.entry("izin.en_cok_acik_talep", "Aynı anda bekleyen izin talebi sayısı"),
            Map.entry("izin.en_ileri_gun", "İzin talebi için en ileri tarih"),
            Map.entry("izin.en_uzun_gun", "Tek izin talebinin en uzun süresi"),
            Map.entry("kadro.is_parcacigi", "Kadro hesabında paralel iş parçacığı sayısı"),
            Map.entry("konaklama.gece_min_dk", "Konaklamada gece sayılması için en az kesişim"),
            Map.entry("konaklama.oneri_min_saat", "Konaklama önerisi için en az boşluk"),
            Map.entry("konduktor.yolcu_trenine_gerekli", "Yolcu trenlerinde kondüktör gerekli"),
            Map.entry("makinist.gorunur_gun", "Makinistin göreceği gün sayısı"),
            Map.entry("sefer.parca_azami_saat", "Excel ile açılan parçanın en uzun süresi"),
            Map.entry("takas.gunluk_acma", "Günlük takas ve bırakma talebi sayısı"),
            Map.entry("takas.kapanmis_gun", "Sonuçlanan taleplerin listede kalma süresi"),
            Map.entry("takas.karsi_onay_saat", "Karşı makinistin yanıt süresi"),
            Map.entry("takas.son_saat", "Takas ve bırakma için kalkışa en geç süre"),
            Map.entry("yolculuk.aktarma_dk", "Yolculuk önerisinde asgari aktarma süresi"),
            Map.entry("yolculuk.arama_saat", "Yolculuk önerisi arama penceresi"),
            Map.entry("yolculuk.calisma_sayilir", "Yolculuk süresini çalışmadan say"),
            Map.entry("yolculuk.oksuz_saat", "Yolculuk sonrası görev arama penceresi"),
            Map.entry("yolculuk.yuk_trenine_binilir", "Yolculukta yük trenine binilebilir"),
            // kural şiddetleri
            Map.entry("K-01", "Makinist durumu"),
            Map.entry("K-02", "Ehliyet geçerliliği"),
            Map.entry("K-03", "Sağlık raporu geçerliliği"),
            Map.entry("K-04", "Psikoteknik raporu geçerliliği"),
            Map.entry("K-05", "Araç tipi yetkisi"),
            Map.entry("K-06", "Hat yetkisi"),
            Map.entry("K-07", "Hatta son sürüşten bu yana geçen süre"),
            Map.entry("K-08", "Hiç sürüş yapmadan geçen süre (bröve düşmesi)"),
            Map.entry("K-09", "Onaylı izin günü"),
            Map.entry("K-11", "Bağlı garda dinlenme"),
            Map.entry("K-12", "Dış merkezde dinlenme"),
            Map.entry("K-13", "Günlük görev süresi"),
            Map.entry("K-14", "Haftalık görev süresi"),
            Map.entry("K-15", "Yıllık fazla mesai"),
            Map.entry("K-16", "Gece nöbeti azami süresi"),
            Map.entry("K-17", "Gece çalışılan haftada fazla mesai"),
            Map.entry("K-18", "Üst üste gece haftaları"),
            Map.entry("K-19", "Haftalık kesintisiz dinlenme"),
            Map.entry("K-20", "Konum sürekliliği"),
            Map.entry("K-21", "Tek sefer süresi"),
            Map.entry("K-22", "Geçmiş tarihli sefere atama"),
            Map.entry("K-23", "Nöbet içinde aktarma süresi"));

    private AyarAdlari() {
    }

    /** Okunur ad; bilinmeyen anahtar kendi metniyle döner. */
    public static String ad(String anahtar) {
        return ADLAR.getOrDefault(anahtar, anahtar);
    }

    /** Yalnız test için: kayıtlı (adı olan) anahtar mı. */
    static boolean var(String anahtar) {
        return ADLAR.containsKey(anahtar);
    }
}
