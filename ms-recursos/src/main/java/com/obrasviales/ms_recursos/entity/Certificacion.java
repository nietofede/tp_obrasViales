package com.obrasviales.ms_recursos.entity;

import jakarta.persistence.*;
import java.time.LocalDate;

@Entity
@Table(name = "certificaciones")
public class Certificacion {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "operario_id", nullable = false)
    private Operario operario;

    @Column(name = "tipo_maquina", nullable = false, length = 100)
    private String tipoMaquina;

    @Column(nullable = false)
    private LocalDate vencimiento;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Operario getOperario() { return operario; }
    public void setOperario(Operario operario) { this.operario = operario; }

    public String getTipoMaquina() { return tipoMaquina; }
    public void setTipoMaquina(String tipoMaquina) { this.tipoMaquina = tipoMaquina; }

    public LocalDate getVencimiento() { return vencimiento; }
    public void setVencimiento(LocalDate vencimiento) { this.vencimiento = vencimiento; }
}
