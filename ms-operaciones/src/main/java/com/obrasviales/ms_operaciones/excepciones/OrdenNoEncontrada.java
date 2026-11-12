package com.obrasviales.ms_operaciones.excepciones;

public class OrdenNoEncontrada extends RuntimeException {
    public OrdenNoEncontrada(Long id) {
        super("No se encontró la orden de trabajo con ID: " + id);
    }
}