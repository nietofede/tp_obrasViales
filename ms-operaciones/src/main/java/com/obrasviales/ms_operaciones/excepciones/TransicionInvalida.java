package com.obrasviales.ms_operaciones.excepciones;

public class TransicionInvalida extends RuntimeException {
    public TransicionInvalida(String mensaje) {
        super(mensaje);
    }
}