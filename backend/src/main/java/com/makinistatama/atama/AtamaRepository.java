package com.makinistatama.atama;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface AtamaRepository extends JpaRepository<Atama, Long> {

    List<Atama> findBySeferIdIn(Collection<Long> seferIdler);

    /** Tekil sonuç varsayar: YOLCU ile ÇAĞRILMAZ (bir seferde birden çok YOLCU olabilir; MK-38). */
    Optional<Atama> findBySeferIdAndRol(Long seferId, AtamaRol rol);

    Optional<Atama> findBySeferIdAndMakinistId(Long seferId, Long makinistId);

    /** Makinistin [bas, bit] (planlanan gün) görevleri; İPTAL seferler DAHİL (makinist ekranında "İPTAL" etiketiyle görünür). Kalkış sırasıyla. */
    @Query("""
            select a from Atama a join Sefer s on s.id = a.seferId
            where a.makinistId = :makinistId and s.tarih between :bas and :bit
            order by s.planlananKalkis, s.trenNo""")
    List<Atama> makinistinGorevleri(@Param("makinistId") long makinistId, @Param("bas") LocalDate bas, @Param("bit") LocalDate bit);

    /**
     * Makinistin (YOLCU olmayan = sürüş, iptal olmayan) bir sonraki sürüş günü: {@code sonra}'dan SONRA, {@code en_gec} dahil; {@code hatId} verilirse yalnız o hat. Yoksa null.
     * Kaldırma etkisinin ay ölçekli kuralları (K-07/K-08) için: kaldırılan sürüşten sonraki İLK sürüş günü, son sürüş tazeliğine bağlı tek halkadır.
     */
    @Query("""
            select min(s.tarih) from Atama a join Sefer s on s.id = a.seferId
            where a.makinistId = :makinistId and a.rol <> com.makinistatama.atama.AtamaRol.YOLCU and s.durum <> com.makinistatama.sefer.SeferDurumu.IPTAL
              and s.tarih > :sonra and s.tarih <= :enGec and (:hatId is null or s.hatId = :hatId)""")
    LocalDate sonrakiSurusGunu(@Param("makinistId") long makinistId, @Param("sonra") LocalDate sonra, @Param("enGec") LocalDate enGec, @Param("hatId") Long hatId);

    /** Takas: atama kimlik özeti, varlık yüklemeden (kilit öncesi okuma önbelleğe takılmaz). */
    @Query("select new com.makinistatama.atama.AtamaKisa(a.id, a.seferId, a.makinistId, a.rol) from Atama a where a.id in :idler")
    List<AtamaKisa> kisalar(@Param("idler") Collection<Long> idler);

    /** Takas çapraz çakışma denetimi: verilen seferlerin bütün atamalarının kimlik özeti. */
    @Query("select new com.makinistatama.atama.AtamaKisa(a.id, a.seferId, a.makinistId, a.rol) from Atama a where a.seferId in :idler")
    List<AtamaKisa> seferKisalar(@Param("idler") Collection<Long> idler);

    /** Takas hedef listesi: [bas, bit] (planlanan gün) iptal olmayan seferlerin ASIL/YARDIMCI atamaları (YOLCU takas edilmez; MK-45). */
    @Query("""
            select new com.makinistatama.atama.AtamaKisa(a.id, a.seferId, a.makinistId, a.rol)
            from Atama a join Sefer s on s.id = a.seferId
            where s.tarih between :bas and :bit and s.durum <> com.makinistatama.sefer.SeferDurumu.IPTAL
              and a.rol <> com.makinistatama.atama.AtamaRol.YOLCU""")
    List<AtamaKisa> aralikKisalar(@Param("bas") LocalDate bas, @Param("bit") LocalDate bit);

    /**
     * Verilen makinistlerin [bas, bit] (planlanan gün) arasındaki iptal olmayan atamaları; sefer entity'si değil projeksiyon. OK-17: makinistin bir önceki görevi aynı trenin
     * (aynı {@code trenGunu} + tren no) bir önceki parçasıysa makinist zaten trendedir; OK-6 donuk başlangıcı uygulanmaz, {@code atamaGecikmeDk} yerine güncel kalkış gecikmesi
     * döner; aynı koşul {@code devamEden} (hazırlık yok) ve sonraki parça için {@code devamEdecek} (alıkoyma yok) bayraklarını da verir. Kural motoru, kırmızı hesabı, adalet ve puantaj hepsi bu TEK sorgudan beslenir (başlangıç tek kaynak).
     */
    @Query("""
            select new com.makinistatama.atama.AtamaSatiri(a.id, a.seferId, a.rol, a.makinistId, s.tarih, s.trenNo, s.kalkisGarId, s.varisGarId,
                   s.hatId, s.aracTipiId, s.planlananKalkis, s.planlananVaris, s.kalkisGecikmeDk, s.varisGecikmeDk, s.km,
                   case when s.parcaNo > 1 and exists (select 1 from Atama oa join Sefer os on os.id = oa.seferId
                                                       where oa.makinistId = a.makinistId and os.trenNo = s.trenNo and os.trenGunu = s.trenGunu
                                                         and os.parcaNo = s.parcaNo - 1 and os.durum <> com.makinistatama.sefer.SeferDurumu.IPTAL)
                        then s.kalkisGecikmeDk else a.kalkisGecikmeDk end, s.trenTuru, s.parcaNo, s.trenGunu,
                   case when s.parcaNo > 1 and exists (select 1 from Atama oa join Sefer os on os.id = oa.seferId
                                                       where oa.makinistId = a.makinistId and os.trenNo = s.trenNo and os.trenGunu = s.trenGunu
                                                         and os.parcaNo = s.parcaNo - 1 and os.durum <> com.makinistatama.sefer.SeferDurumu.IPTAL) then true else false end,
                   case when s.parcaNo is not null and exists (select 1 from Atama oa join Sefer os on os.id = oa.seferId
                                                       where oa.makinistId = a.makinistId and os.trenNo = s.trenNo and os.trenGunu = s.trenGunu
                                                         and os.parcaNo = s.parcaNo + 1 and os.durum <> com.makinistatama.sefer.SeferDurumu.IPTAL) then true else false end)
            from Atama a join Sefer s on s.id = a.seferId
            where a.makinistId in :makinistler and s.tarih between :bas and :bit and s.durum <> com.makinistatama.sefer.SeferDurumu.IPTAL""")
    List<AtamaSatiri> aralik(@Param("makinistler") Collection<Long> makinistler, @Param("bas") LocalDate bas, @Param("bit") LocalDate bit);

    /** Pencere öncesi hat bazında son varış (toplu; makinist sayısından bağımsız tek sorgu). YOLCU satırı sürüş değildir, sayılmaz. */
    @Query("""
            select new com.makinistatama.atama.OncekiSurus(a.makinistId, s.hatId, max(s.planlananVaris))
            from Atama a join Sefer s on s.id = a.seferId
            where a.makinistId in :makinistler and s.tarih < :bas and s.durum <> com.makinistatama.sefer.SeferDurumu.IPTAL
              and a.rol <> com.makinistatama.atama.AtamaRol.YOLCU
            group by a.makinistId, s.hatId""")
    List<OncekiSurus> pencereOncesiSonSurus(@Param("makinistler") Collection<Long> makinistler, @Param("bas") LocalDate bas);

    /** Gerçekleşmiş (planlanan varışı {@code simdi}'den önce) iptal olmayan atamalardan makinist x hat son varışı (belge panosu: son sürüş türetmesi). */
    @Query("""
            select new com.makinistatama.atama.OncekiSurus(a.makinistId, s.hatId, max(s.planlananVaris))
            from Atama a join Sefer s on s.id = a.seferId
            where s.planlananVaris <= :simdi and s.durum <> com.makinistatama.sefer.SeferDurumu.IPTAL and a.rol <> com.makinistatama.atama.AtamaRol.YOLCU
            group by a.makinistId, s.hatId""")
    List<OncekiSurus> gerceklesenSonSurus(@Param("simdi") java.time.Instant simdi);

    /**
     * Bu işlemde (SET LOCAL) hazırlanmış ifadeler her çağrıda ÖZEL planla çalışır. PostgreSQL ~5. çalıştırmadan sonra GENEL plana geçer; {@link #aralik} gibi 3.000+ parametreli
     * {@code makinist_id in (...)} + tarih aralığı sorgularında genel plan tarih aralığını %0,5 sanıp sefer'i dıştan gezer ve her sefer için diziyi tarar (özel plan ~9 ms, genel ~160-900 ms).
     */
    @Query(value = "select set_config('plan_cache_mode', 'force_custom_plan', true)", nativeQuery = true)
    String ozelPlan();
}
