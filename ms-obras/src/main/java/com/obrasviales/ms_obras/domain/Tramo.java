package com.obrasviales.ms_obras.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

@Entity
@Table(name = "tramos")
@Getter @Setter @NoArgsConstructor
public class Tramo {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "obra_id")
    private Obra obra;

    @Column(nullable = false)
    private String nombre;

    @Column(name = "superficie_m2", nullable = false, precision = 12, scale = 2)
    private BigDecimal superficieM2;

    @Column(nullable = false)
    private Double latitud;

    @Column(nullable = false)
    private Double longitud;
}