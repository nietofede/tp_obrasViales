package com.obrasviales.ms_recursos.repository;

import com.obrasviales.ms_recursos.entity.Maquina;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface MaquinaRepository extends JpaRepository<Maquina, Long> {
    List<Maquina> findByCuadrillaIdAndEstado(Long cuadrillaId, String estado);
}
