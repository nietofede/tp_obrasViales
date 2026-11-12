package com.obrasviales.ms_recursos.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "plantas_mezcla")
public class PlantaMezcla {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 150)
    private String nombre;

    @Column(nullable = false)
    private Double latitud;

    @Column(nullable = false)
    private Double longitud;

    @Column(nullable = false)
    private Boolean activa = true;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getNombre() { return nombre; }
    public void setNombre(String nombre) { this.nombre = nombre; }

    public Double getLatitud() { return latitud; }
    public void setLatitud(Double latitud) { this.latitud = latitud; }

    public Double getLongitud() { return longitud; }
    public void setLongitud(Double longitud) { this.longitud = longitud; }

    public Boolean getActiva() { return activa; }
    public void setActiva(Boolean activa) { this.activa = activa; }
}
