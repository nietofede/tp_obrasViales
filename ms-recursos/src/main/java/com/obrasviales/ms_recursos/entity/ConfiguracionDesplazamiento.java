package com.obrasviales.ms_recursos.entity;

import jakarta.persistence.*;

@Entity 
@Table (name = "configuracion_desplazamiento")
public class ConfiguracionDesplazamiento {
    
    @Id
    private Long id = 1L;

    @Column(name = "viaje_maximo_cuadrilla_min", nullable = false)
    private Integer viajeMaximoCuadrillaMin = 90;

    @Column(name = "duracion_jornada_min", nullable = false)
    private Integer duracionJornadaMin = 480;

    @Column(name = "ventana_transporte_mezcla_min", nullable = false)
    private Integer ventanaTransporteMezclaMin = 60;

    @Column(name = "margen_camion_min", nullable = false)
    private Integer margenCamionMin = 15;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Integer getViajeMaximoCuadrillaMin() { return viajeMaximoCuadrillaMin; }
    public void setViajeMaximoCuadrillaMin(Integer viajeMaximoCuadrillaMin) { this.viajeMaximoCuadrillaMin = viajeMaximoCuadrillaMin; }

    public Integer getDuracionJornadaMin() { return duracionJornadaMin; }
    public void setDuracionJornadaMin(Integer duracionJornadaMin) { this.duracionJornadaMin = duracionJornadaMin; }

    public Integer getVentanaTransporteMezclaMin() { return ventanaTransporteMezclaMin; }
    public void setVentanaTransporteMezclaMin(Integer ventanaTransporteMezclaMin) { this.ventanaTransporteMezclaMin = ventanaTransporteMezclaMin; }

    public Integer getMargenCamionMin() { return margenCamionMin; }
    public void setMargenCamionMin(Integer margenCamionMin) { this.margenCamionMin = margenCamionMin; }
}
