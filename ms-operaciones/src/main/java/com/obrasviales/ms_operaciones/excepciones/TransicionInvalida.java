package com.obrasviales.ms_operaciones.excepciones;

import com.obrasviales.commons.exception.ReglaNegocioException;

public class TransicionInvalida extends ReglaNegocioException {
    public TransicionInvalida(String mensaje) {
        super(mensaje, "RN-11");
    }
}