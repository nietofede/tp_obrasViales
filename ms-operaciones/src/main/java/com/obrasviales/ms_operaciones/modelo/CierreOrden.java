package com.obrasviales.ms_operaciones.modelo;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

// para el cierre horas de maquina y consumo real de materiales, uno solo por orden
@Entity
@Table(name = "cierres_orden")
@Getter
@Setter
@NoArgsConstructor
public class CierreOrden {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "orden_trabajo_id", nullable = false, unique = true)
    private Long ordenTrabajoId;

    @Column(name = "horas_maquina", nullable = false, precision = 8, scale = 2)
    private BigDecimal horasMaquina;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "cierre_consumos", joinColumns = @JoinColumn(name = "cierre_id"))
    private List<ConsumoMaterial> consumos = new ArrayList<>();

    @Column(name = "cerrado", nullable = false, updatable = false)
    private LocalDateTime cerrado;

    @PrePersist
    protected void onCreate() {
        cerrado = LocalDateTime.now();
    }
}
