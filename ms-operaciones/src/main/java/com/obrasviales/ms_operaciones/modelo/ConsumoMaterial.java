package com.obrasviales.ms_operaciones.modelo;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

// consumo real de un material al cerrar la orden. Vive dentro del cierre en la db.
@Embeddable
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ConsumoMaterial {

    @Column(name = "material", nullable = false, length = 100)
    private String material;

    @Column(name = "cantidad", nullable = false, precision = 14, scale = 2)
    private BigDecimal cantidad;

    @Column(name = "unidad", nullable = false, length = 20)
    private String unidad;
}
