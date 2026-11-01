package com.makinistatama.tanim;

import java.time.LocalDate;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

interface ResmiTatilRepository extends JpaRepository<ResmiTatil, LocalDate> {

    List<ResmiTatil> findAllByOrderByTarihAsc();

    List<ResmiTatil> findByTarihBetweenOrderByTarihAsc(LocalDate bas, LocalDate bit);
}
