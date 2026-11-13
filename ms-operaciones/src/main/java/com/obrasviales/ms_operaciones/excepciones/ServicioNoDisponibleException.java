package com.obrasviales.ms_operaciones.excepciones;

// Se lanza cuando ms-obras o ms-recursos no andan o devuelven error 
public class ServicioNoDisponibleException extends RuntimeException {
    public ServicioNoDisponibleException(String mensaje) {
        super(mensaje);
    }
}
