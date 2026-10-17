package com.makinistatama.konaklama;

import java.time.Instant;
import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface KonaklamaRepository extends JpaRepository<Konaklama, Long> {

    /** [bas, bit) ile kesişen kayıtlar (makinistId boşsa hepsi), girişe göre. */
    @Query("""
            select k from Konaklama k
            where k.giris < :bit and k.cikis > :bas and (:makinistId is null or k.makinistId = :makinistId)
            order by k.giris, k.id""")
    List<Konaklama> kesisenler(@Param("bas") Instant bas, @Param("bit") Instant bit, @Param("makinistId") Long makinistId);

    /** Aynı makinistin [giris, cikis) ile kesişen başka kaydı var mı ({@code haricId}: güncellenen kaydın kendisi). */
    @Query("""
            select count(k) > 0 from Konaklama k
            where k.makinistId = :makinistId and k.giris < :cikis and k.cikis > :giris and (:haricId is null or k.id <> :haricId)""")
    boolean cakisanVarMi(@Param("makinistId") long makinistId, @Param("giris") Instant giris, @Param("cikis") Instant cikis, @Param("haricId") Long haricId);

    List<Konaklama> findByMakinistIdIn(Collection<Long> makinistIdler);

    /** Girişi [bas, bit) içinde olan kayıtlar (rapor ayı = giriş günü). */
    @Query("select k from Konaklama k where k.giris >= :bas and k.giris < :bit order by k.giris, k.id")
    List<Konaklama> girisAraliginda(@Param("bas") Instant bas, @Param("bit") Instant bit);
}
