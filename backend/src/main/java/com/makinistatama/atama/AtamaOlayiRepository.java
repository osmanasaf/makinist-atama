package com.makinistatama.atama;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

interface AtamaOlayiRepository extends JpaRepository<AtamaOlayi, Long> {

    /** Makinistin açık (kapatılmamış) kalemleri, {@code bas}'tan yeni olanlar, en yeniden eskiye. */
    List<AtamaOlayi> findByMakinistIdAndKapatildiZamanIsNullAndZamanGreaterThanEqualOrderByZamanDescIdDesc(Long makinistId, Instant bas);

    Optional<AtamaOlayi> findByIdAndMakinistId(Long id, Long makinistId);
}
