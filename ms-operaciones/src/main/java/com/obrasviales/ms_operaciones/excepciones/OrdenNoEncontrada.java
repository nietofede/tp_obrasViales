package com.obrasviales.ms_operaciones.excepciones;

import com.obrasviales.commons.exception.RecursoNoEncontradoException;

public class OrdenNoEncontrada extends RecursoNoEncontradoException {
    public OrdenNoEncontrada(Long id) {
        super("No se encontró la orden de trabajo con ID: " + id);
    }
}