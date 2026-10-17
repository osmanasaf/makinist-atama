package com.makinistatama.tanim;

import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

interface TanimRepository extends JpaRepository<Tanim, Long> {

    /** Kimlik sırası: kullanıcının eklediği yeni tanımlar sona gelir. */
    List<Tanim> findByTipOrderByIdAsc(TanimTipi tip);

    List<Tanim> findByIdIn(Collection<Long> idler);
}
