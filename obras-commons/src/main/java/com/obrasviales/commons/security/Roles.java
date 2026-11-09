package main.java.com.obrasviales.commons.security;

public final class Roles {
    private Roles() {
        // constructor privado vacio para evitar que se instancie
    }

    public static final String ADMIN = "ADMIN";
    public static final String PLANIFICADOR = "PLANIFICADOR";
    public static final String CAPATAZ = "CAPATAZ";
    public static final String OPERARIO = "OPERARIO";
    public static final String AUDITOR = "AUDITOR";
    public static final String SISTEMA = "SISTEMA";
}