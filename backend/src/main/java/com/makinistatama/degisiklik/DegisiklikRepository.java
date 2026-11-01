package com.makinistatama.degisiklik;

import java.time.Instant;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

interface DegisiklikRepository extends JpaRepository<DegisiklikKaydi, Long> {

    List<DegisiklikKaydi> findByZamanGreaterThanEqualAndZamanLessThanOrderByZamanAscIdAsc(Instant bas, Instant bit);

    List<DegisiklikKaydi> findBySeferIdInAndTurInOrderByIdAsc(java.util.Collection<Long> seferIdler, java.util.Collection<DegisiklikTuru> turler);

    long countByTur(DegisiklikTuru tur);

    long countByZamanGreaterThanEqualAndZamanLessThan(Instant bas, Instant bit);
}
