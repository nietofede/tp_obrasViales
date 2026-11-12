package com.obrasviales.ms_operaciones.modelo;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

// Una sola fila de cuantas suspensiones llevan una fase a estar en riesgo 
@Entity
@Table(name = "configuracion_suspensiones")
@Getter
@Setter
@NoArgsConstructor
public class ConfiguracionSuspensiones {

    public static final Long ID_UNICO = 1L;

    @Id
    private Long id = ID_UNICO;

    @Column(name = "limite_suspensiones", nullable = false)
    private Integer limiteSuspensiones;
}
