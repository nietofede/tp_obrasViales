package com.obrasviales.ms_recursos.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table (name = "cuadrillas")

public class Cuadrilla {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 100)
    private String nombre;

    @Column(name = "ultima_latitud", nullable = false)
    private Double ultimaLatitud;

    @Column(name = "ultima_longitud", nullable = false)
    private Double ultimaLongitud;

    @Column(name = "fecha_checkin", nullable = false)
    private LocalDateTime fechaCheckin = LocalDateTime.now();

    @OneToMany(mappedBy = "cuadrilla", fetch = FetchType.LAZY)
    private List<Operario> operarios = new ArrayList<>();

    @OneToMany(mappedBy = "cuadrilla", fetch = FetchType.LAZY)
    private List<Maquina> maquinas = new ArrayList<>();

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getNombre() { return nombre; }
    public void setNombre(String nombre) { this.nombre = nombre; }

    public Double getUltimaLatitud() { return ultimaLatitud; }
    public void setUltimaLatitud(Double ultimaLatitud) { this.ultimaLatitud = ultimaLatitud; }

    public Double getUltimaLongitud() { return ultimaLongitud; }
    public void setUltimaLongitud(Double ultimaLongitud) { this.ultimaLongitud = ultimaLongitud; }

    public LocalDateTime getFechaCheckin() { return fechaCheckin; }
    public void setFechaCheckin(LocalDateTime fechaCheckin) { this.fechaCheckin = fechaCheckin; }

    public List<Operario> getOperarios() { return operarios; }
    public void setOperarios(List<Operario> operarios) { this.operarios = operarios; }

    public List<Maquina> getMaquinas() { return maquinas; }
    public void setMaquinas(List<Maquina> maquinas) { this.maquinas = maquinas; }
    
}
