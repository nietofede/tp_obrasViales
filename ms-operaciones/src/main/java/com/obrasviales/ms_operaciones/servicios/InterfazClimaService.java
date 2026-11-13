package com.obrasviales.ms_operaciones.servicios;

import com.obrasviales.ms_operaciones.dtos.DatosClimaDTO;

import java.time.LocalDate;

public interface InterfazClimaService {
    // Condiciones de la ventana de ejecucion de una fecha en una ubicacion: el peor valor de cada variable (RN-09).
    // Cumple el papel de la interfaz ProveedorClima de la propuesta: el resto del servicio no conoce a Open-Meteo.
    DatosClimaDTO obtenerCondicionesClimaticas(Double latitud, Double longitud, LocalDate fecha);
}