package com.obrasviales.commons.exception;

public class ReglaNegocioException extends RuntimeException {
    private String codigoRegla;

    public ReglaNegocioException(String mensaje, String codigoRegla) {
        super(mensaje);
        this.codigoRegla = codigoRegla;
    }

    public String getCodigoRegla() {
        return codigoRegla;
    }
    
}
