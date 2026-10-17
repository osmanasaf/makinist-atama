package com.makinistatama.makinist;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface IzinRepository extends JpaRepository<Izin, Long> {

    List<Izin> findByMakinistIdInOrderByBaslangic(Collection<Long> makinistIdler);

    long countByMakinistIdAndDurum(Long makinistId, IzinDurumu durum);

    /** [bas, bit] ile kesişen TALEP/ONAY izinleri, erken başlayan önce (makinist ucundaki hata mesajı için ilki gösterilir). */
    @Query("select i from Izin i where i.makinistId = :makinistId and i.durum <> com.makinistatama.makinist.IzinDurumu.RED and i.baslangic <= :bit and i.bitis >= :bas order by i.baslangic, i.id")
    List<Izin> cakisanlar(@Param("makinistId") long makinistId, @Param("bas") LocalDate bas, @Param("bit") LocalDate bit);

    /** Makinistin TALEP/ONAY durumundaki izinlerinden biri [bas, bit] (iki uç dahil) ile kesişiyor mu. */
    @Query("select count(i) > 0 from Izin i where i.makinistId = :makinistId and i.durum <> com.makinistatama.makinist.IzinDurumu.RED and i.baslangic <= :bit and i.bitis >= :bas")
    boolean cakisanVarMi(@Param("makinistId") long makinistId, @Param("bas") LocalDate bas, @Param("bit") LocalDate bit);
}
