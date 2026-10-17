package com.makinistatama.parametre;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

interface KuralSiddetRepository extends JpaRepository<KuralSiddet, String> {

    List<KuralSiddet> findAllByOrderByKod();
}
