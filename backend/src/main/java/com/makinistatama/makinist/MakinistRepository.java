package com.makinistatama.makinist;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import jakarta.persistence.LockModeType;

interface MakinistRepository extends JpaRepository<Makinist, Long> {

    boolean existsBySicil(String sicil);

    boolean existsByAdSoyadStartingWith(String onEk);

    List<Makinist> findAllByOrderBySicil();

    /** Varlık yüklemeden (kalıcılık bağlamına girmeden) belirli durumdaki makinist kimlikleri, kimlik sırasıyla. */
    @Query("select m.id from Makinist m where m.durum = :durum and m.tur in :turler order by m.id")
    List<Long> kimlikler(@org.springframework.data.repository.query.Param("durum") MakinistDurum durum,
            @org.springframework.data.repository.query.Param("turler") Collection<PersonelTuru> turler);

    /** Güncellemede sürüm HER ZAMAN artar (yalnız bröve değişse bile): iki düzenleyen aynı sürümden başlarsa ikincisi 409 alır. */
    @Lock(LockModeType.OPTIMISTIC_FORCE_INCREMENT)
    Optional<Makinist> findWithVersionById(Long id);

    /** Atama yazımı için satır kilidi (MK-7): sıralı kimlik, kilitlenmeyi önler. Kilit bekleme süresi için önce {@link #kilitZamanAsimi()}. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    List<Makinist> findByIdInOrderById(Collection<Long> idler);

    /** Bu işlemde (SET LOCAL) kilit bekleme zaman aşımı 5 sn: aşılırsa 409 ISLEM_SURUYOR (HataYonetimi). */
    @Query(value = "select set_config('lock_timeout', '5s', true)", nativeQuery = true)
    String kilitZamanAsimi();
}
