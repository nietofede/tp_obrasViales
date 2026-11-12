package com.obrasviales.ms_recursos.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "composicion_minima_trabajo")
public class ComposicionMinimaTrabajo {
    
    @Id
    @Column(name = "tipo_trabajo", length = 100)
    private String tipoTrabajo;

    @Column(name = "min_operarios", nullable = false)
    private Integer minOperarios;

    @Column(name = "tipo_maquina_requerida", length = 100)
    private String tipoMaquinaRequerida;

    @Column(name = "cantidad_maquinas", nullable = false)
    private Integer cantidadMaquinas = 1;

    @Column(name = "requiere_mezcla", nullable = false)
    private Boolean requiereMezcla = false;

    public String getTipoTrabajo() { return tipoTrabajo; }
    public void setTipoTrabajo(String tipoTrabajo) { this.tipoTrabajo = tipoTrabajo; }

    public Integer getMinOperarios() { return minOperarios; }
    public void setMinOperarios(Integer minOperarios) { this.minOperarios = minOperarios; }

    public String getTipoMaquinaRequerida() { return tipoMaquinaRequerida; }
    public void setTipoMaquinaRequerida(String tipoMaquinaRequerida) { this.tipoMaquinaRequerida = tipoMaquinaRequerida; }

    public Integer getCantidadMaquinas() { return cantidadMaquinas; }
    public void setCantidadMaquinas(Integer cantidadMaquinas) { this.cantidadMaquinas = cantidadMaquinas; }

    public Boolean getRequiereMezcla() { return requiereMezcla; }
    public void setRequiereMezcla(Boolean requiereMezcla) { this.requiereMezcla = requiereMezcla; }
}
