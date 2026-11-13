package com.obrasviales.commons.dto;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

// Value object compartido: valida los rangos y arma el par para OSRM (longitud,latitud).
class CoordenadaTest {

    @Test
    void aceptaLosLimitesDelRango() {
        Coordenada polo = new Coordenada(-90.0, 180.0);

        assertThat(polo.latitud()).isEqualTo(-90.0);
        assertThat(polo.longitud()).isEqualTo(180.0);
    }

    @Test
    void rechazaUnaLatitudFueraDeRango() {
        assertThatThrownBy(() -> new Coordenada(90.5, -64.0)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rechazaUnaLongitudFueraDeRango() {
        assertThatThrownBy(() -> new Coordenada(-31.0, -181.0)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rechazaValoresNulos() {
        assertThatThrownBy(() -> new Coordenada(null, -64.0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Coordenada(-31.0, null)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void paraOsrmVaPrimeroLaLongitud() {
        assertThat(new Coordenada(-31.42, -64.18).toOsrmString()).isEqualTo("-64.18,-31.42");
    }
}
