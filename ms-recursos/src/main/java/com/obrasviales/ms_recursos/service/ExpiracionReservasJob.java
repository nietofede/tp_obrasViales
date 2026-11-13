package com.obrasviales.ms_recursos.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

// Tarea periódica que marca como EXPIRADA las reservas provisorias vencidas. Es idempotente: correrla dos veces
// seguidas no cambia nada la segunda vez. La frecuencia se puede ajustar con app.reservas.expiracion-ms.
@Component
public class ExpiracionReservasJob {

    private static final Logger log = LoggerFactory.getLogger(ExpiracionReservasJob.class);

    private final ReservaService reservaService;

    public ExpiracionReservasJob(ReservaService reservaService) {
        this.reservaService = reservaService;
    }

    @Scheduled(fixedDelayString = "${app.reservas.expiracion-ms:60000}")
    public void expirarVencidas() {
        try {
            int cantidad = reservaService.expirarVencidas();
            if (cantidad > 0) {
                log.info("Reservas provisorias pasadas a EXPIRADA por TTL vencido: {}", cantidad);
            }
        } catch (RuntimeException ex) {
            // un fallo puntual (por ejemplo la base caída) no debe frenar las próximas corridas
            log.error("No se pudieron expirar las reservas vencidas", ex);
        }
    }
}
