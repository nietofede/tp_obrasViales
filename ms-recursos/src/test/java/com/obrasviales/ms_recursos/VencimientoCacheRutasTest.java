package com.obrasviales.ms_recursos;

import com.obrasviales.ms_recursos.client.VencimientoCacheRutas;
import org.junit.jupiter.api.Test;
import org.springframework.cache.CacheManager;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

// La cache de rutas vence: al cumplirse el TTL se vacia y la proxima consulta vuelve a ir a OSRM.
class VencimientoCacheRutasTest {

    @Test
    void alVencerSeVaciaLaCacheDeRutas() {
        CacheManager cacheManager = new ConcurrentMapCacheManager("rutas");
        cacheManager.getCache("rutas").put("cordoba-tramo", "ruta guardada");

        new VencimientoCacheRutas(cacheManager).vaciar();

        assertThat(cacheManager.getCache("rutas").get("cordoba-tramo")).isNull();
    }

    @Test
    void sinCacheDeRutasNoFalla() {
        // con una lista fija de caches vacia, getCache("rutas") devuelve null
        ConcurrentMapCacheManager vacio = new ConcurrentMapCacheManager();
        vacio.setCacheNames(List.of());

        new VencimientoCacheRutas(vacio).vaciar();
    }
}
