package com.makinistatama.kullanici;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;

interface KullaniciRepository extends JpaRepository<Kullanici, Long> {

    Optional<Kullanici> findByEposta(String eposta);

    Optional<Kullanici> findByMakinistId(Long makinistId);

    boolean existsByEposta(String eposta);

    /** Verilen makinistlerden AKTİF hesabı olanların kimlikleri (tek sorgu). */
    @Query("select k.makinistId from Kullanici k where k.makinistId in :idler and k.aktif = true")
    List<Long> aktifHesapliMakinistler(@Param("idler") java.util.Collection<Long> idler);

    List<Kullanici> findAllByOrderByAdSoyad();

    /** Kendi-parola değişimi: aynı kullanıcının eşzamanlı denemeleri sıraya girsin (sayım ve kilit gevşemesin). */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select k from Kullanici k where k.id = :id")
    Optional<Kullanici> kilitliGetir(@Param("id") Long id);


    /** Son-yönetici denetimi: eşzamanlı iki değişiklik sıraya girsin diye satırlar kilitlenir. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    List<Kullanici> findByRolAndAktifTrue(Rol rol);

    /** Sayaç artışı atomik UPDATE'tir (satırda versiyon yok, çakışma olmaz); eşiğe ulaşınca kilit konur. */
    @Modifying
    @Query("""
            update Kullanici k
               set k.basarisizGiris = k.basarisizGiris + 1,
                   k.kilitBitis = case when k.basarisizGiris + 1 >= :esik then :kilitBitis else k.kilitBitis end
             where k.eposta = :eposta and k.aktif = true
            """)
    int hataliGirisKaydet(@Param("eposta") String eposta, @Param("esik") int esik, @Param("kilitBitis") Instant kilitBitis);

    /** Süresi dolan kilit yeni hata sayılmadan önce temizlenir: kullanıcı yeniden 5 hak kazanır. */
    @Modifying
    @Query("""
            update Kullanici k set k.basarisizGiris = 0, k.kilitBitis = null
             where k.eposta = :eposta and k.kilitBitis is not null and k.kilitBitis <= :simdi
            """)
    int suresiDolanKilidiTemizle(@Param("eposta") String eposta, @Param("simdi") Instant simdi);

    @Modifying
    @Query("update Kullanici k set k.basarisizGiris = 0, k.kilitBitis = null where k.id = :id")
    int sayaciSifirla(@Param("id") Long id);
}
