package com.obrasviales.ms_recursos.entity;

import jakarta.persistence.*;
import java.time.LocalDate;

@Entity 
@Table(name = "mantenimientos")
public class Mantenimiento {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "maquina_id", nullable = false)
    private Maquina maquina;

    @Column(name = "fecha_inicio", nullable = false)
    private LocalDate fechaInicio;

    @Column(name = "fecha_fin", nullable = false)
    private LocalDate fechaFin;

    @Column(nullable = false, length = 255)
    private String motivo;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Maquina getMaquina() { return maquina; }
    public void setMaquina(Maquina maquina) { this.maquina = maquina; }

    public LocalDate getFechaInicio() { return fechaInicio; }
    public void setFechaInicio(LocalDate fechaInicio) { this.fechaInicio = fechaInicio; }

    public LocalDate getFechaFin() { return fechaFin; }
    public void setFechaFin(LocalDate fechaFin) { this.fechaFin = fechaFin; }

    public String getMotivo() { return motivo; }
    public void setMotivo(String motivo) { this.motivo = motivo; }
}
