package com.makinistatama.tanim;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

interface GarIntikalSureRepository extends JpaRepository<GarIntikalSuresi, GarIntikalSuresi.Anahtar> {

    List<GarIntikalSuresi> findAllByOrderByGarAIdAscGarBIdAsc();
}
