package com.obrasviales.ms_operaciones.servicios;

import com.obrasviales.ms_operaciones.dtos.DatosClimaDTO;

public interface InterfazClimaService {
    // pedimos las condiciones climaticas de una ubicacion geografica, obtenemos el dto    
    DatosClimaDTO obtenerCondicionesClimaticas(Double latitud, Double longitud);
}