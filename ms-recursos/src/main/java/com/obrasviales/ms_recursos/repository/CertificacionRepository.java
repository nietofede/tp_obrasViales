package com.obrasviales.ms_recursos.repository;

import com.obrasviales.ms_recursos.entity.Certificacion;
import org.springframework.data.jpa.repository.JpaRepository;
import java.time.LocalDate;
import java.util.List;

public interface CertificacionRepository extends JpaRepository<Certificacion, Long> {
    List<Certificacion> findByOperarioId(Long operarioId);

    boolean existsByOperarioIdAndTipoMaquinaAndVencimientoGreaterThanEqual(
            Long operarioId, String tipoMaquina, LocalDate fechaOrden);
}