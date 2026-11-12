package com.obrasviales.ms_recursos.client;

public interface ProveedorRutas {

    record ResultadoRuta(int duracionMinutos, double distanciaKm, String fuente) {}

    ResultadoRuta calcularRuta(double origenLat, double origenLon, double destinoLat, double destinoLon);
}
