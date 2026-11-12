package com.obrasviales.ms_recursos.repository;

import com.obrasviales.ms_recursos.entity.PlantaMezcla;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface PlantaMezclaRepository extends JpaRepository<PlantaMezcla, Long> {
    List<PlantaMezcla> findByActivaTrue();
}
