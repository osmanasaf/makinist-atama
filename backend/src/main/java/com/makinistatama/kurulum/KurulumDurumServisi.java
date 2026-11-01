package com.makinistatama.kurulum;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.makinistatama.degisiklik.DegisiklikServisi;
import com.makinistatama.degisiklik.DegisiklikTuru;
import com.makinistatama.kurum.KurumServisi;
import com.makinistatama.makinist.MakinistDurum;
import com.makinistatama.makinist.MakinistServisi;
import com.makinistatama.makinist.PersonelTuru;
import com.makinistatama.sefer.SeferServisi;
import com.makinistatama.tanim.TanimServisi;
import com.makinistatama.tanim.TanimTipi;

/**
 * İlk kurulum sihirbazının durumu (MK-171): "kurulum tamamlandı" bayrağı SAKLANMAZ, adımlar veriden türetilir (silinen kayıtla çelişmez). Kurum = ad dolu; Tanım = en az 2 gar + 1 hat + 1 araç tipi;
 * Makinist = en az 1 AKTİF makinist; Profil = en az bir kural profili uygulaması (denetim kaydı); Sefer = en az 1 sefer. Yalnız sayılar döner (kişisel veri yok).
 * Tek yazım: kartı gizleme tercihi ({@code kurum.sihirbaz_gizli}).
 */
@Service
@Transactional(readOnly = true)
public class KurulumDurumServisi {

    static final int EN_AZ_GAR = 2;

    private final KurumServisi kurum;
    private final TanimServisi tanimlar;
    private final MakinistServisi makinistler;
    private final DegisiklikServisi degisiklik;
    private final SeferServisi seferler;

    KurulumDurumServisi(KurumServisi kurum, TanimServisi tanimlar, MakinistServisi makinistler, DegisiklikServisi degisiklik, SeferServisi seferler) {
        this.kurum = kurum;
        this.tanimlar = tanimlar;
        this.makinistler = makinistler;
        this.degisiklik = degisiklik;
        this.seferler = seferler;
    }

    public KurulumDurumDto durum() {
        boolean kurumAdi = kurum.ad().isPresent();
        int gar = tanimlar.liste(TanimTipi.GAR).size();
        int hat = tanimlar.liste(TanimTipi.HAT).size();
        int arac = tanimlar.liste(TanimTipi.ARAC_TIPI).size();
        int aktif = makinistler.kimlikler(MakinistDurum.AKTIF, PersonelTuru.MAKINISTLER).size();
        long profil = degisiklik.sayi(DegisiklikTuru.PROFIL_UYGULA);
        long sefer = seferler.sayi();
        List<KurulumDurumDto.Adim> adimlar = List.of(
                new KurulumDurumDto.Adim("KURUM", "Kurum adı ve logo", kurumAdi, kurumAdi ? "Kurum adı girildi" : "Kurum adı girilmedi"),
                new KurulumDurumDto.Adim("TANIM", "Gar, hat ve araç tipi tanımları", gar >= EN_AZ_GAR && hat >= 1 && arac >= 1, gar + " gar, " + hat + " hat, " + arac + " araç tipi"),
                new KurulumDurumDto.Adim("MAKINIST", "Makinist listesi", aktif >= 1, aktif + " aktif makinist"),
                new KurulumDurumDto.Adim("PROFIL", "Kural profili", profil >= 1, profil + " profil uygulaması"),
                new KurulumDurumDto.Adim("SEFER", "İlk seferler", sefer >= 1, sefer + " sefer"));
        return new KurulumDurumDto(adimlar, adimlar.stream().allMatch(KurulumDurumDto.Adim::yapildi), kurum.sihirbazGizli());
    }

    @Transactional
    public KurulumDurumDto sihirbazGizle(boolean gizli) {
        kurum.sihirbazGizle(gizli);
        return durum();
    }
}
