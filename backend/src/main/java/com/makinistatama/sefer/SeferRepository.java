package com.makinistatama.sefer;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface SeferRepository extends JpaRepository<Sefer, Long> {

    /** Gün listesi: planlanan gün, kalkış (planlanan) sırasıyla; aynı dakikada tren no sırası. */
    List<Sefer> findByTarihOrderByPlanlananKalkisAscTrenNoAsc(LocalDate tarih);

    /**
     * Tren kimliği {@code (COALESCE(trenGunu, tarih), trenNo)} (MK-60): parçalı trenin parçaları gece aşıp ertesi güne düşse de tren günü
     * zincirin ilk parçasının tarihidir. Spring Data'nın null-parametre davranışına güvenmemek için açık sorgu.
     */
    @Query("select count(s) > 0 from Sefer s where coalesce(s.trenGunu, s.tarih) = :gun and s.trenNo = :trenNo")
    boolean trenVarMi(@Param("gun") LocalDate gun, @Param("trenNo") String trenNo);

    /** Yükleme: verilen günlerde tren günü olan (parçasız {@code tarih}'i ya da parçalı {@code trenGunu}'su) seferler. */
    @Query("select s from Sefer s where s.tarih in :gunler or s.trenGunu in :gunler")
    List<Sefer> trenGunuIn(@Param("gunler") Collection<LocalDate> gunler);

    /** Parça zinciri (MK-60): tam eşleşme, parça sırasıyla. */
    List<Sefer> findByTrenNoAndTrenGunuOrderByParcaNo(String trenNo, LocalDate trenGunu);

    /**
     * Birleştirme (MK-123): zincir satırlarını PARÇA SIRASIYLA (parça 1 dahil; gecikme / iptal yayılımıyla aynı sıra: kilitlenme yok) gerçek {@code FOR UPDATE} ile kilitler.
     * JPA {@code PESSIMISTIC_WRITE} PostgreSQL'de {@code FOR NO KEY UPDATE} üretir; o kilit {@code atama.sefer_id} FK'sinin {@code KEY SHARE}'iyle çakışmaz, yani eşzamanlı ata'yı
     * beklemez (QA BUG-1: atama sessizce silinirdi). {@code FOR UPDATE} ata'nın FK kilidiyle çakışır: ya ata biter (birleştirme farkı görür, 409) ya birleştirme biter (ata FK hatası alır).
     */
    @Query(value = "select * from sefer where id in (:idler) order by parca_no for update", nativeQuery = true)
    List<Sefer> zinciriKilitle(@Param("idler") Collection<Long> idler);

    List<Sefer> findByTarihBetweenOrderByPlanlananKalkisAscTrenNoAsc(LocalDate bas, LocalDate bit);

    List<Sefer> findByTrenNoIgnoreCase(String trenNo);
}
