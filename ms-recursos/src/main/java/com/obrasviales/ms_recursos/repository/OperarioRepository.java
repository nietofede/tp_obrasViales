package com.obrasviales.ms_recursos.repository;

import com.obrasviales.ms_recursos.entity.Operario;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface OperarioRepository extends JpaRepository<Operario, Long> {
    Optional<Operario> findByKeycloakUsername(String keycloakUsername);
    List<Operario> findByCuadrillaIdAndEstado(Long cuadrillaId, String estado);
}
