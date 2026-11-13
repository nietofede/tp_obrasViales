package com.obrasviales.ms_recursos.client;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

// Vencimiento de la cache de rutas de OSRM. La cache en memoria de Spring no tiene TTL por entrada, asi que se vacia
// entera cada app.osrm.cache-ttl (6 h por defecto): ninguna ruta guardada queda mas vieja que ese tiempo.
@Component
public class VencimientoCacheRutas {

    private static final Logger log = LoggerFactory.getLogger(VencimientoCacheRutas.class);

    private final CacheManager cacheManager;

    public VencimientoCacheRutas(CacheManager cacheManager) {
        this.cacheManager = cacheManager;
    }

    @Scheduled(fixedRateString = "${app.osrm.cache-ttl:PT6H}", initialDelayString = "${app.osrm.cache-ttl:PT6H}")
    public void vaciar() {
        Cache rutas = cacheManager.getCache("rutas");
        if (rutas != null) {
            rutas.clear();
            log.info("Cache de rutas de OSRM vaciada por vencimiento");
        }
    }
}
