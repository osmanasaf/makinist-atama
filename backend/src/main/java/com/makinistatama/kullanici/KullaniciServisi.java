package com.makinistatama.kullanici;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.makinistatama.ortak.UygulamaHatasi;

@Service
@Transactional(readOnly = true)
public class KullaniciServisi {

    /** 5 ardışık hatalı parola → 15 dakika kilit. */
    static final int KILIT_ESIGI = 5;
    static final Duration KILIT_SURESI = Duration.ofMinutes(15);
    private static final int PAROLA_EN_AZ = 10;
    private static final int PAROLA_EN_COK_BAYT = 72; // BCrypt sınırı bayt cinsinden; Türkçe harfler 2 bayt

    private final KullaniciRepository depo;
    private final PasswordEncoder sifreleyici;
    private final Clock saat;

    KullaniciServisi(KullaniciRepository depo, PasswordEncoder sifreleyici, Clock saat) {
        this.depo = depo;
        this.sifreleyici = sifreleyici;
        this.saat = saat;
    }

    public List<KullaniciDto> liste() {
        return depo.findAllByOrderByAdSoyad().stream().map(KullaniciDto::of).toList();
    }

    public long sayi() {
        return depo.count();
    }

    public Optional<Kullanici> epostaIle(String eposta) {
        return depo.findByEposta(normallestir(eposta));
    }

    public Optional<Kullanici> idIle(long id) {
        return depo.findById(id);
    }

    @Transactional
    public KullaniciDto olustur(KullaniciIstekleri.Olustur istek) {
        if (istek.rol() == Rol.MAKINIST) {
            throw makinistRoluYonetimdenAcilmaz();
        }
        String eposta = normallestir(istek.eposta());
        parolaKontrol(istek.parola(), null); // personel parolası: giriş adı kuralı yalnız kendi parolasını değiştirmede (MK-36)
        if (depo.existsByEposta(eposta)) {
            throw epostaKayitli();
        }
        try {
            return KullaniciDto.of(depo.saveAndFlush(
                    new Kullanici(eposta, istek.adSoyad().trim(), sifreleyici.encode(istek.parola()), istek.rol())));
        } catch (DataIntegrityViolationException e) { // eşzamanlı aynı e-posta
            throw epostaKayitli();
        }
    }

    @Transactional
    public KullaniciDto guncelle(long id, KullaniciIstekleri.Guncelle istek) {
        Kullanici k = depo.findById(id).orElseThrow(() -> UygulamaHatasi.bulunamadi("Kullanıcı bulunamadı."));
        if ((k.rol() == Rol.MAKINIST) != (istek.rol() == Rol.MAKINIST)) {
            throw makinistRoluYonetimdenAcilmaz(); // makinist hesabının rolü değişmez, personel hesabı makinist yapılamaz
        }
        boolean yoneticiKalacak = istek.rol() == Rol.YONETICI && istek.aktif();
        if (k.rol() == Rol.YONETICI && k.aktif() && !yoneticiKalacak && depo.findByRolAndAktifTrue(Rol.YONETICI).size() <= 1) {
            throw UygulamaHatasi.cakisma("SON_YONETICI", "Son aktif yönetici pasifleştirilemez ya da rolü düşürülemez.");
        }
        k.guncelle(istek.adSoyad().trim(), istek.rol(), istek.aktif());
        return KullaniciDto.of(k);
    }

    @Transactional
    public void parolaSifirla(long id, String yeniParola) {
        Kullanici k = depo.findById(id).orElseThrow(() -> UygulamaHatasi.bulunamadi("Kullanıcı bulunamadı."));
        if (k.rol() == Rol.MAKINIST) {
            throw new UygulamaHatasi(HttpStatus.BAD_REQUEST, "MAKINIST_HESABI", "Makinist hesabının parolası makinist kartındaki \"Parola sıfırla\" ile verilir.", null);
        }
        parolaKontrol(yeniParola, null);
        k.parolaDegistir(sifreleyici.encode(yeniParola));
    }

    @Transactional
    public void girisBasarili(long id) {
        depo.sayaciSifirla(id);
    }

