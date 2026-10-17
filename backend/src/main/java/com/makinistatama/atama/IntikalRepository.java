package com.makinistatama.atama;

import java.time.Instant;
import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface IntikalRepository extends JpaRepository<Intikal, Long> {

    /** Kural motoruna giren intikaller (yalnız PLANLI): kalkışı [bas, bit) içinde olan, verilen makinistlerin. {@link SatirKaynagi} tek çağıran. */
    @Query("""
            select i from Intikal i
            where i.makinistId in :makinistler and i.durum = com.makinistatama.atama.IntikalDurumu.PLANLI and i.kalkis >= :bas and i.kalkis < :bit""")
    List<Intikal> satirlar(@Param("makinistler") Collection<Long> makinistler, @Param("bas") Instant bas, @Param("bit") Instant bit);

    /** Liste ucu: kalkışı [bas, bit) içinde (IPTAL dahil); {@code makinistId} boşsa hepsi. */
    @Query("""
            select i from Intikal i
            where i.kalkis >= :bas and i.kalkis < :bit and (:makinistId is null or i.makinistId = :makinistId)
            order by i.kalkis, i.id""")
    List<Intikal> aralik(@Param("bas") Instant bas, @Param("bit") Instant bit, @Param("makinistId") Long makinistId);
}
