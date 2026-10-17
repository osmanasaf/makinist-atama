package com.makinistatama.parametre;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

interface KuralParametreRepository extends JpaRepository<KuralParametre, String> {

    List<KuralParametre> findAllByOrderByAnahtar();
}
