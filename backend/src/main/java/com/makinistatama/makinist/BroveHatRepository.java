package com.makinistatama.makinist;

import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface BroveHatRepository extends JpaRepository<BroveHat, Long> {

    List<BroveHat> findByMakinistIdIn(Collection<Long> makinistIdler);

    /** Toplu silme hemen çalışır (türetilmiş silme Hibernate sırasında ekleme sonrasına kalıp benzersiz anahtarı çakıştırırdı). */
    @Modifying(flushAutomatically = true)
    @Query("delete from BroveHat b where b.makinistId = :makinistId")
    void makinistinTumunuSil(@Param("makinistId") Long makinistId);
}
