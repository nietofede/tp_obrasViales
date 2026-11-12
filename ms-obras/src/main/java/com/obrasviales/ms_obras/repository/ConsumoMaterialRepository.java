package com.obrasviales.ms_obras.repository;

import com.obrasviales.ms_obras.domain.ConsumoMaterial;
import com.obrasviales.ms_obras.domain.TipoFase;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ConsumoMaterialRepository extends JpaRepository<ConsumoMaterial, Long> {

    List<ConsumoMaterial> findByTipoFase(TipoFase tipoFase);
}