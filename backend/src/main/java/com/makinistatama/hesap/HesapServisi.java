package com.makinistatama.hesap;

import java.time.Clock;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.makinistatama.degisiklik.DegisiklikServisi;
import com.makinistatama.degisiklik.DegisiklikServisi.Kayit;
import com.makinistatama.degisiklik.DegisiklikTuru;
import com.makinistatama.guvenlik.KullaniciKimligi;
import com.makinistatama.kullanici.Kullanici;
import com.makinistatama.kullanici.KullaniciDto;
import com.makinistatama.kullanici.KullaniciIstekleri;
import com.makinistatama.kullanici.KullaniciServisi;
import com.makinistatama.makinist.MakinistDto;
import com.makinistatama.makinist.MakinistDurum;
import com.makinistatama.makinist.MakinistServisi;
import com.makinistatama.ortak.UygulamaHatasi;

/**
 * Kullanıcı ve makinist hesabı işlemlerinin DENETİMLİ sarmalayıcısı (MK-27): {@code kullanici} paketi {@code degisiklik}i göremediği için denetim kaydı burada yazılır.
 * Kayıtlara parola, karma ya da geçici parola ASLA girmez (yalnız rol / durum). Geçici parola {@link KullaniciServisi#geciciParolaUret()} ile üretilir ve yanıtta bir kez döner (MK-36).
 */
@Service
@Transactional
public class HesapServisi {

    private final KullaniciServisi kullanicilar;
    private final MakinistServisi makinistler;
    private final DegisiklikServisi degisiklik;
    private final Clock saat;

    HesapServisi(KullaniciServisi kullanicilar, MakinistServisi makinistler, DegisiklikServisi degisiklik, Clock saat) {
        this.kullanicilar = kullanicilar;
        this.makinistler = makinistler;
        this.degisiklik = degisiklik;
        this.saat = saat;
    }

    // ------------------------------------------------------------ /api/kullanicilar (yönetici)

    @Transactional(readOnly = true)
    public List<KullaniciDto> liste() {
        return kullanicilar.liste();
    }

    public KullaniciDto olustur(KullaniciIstekleri.Olustur istek) {
        KullaniciDto k = kullanicilar.olustur(istek);
        degisiklik.kaydet(Kayit.of(DegisiklikTuru.HESAP_ACILDI, "KULLANICI", k.id()).ayrinti(rolAyrintisi(k.rol().name())));
        return k;
    }

    public KullaniciDto guncelle(long id, KullaniciIstekleri.Guncelle istek) {
        Kullanici eski = kullanicilar.idIle(id).orElseThrow(() -> UygulamaHatasi.bulunamadi("Kullanıcı bulunamadı."));
        String once = eski.rol() + "," + (eski.aktif() ? "aktif" : "pasif");
        KullaniciDto k = kullanicilar.guncelle(id, istek);
        degisiklik.kaydet(Kayit.of(DegisiklikTuru.HESAP_GUNCELLENDI, "KULLANICI", id).makinist(k.makinistId())
                .degisim(once, k.rol() + "," + (k.aktif() ? "aktif" : "pasif")).ayrinti(rolAyrintisi(k.rol().name())));
        return k;
    }

    public void parolaSifirla(long id, String yeniParola) {
        kullanicilar.parolaSifirla(id, yeniParola);
        degisiklik.kaydet(Kayit.of(DegisiklikTuru.HESAP_PAROLA_SIFIRLANDI, "KULLANICI", id));
    }

    // ------------------------------------------------------------ makinist hesabı (personel)

    @Transactional(readOnly = true)
    public HesapDto hesap(long makinistId) {
        makinistler.getir(makinistId); // yoksa 404
        return kullanicilar.makinistHesabiIle(makinistId).map(this::dto).orElse(HesapDto.yok());
    }

    public GeciciParolaDto hesapAc(long makinistId) {
        MakinistDto m = aktifMakinist(makinistId);
        if (m.sicil().contains("@")) {
            throw UygulamaHatasi.dogrulama("sicil", "Sicil '@' içeriyorsa hesap açılamaz (giriş adı e-postayla karışır).");
        }
        String gecici = KullaniciServisi.geciciParolaUret();
        KullaniciDto k = kullanicilar.makinistHesabiAc(makinistId, m.sicil(), m.adSoyad(), gecici);
        degisiklik.kaydet(Kayit.of(DegisiklikTuru.HESAP_ACILDI, "KULLANICI", k.id()).makinist(makinistId).ayrinti(rolAyrintisi("MAKINIST")));
        return new GeciciParolaDto(hesap(makinistId), gecici);
    }

    public GeciciParolaDto parolaSifirla(long makinistId) {
        aktifMakinist(makinistId);
        String gecici = KullaniciServisi.geciciParolaUret();
        KullaniciDto k = kullanicilar.makinistParolaSifirla(makinistId, gecici);
        degisiklik.kaydet(Kayit.of(DegisiklikTuru.HESAP_PAROLA_SIFIRLANDI, "KULLANICI", k.id()).makinist(makinistId).ayrinti(rolAyrintisi("MAKINIST")));
        return new GeciciParolaDto(hesap(makinistId), gecici);
    }

    public HesapDto kilidiAc(long makinistId) {
        makinistler.getir(makinistId);
        KullaniciDto k = kullanicilar.kilidiAc(makinistId);
        degisiklik.kaydet(Kayit.of(DegisiklikTuru.HESAP_KILIT_ACILDI, "KULLANICI", k.id()).makinist(makinistId).ayrinti(rolAyrintisi("MAKINIST")));
        return hesap(makinistId);
    }

    // ------------------------------------------------------------ kendi parolası (tüm roller)

    /** Yanlış eski parola sayaca işlenir ve işlem geri alınmaz (kaba kuvvet engeli); başarıda oturum, parola izi değiştiği için düşer. */
    @Transactional(noRollbackFor = UygulamaHatasi.class)
    public void kendiParolasiniDegistir(KullaniciKimligi kimlik, String eskiParola, String yeniParola) {
        kullanicilar.kendiParolasiniDegistir(kimlik.id(), eskiParola, yeniParola);
        degisiklik.kaydet(Kayit.of(DegisiklikTuru.PAROLA_DEGISTI, "KULLANICI", kimlik.id()).makinist(kimlik.makinistId()).ayrinti(rolAyrintisi(kimlik.rol().name())));
    }

    // ------------------------------------------------------------ yardımcılar

    private MakinistDto aktifMakinist(long makinistId) {
        MakinistDto m = makinistler.getir(makinistId);
        if (m.durum() != MakinistDurum.AKTIF && m.durum() != MakinistDurum.ASKIDA) {
            throw new UygulamaHatasi(HttpStatus.CONFLICT, "MAKINIST_PASIF", "Pasif makinist için hesap açılamaz ya da parolası sıfırlanamaz.", null);
        }
        return m;
    }

    private HesapDto dto(Kullanici k) {
        return new HesapDto(true, k.eposta(), k.aktif(), k.parolaDegistirmeli(), k.kilitBitis() != null && k.kilitBitis().isAfter(saat.instant()));
    }

    private static String rolAyrintisi(String rol) {
        return "{\"rol\":\"" + rol + "\"}";
    }
}
