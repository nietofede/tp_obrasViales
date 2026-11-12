package com.obrasviales.ms_recursos.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;

@Entity
@Table(name = "maquinas")
public class Maquina {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cuadrilla_id")
    private Cuadrilla cuadrilla;

    @Column(nullable = false, unique = true, length = 50)
    private String codigo;

    @Column(nullable = false, length = 100)
    private String tipo;

    @Column(nullable = false, length = 50)
    private String estado = "DISPONIBLE";

    @Column(name = "horas_uso", nullable = false)
    private Integer horasUso = 0;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Cuadrilla getCuadrilla() { return cuadrilla; }
    public void setCuadrilla(Cuadrilla cuadrilla) { this.cuadrilla = cuadrilla; }

    public String getCodigo() { return codigo; }
    public void setCodigo(String codigo) { this.codigo = codigo; }

    public String getTipo() { return tipo; }
    public void setTipo(String tipo) { this.tipo = tipo; }

    public String getEstado() { return estado; }
    public void setEstado(String estado) { this.estado = estado; }

    public Integer getHorasUso() { return horasUso; }
    public void setHorasUso(Integer horasUso) { this.horasUso = horasUso; }
}
