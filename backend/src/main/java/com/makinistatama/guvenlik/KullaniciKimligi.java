package com.makinistatama.guvenlik;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.Collection;
import java.util.HexFormat;
import java.util.List;

import org.springframework.security.core.CredentialsContainer;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import com.makinistatama.kullanici.Kullanici;
import com.makinistatama.kullanici.Rol;

/**
 * Oturumdaki kimlik. Giriş sonrası parola karması silinir; kilit yüklendiği ana göre hesaplanır. {@code parolaIzi} karmanın geri döndürülemez kısa özetidir; {@link KimlikTazelemeFilter} oturumdaki iz ile veritabanındaki güncel iz farklıysa (parola sıfırlandı) oturumu düşürür; karmanın kendisi
 * oturumda tutulmaz.
 */
public final class KullaniciKimligi implements UserDetails, CredentialsContainer {

    private static final long serialVersionUID = 1L;

    private final long id;
    private final String eposta;
    private final String adSoyad;
    private final Rol rol;
    private final boolean aktif;
    private final boolean kilitli;
    private final Long makinistId;
    private final boolean parolaDegistirmeli;
    private String parolaKarma;
    private final String parolaIzi;

    KullaniciKimligi(Kullanici k, Instant simdi) {
        this.id = k.id();
        this.eposta = k.eposta();
        this.adSoyad = k.adSoyad();
        this.rol = k.rol();
        this.aktif = k.aktif();
        this.kilitli = k.kilitBitis() != null && k.kilitBitis().isAfter(simdi);
        this.makinistId = k.makinistId();
        this.parolaDegistirmeli = k.parolaDegistirmeli();
        this.parolaKarma = k.parolaKarma();
        this.parolaIzi = iz(k.parolaKarma());
    }

    /** Karmanın SHA-256 özetinin ilk 8 baytı (hex); karma yoksa boş. */
    private static String iz(String karma) {
        if (karma == null) {
            return "";
        }
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(karma.getBytes(StandardCharsets.UTF_8)), 0, 8);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e); // SHA-256 her JVM'de vardır
        }
    }

    /** Oturum açıldığındaki parola karmasının özeti: parola değişince değişir. */
    String parolaIzi() {
        return parolaIzi;
    }

    public long id() {
        return id;
    }

    public String adSoyad() {
        return adSoyad;
    }

    public Rol rol() {
        return rol;
    }

    /** Makinist hesabında bağlı makinist kartı; personel hesabında null. */
    public Long makinistId() {
        return makinistId;
    }

    /** Geçici parolayla açılmış hesap: yalnız @HerkesGiris uçları kullanabilir (MK-26). */
    public boolean parolaDegistirmeli() {
        return parolaDegistirmeli;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_" + rol.name()));
    }

    @Override
    public String getPassword() {
        return parolaKarma;
    }

    @Override
    public String getUsername() {
        return eposta;
    }

    @Override
    public boolean isAccountNonLocked() {
        return !kilitli;
    }

    @Override
    public boolean isEnabled() {
        return aktif;
    }

    @Override
    public void eraseCredentials() {
        this.parolaKarma = null;
    }
}