    /** Yalnız var olan aktif kullanıcıda sayılır; var olmayan e-posta hiçbir iz bırakmaz. */
    @Transactional
    public void hataliGiris(String eposta) {
        String e = normallestir(eposta);
        Instant simdi = saat.instant();
        depo.suresiDolanKilidiTemizle(e, simdi);
        depo.hataliGirisKaydet(e, KILIT_ESIGI, simdi.plus(KILIT_SURESI));
    }

    static String normallestir(String eposta) {
        return eposta.trim().toLowerCase(Locale.ROOT);
    }

    private static UygulamaHatasi epostaKayitli() {
        return UygulamaHatasi.cakisma("EPOSTA_KAYITLI", "Bu e-posta adresi zaten kayıtlı.");
    }

    /** Bean Validation karakter sayar; BCrypt bayt keser: ikisi de denetlenir. */
    static void parolaKontrol(String parola, String girisAdi) {
        parolaKontrol(parola, girisAdi, "parola");
    }

    static void parolaKontrol(String parola, String girisAdi, String alan) {
        if (parola.length() < PAROLA_EN_AZ) {
            throw UygulamaHatasi.dogrulama(alan, "Parola en az 10 karakter olmalı.");
        }
        if (parola.getBytes(StandardCharsets.UTF_8).length > PAROLA_EN_COK_BAYT) {
            throw UygulamaHatasi.dogrulama(alan, "Parola çok uzun (en fazla 72 bayt; Türkçe harfler 2 bayt sayılır).");
        }
        if (girisAdi != null && !girisAdi.isBlank() && parola.toLowerCase(Locale.ROOT).contains(girisAdi.toLowerCase(Locale.ROOT))) {
            throw UygulamaHatasi.dogrulama(alan, "Parola giriş adını (sicil ya da e-posta) içeremez.");
        }
    }

    // ------------------------------------------------------------ makinist hesabı (F2a, MK-26/MK-36)

    private static final String ALFABE = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz23456789";
    private static final int GECICI_UZUNLUK = 12;
    private static final java.security.SecureRandom RASTGELE = new java.security.SecureRandom();

    /** Geçici parola: {@code SecureRandom}, 12 karakter, karışan karakterler (0/O/1/l/I) hariç. Hiçbir yerde saklanmaz/loglanmaz. */
    public static String geciciParolaUret() {
        StringBuilder s = new StringBuilder(GECICI_UZUNLUK);
        for (int i = 0; i < GECICI_UZUNLUK; i++) {
            s.append(ALFABE.charAt(RASTGELE.nextInt(ALFABE.length())));
        }
        return s.toString();
    }

    public Optional<Kullanici> makinistHesabiIle(long makinistId) {
        return depo.findByMakinistId(makinistId);
    }

    /** Verilen makinistlerden aktif hesabı olanlar (MK-33: atamacı görünürlüğü "hesap yok" için; tek sorgu, N+1 yok). */
    public java.util.Set<Long> makinistHesaplilari(java.util.Collection<Long> makinistIdler) {
        return makinistIdler.isEmpty() ? java.util.Set.of() : new java.util.HashSet<>(depo.aktifHesapliMakinistler(makinistIdler));
    }

    /** Geçici parolalı makinist hesabı açar (giriş adı = sicil). Aynı makinist için ikinci hesap 409 {@code HESAP_VAR} (UNIQUE ihlali de buna çevrilir). */
    @Transactional
    public KullaniciDto makinistHesabiAc(long makinistId, String sicil, String adSoyad, String geciciParola) {
        String girisAdi = normallestir(sicil);
        if (depo.findByMakinistId(makinistId).isPresent() || depo.existsByEposta(girisAdi)) {
            throw hesapVar();
        }
        try {
            return KullaniciDto.of(depo.saveAndFlush(Kullanici.makinistHesabi(girisAdi, adSoyad.trim(), sifreleyici.encode(geciciParola), makinistId)));
        } catch (DataIntegrityViolationException e) { // eşzamanlı ikinci hesap
            throw hesapVar();
        }
    }

