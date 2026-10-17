package com.makinistatama.parametre;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import com.makinistatama.kural.Siddet;

import jakarta.persistence.LockModeType;

interface OzelKuralRepository extends JpaRepository<OzelKuralKaydi, Long> {

    List<OzelKuralKaydi> findAllByOrderByIdAsc();

    /** Yazma başında tüm satırları kilitler: açık kural sınırı (sayım + yazma) eşzamanlı yazımlarda sırayla işler. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select k from OzelKuralKaydi k order by k.id")
    List<OzelKuralKaydi> kilitle();

    /** Aktif (KAPALI olmayan) kurallar: motora giren liste. */
    List<OzelKuralKaydi> findBySiddetNotOrderByIdAsc(Siddet siddet);

    long countBySiddetNot(Siddet siddet);
}
