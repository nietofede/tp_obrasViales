package com.obrasviales.ms_obras.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

@Entity
@Table(name = "consumos_material")
@Getter @Setter @NoArgsConstructor
public class ConsumoMaterial {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo_fase", nullable = false)
    private TipoFase tipoFase;

    @Column(nullable = false)
    private String material;

    @Column(name = "consumo_por_m2", nullable = false, precision = 12, scale = 4)
    private BigDecimal consumoPorM2;

    @Column(nullable = false)
    private String unidad;
}