    /** Yeni geçici parola: bayrak açılır, parola izi değişir (açık oturumlar düşer), kilit kalkar. */
    @Transactional
    public KullaniciDto makinistParolaSifirla(long makinistId, String geciciParola) {
        Kullanici k = makinistHesabi(makinistId);
        k.geciciParolaVer(sifreleyici.encode(geciciParola));
        return KullaniciDto.of(k);
    }

    @Transactional
    public KullaniciDto kilidiAc(long makinistId) {
        Kullanici k = makinistHesabi(makinistId);
        k.kilidiSifirla();
        return KullaniciDto.of(k);
    }

    private Kullanici makinistHesabi(long makinistId) {
        return depo.findByMakinistId(makinistId).orElseThrow(() -> UygulamaHatasi.bulunamadi("Bu makinistin hesabı yok."));
    }

    /**
     * Kendi parolasını değiştirme (tüm roller). Eski parola yanlışsa başarısız giriş sayacına işler (çalınan oturumdan kaba kuvvet) ve 400 {@code PAROLA_HATALI} verir;
     * sayaç artışı geri alınmasın diye bu yöntem UygulamaHatasi'nda işlemi geri almaz. Başarıda bayrak kapanır ve parola izi değiştiği için oturum düşer.
     */
    @Transactional(noRollbackFor = UygulamaHatasi.class)
    public void kendiParolasiniDegistir(long id, String eskiParola, String yeniParola) {
        Kullanici k = depo.kilitliGetir(id).orElseThrow(() -> UygulamaHatasi.bulunamadi("Kullanıcı bulunamadı."));
        // Oturumlu kaba kuvvet sınırı, GİRİŞ kilidinden bağımsızdır (anonim yanlış girişler oturumlu kullanıcının parola değişimini kilitlemesin); satır kilidi altında ve doğrulamadan
        // ÖNCE sayılır: eşzamanlı patlamada sınır gevşemez. Kilitliyken deneme sayılmaz (süre uzamaz).
        Instant simdi = saat.instant();
        if (k.parolaKilitli(simdi)) {
            throw new UygulamaHatasi(HttpStatus.TOO_MANY_REQUESTS, "COK_FAZLA_DENEME", "Çok fazla hatalı deneme yapıldı. Parola değişimi 15 dakika kilitlendi.", null);
        }
        k.parolaDenemesiSay(simdi, KILIT_ESIGI, KILIT_SURESI);
        if (!sifreleyici.matches(eskiParola, k.parolaKarma())) {
            hataliGiris(k.eposta()); // giriş sayacına da işler: çalıntı oturumdan tahmin edilen parola girişte de sayılır
            throw new UygulamaHatasi(HttpStatus.BAD_REQUEST, "PAROLA_HATALI", "Eski parola hatalı.", java.util.Map.of("alanlar", java.util.Map.of("eskiParola", "Eski parola hatalı.")));
        }
        k.parolaDenemesiniSifirla(); // eski parolayı bilen kullanıcı yeni parola kuralları yüzünden kilitlenmesin
        parolaKontrol(yeniParola, k.eposta(), "yeniParola");
        if (yeniParola.chars().noneMatch(Character::isLetter) || yeniParola.chars().noneMatch(Character::isDigit)) { // makinistin kendi seçtiği parola: yalnız uzunluk yetmez (05-review INFO 2)
            throw UygulamaHatasi.dogrulama("yeniParola", "Parola en az bir harf ve bir rakam içermeli.");
        }
        if (sifreleyici.matches(yeniParola, k.parolaKarma())) {
            throw UygulamaHatasi.dogrulama("yeniParola", "Yeni parola eskisiyle aynı olamaz.");
        }
        k.parolaDegistir(sifreleyici.encode(yeniParola));
    }

    private static UygulamaHatasi makinistRoluYonetimdenAcilmaz() {
        return new UygulamaHatasi(HttpStatus.BAD_REQUEST, "MAKINIST_ROLU", "Makinist hesabı kullanıcı yönetiminden değil, makinist kartından açılır.",
                java.util.Map.of("alanlar", java.util.Map.of("rol", "Makinist rolü burada seçilemez.")));
    }

    private static UygulamaHatasi hesapVar() {
        return UygulamaHatasi.cakisma("HESAP_VAR", "Bu makinistin zaten bir hesabı var.");
    }
}
