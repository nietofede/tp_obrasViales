package com.obrasviales.ms_recursos.controller;

import com.obrasviales.commons.security.Roles;

// Expresiones de @PreAuthorize en un solo lugar, para que la matriz de permisos se lea (y se cambie) de un vistazo.
final class Permisos {

    private Permisos() {
    }

    static final String SOLO_ADMIN = "hasRole('" + Roles.ADMIN + "')";
    static final String SOLO_PLANIFICADOR = "hasRole('" + Roles.PLANIFICADOR + "')";
    static final String SOLO_CAPATAZ = "hasRole('" + Roles.CAPATAZ + "')";
    static final String SOLO_OPERARIO = "hasRole('" + Roles.OPERARIO + "')";

    static final String ADMIN_O_PLANIFICADOR =
            "hasAnyRole('" + Roles.ADMIN + "', '" + Roles.PLANIFICADOR + "')";

    // Consulta de catálogos: quienes administran u operan el sistema, más el auditor (solo lectura).
    static final String LECTURA = "hasAnyRole('" + Roles.ADMIN + "', '" + Roles.PLANIFICADOR + "', '"
            + Roles.CAPATAZ + "', '" + Roles.AUDITOR + "')";

    // Consulta de una reserva: lo mismo que la lectura, más la cuenta de servicio que la creó.
    static final String LECTURA_O_SISTEMA = "hasAnyRole('" + Roles.PLANIFICADOR + "', '" + Roles.CAPATAZ + "', '"
            + Roles.AUDITOR + "', '" + Roles.SISTEMA + "')";
}
