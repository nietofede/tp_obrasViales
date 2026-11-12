package com.obrasviales.ms_recursos.entity;

import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

@Entity 
@Table (name = "reservas")
public class Reserva {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "cuadrilla_id", nullable = false)
    private Cuadrilla cuadrilla;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "planta_id")
    private PlantaMezcla planta;

    @Column(name = "orden_trabajo_id", nullable = false, unique = true)
    private Long ordenTrabajoId;

    @Column(name = "fecha_reserva", nullable = false)
    private LocalDate fechaReserva;

    @Column(name = "fecha_vencimiento_ttl", nullable = false)
    private LocalDateTime fechaVencimientoTtl;

    @Column(nullable = false, length = 50)
    private String estado = "PROVISORIA";

    @Column(name = "hora_llegada_cuadrilla")
    private LocalTime horaLlegadaCuadrilla;

    @Column(name = "hora_salida_camion")
    private LocalTime horaSalidaCamion;

    @Column(name = "tiempo_viaje_cuadrilla_min")
    private Integer tiempoViajeCuadrillaMin;

    @Column(name = "tiempo_viaje_mezcla_min")
    private Integer tiempoViajeMezclaMin;

    @Column(name = "fuente_ruta", nullable = false, length = 20)
    private String fuenteRuta = "OSRM";

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Cuadrilla getCuadrilla() { return cuadrilla; }
    public void setCuadrilla(Cuadrilla cuadrilla) { this.cuadrilla = cuadrilla; }

    public PlantaMezcla getPlanta() { return planta; }
    public void setPlanta(PlantaMezcla planta) { this.planta = planta; }

    public Long getOrdenTrabajoId() { return ordenTrabajoId; }
    public void setOrdenTrabajoId(Long ordenTrabajoId) { this.ordenTrabajoId = ordenTrabajoId; }

    public LocalDate getFechaReserva() { return fechaReserva; }
    public void setFechaReserva(LocalDate fechaReserva) { this.fechaReserva = fechaReserva; }

    public LocalDateTime getFechaVencimientoTtl() { return fechaVencimientoTtl; }
    public void setFechaVencimientoTtl(LocalDateTime fechaVencimientoTtl) { this.fechaVencimientoTtl = fechaVencimientoTtl; }

    public String getEstado() { return estado; }
    public void setEstado(String estado) { this.estado = estado; }

    public LocalTime getHoraLlegadaCuadrilla() { return horaLlegadaCuadrilla; }
    public void setHoraLlegadaCuadrilla(LocalTime horaLlegadaCuadrilla) { this.horaLlegadaCuadrilla = horaLlegadaCuadrilla; }

    public LocalTime getHoraSalidaCamion() { return horaSalidaCamion; }
    public void setHoraSalidaCamion(LocalTime horaSalidaCamion) { this.horaSalidaCamion = horaSalidaCamion; }

    public Integer getTiempoViajeCuadrillaMin() { return tiempoViajeCuadrillaMin; }
    public void setTiempoViajeCuadrillaMin(Integer tiempoViajeCuadrillaMin) { this.tiempoViajeCuadrillaMin = tiempoViajeCuadrillaMin; }

    public Integer getTiempoViajeMezclaMin() { return tiempoViajeMezclaMin; }
    public void setTiempoViajeMezclaMin(Integer tiempoViajeMezclaMin) { this.tiempoViajeMezclaMin = tiempoViajeMezclaMin; }

    public String getFuenteRuta() { return fuenteRuta; }
    public void setFuenteRuta(String fuenteRuta) { this.fuenteRuta = fuenteRuta; }
}
