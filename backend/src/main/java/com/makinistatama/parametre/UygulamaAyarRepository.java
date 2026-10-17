package com.makinistatama.parametre;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

interface UygulamaAyarRepository extends JpaRepository<UygulamaAyar, String> {

    List<UygulamaAyar> findAllByOrderByGrupAscSiraAsc();
}
