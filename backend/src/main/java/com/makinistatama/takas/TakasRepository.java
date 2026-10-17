package com.makinistatama.takas;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface TakasRepository extends JpaRepository<TakasTalebi, Long> {

    @Query("select new com.makinistatama.takas.TakasKisileri(t.kaynakMakinistId, t.hedefMakinistId) from TakasTalebi t where t.id = :id")
    Optional<TakasKisileri> kisiler(@Param("id") long id);

    /** Makinistin taraf olduğu açık talepler ile {@code bas}'tan yeni kapanmışlar, en yeniden eskiye. */
    @Query("""
            select t from TakasTalebi t
            where (t.kaynakMakinistId = :makinist or t.hedefMakinistId = :makinist) and (t.durum in :acik or t.olusturma >= :bas)
            order by t.id desc""")
    List<TakasTalebi> makinistinkiler(@Param("makinist") long makinist, @Param("acik") Collection<TakasDurumu> acik, @Param("bas") Instant bas);

    /** Atamacı listesi: açık talepler ile {@code bas}'tan yeni kapanmışlar. */
    @Query("select t from TakasTalebi t where t.durum in :acik or t.olusturma >= :bas order by t.id desc")
    List<TakasTalebi> tumu(@Param("acik") Collection<TakasDurumu> acik, @Param("bas") Instant bas);

    /** Kota: makinistin verilen andan sonra açtığı talep sayısı (kapananlar ve geri çekilenler dahil). */
    long countByKaynakMakinistIdAndOlusturmaAfter(Long kaynakMakinistId, Instant bas);

    List<TakasTalebi> findByDurumIn(Collection<TakasDurumu> durumlar);

    /** Verilen atamalardan birini kaynak ya da hedef olarak içeren AÇIK talepler. */
    @Query("select t from TakasTalebi t where t.durum in :acik and (t.kaynakAtamaId in :idler or t.hedefAtamaId in :idler)")
    List<TakasTalebi> acikOlanlar(@Param("acik") Collection<TakasDurumu> acik, @Param("idler") Collection<Long> idler);

    List<TakasTalebi> findByHedefMakinistIdAndDurum(Long hedefMakinistId, TakasDurumu durum);
}
