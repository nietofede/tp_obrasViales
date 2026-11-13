package com.obrasviales.ms_recursos.repository;

import com.obrasviales.ms_recursos.entity.Reserva;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface ReservaRepository extends JpaRepository<Reserva, Long> {

    // Idempotencia: evita duplicar reservas si Operaciones reintenta la petición
    Optional<Reserva> findByOrdenTrabajoId(Long ordenTrabajoId);

    // RN-05: Verifica si la cuadrilla ya tiene una reserva activa (confirmada o provisoria no vencida) en la fecha
    @Query("""
        SELECT COUNT(r) > 0 FROM Reserva r
        WHERE r.cuadrilla.id = :cuadrillaId
          AND r.fechaReserva = :fecha
          AND (r.estado = 'CONFIRMADA' OR (r.estado = 'PROVISORIA' AND r.fechaVencimientoTtl > :ahora))
    """)
    boolean existeReservaSolapada(
            @Param("cuadrillaId") Long cuadrillaId,
            @Param("fecha") LocalDate fecha,
            @Param("ahora") LocalDateTime ahora);

    List<Reserva> findByCuadrillaIdAndFechaReserva(Long cuadrillaId, LocalDate fecha);

    @Modifying
    @Query("""
        UPDATE Reserva r SET r.estado = 'EXPIRADA'
        WHERE r.estado = 'PROVISORIA' AND r.fechaVencimientoTtl <= :ahora
    """)
    int expirarReservasVencidas(@Param("ahora") LocalDateTime ahora);

    // Asignaciones vigentes de una cuadrilla desde una fecha: las que bloquean recursos (misma condición que RN-05).
    @Query("""
        SELECT r FROM Reserva r
        WHERE r.cuadrilla.id = :cuadrillaId
          AND r.fechaReserva >= :desde
          AND (r.estado = 'CONFIRMADA' OR (r.estado = 'PROVISORIA' AND r.fechaVencimientoTtl > :ahora))
        ORDER BY r.fechaReserva
    """)
    List<Reserva> asignacionesVigentes(
            @Param("cuadrillaId") Long cuadrillaId,
            @Param("desde") LocalDate desde,
            @Param("ahora") LocalDateTime ahora);
}
