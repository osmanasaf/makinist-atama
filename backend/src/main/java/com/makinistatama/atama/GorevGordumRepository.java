package com.makinistatama.atama;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

interface GorevGordumRepository extends JpaRepository<GorevGordum, Long> {

    Optional<GorevGordum> findByAtamaId(Long atamaId);

    List<GorevGordum> findByAtamaIdIn(Collection<Long> atamaIdler);

    /** Silinen atamanın öksüz onayı (FK yok): atama silme yolları aynı işlemde temizler. Denetim kaydı (GORDUM) kalır. */
    @org.springframework.data.jpa.repository.Modifying(flushAutomatically = true)
    @org.springframework.data.jpa.repository.Query("delete from GorevGordum g where g.atamaId in :idler")
    int atamalariSilinenleriTemizle(@org.springframework.data.repository.query.Param("idler") Collection<Long> idler);
}
