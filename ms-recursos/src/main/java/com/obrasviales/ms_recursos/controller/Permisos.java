package com.obrasviales.ms_recursos.controller;

import com.obrasviales.commons.security.Roles;

// Expresiones de @PreAuthorize en un solo lugar, para que la matriz de permisos se lea (y se cambie) de un vistazo.
final class Permisos {

    private Permisos() {
    }

    static final String SOLO_CAPATAZ = "hasRole('" + Roles.CAPATAZ + "')";

    // Consulta de recursos: quienes administran u operan el sistema, más el auditor (solo lectura).
    static final String LECTURA = "hasAnyRole('" + Roles.ADMIN + "', '" + Roles.PLANIFICADOR + "', '"
            + Roles.CAPATAZ + "', '" + Roles.AUDITOR + "')";
}
