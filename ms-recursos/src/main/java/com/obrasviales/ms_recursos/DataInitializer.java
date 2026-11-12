package com.obrasviales.ms_recursos;

import com.obrasviales.ms_recursos.entity.*;
import com.obrasviales.ms_recursos.repository.*;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.LocalDate;

@Configuration
public class DataInitializer {

    @Bean
    CommandLineRunner initDatabase(
            ConfiguracionDesplazamientoRepository configRepo,
            ComposicionMinimaRepository compRepo,
            CuadrillaRepository cuadrillaRepo,
            OperarioRepository operarioRepo,
            MaquinaRepository maquinaRepo,
            CertificacionRepository certRepo,
            PlantaMezclaRepository plantaRepo) {
        return args -> {
            if (configRepo.count() > 0) {
                return; // Ya hay datos cargados
            }

            // 1. Configuración logística (RN-07 y RN-08)
            ConfiguracionDesplazamiento config = new ConfiguracionDesplazamiento();
            config.setId(1L);
            config.setViajeMaximoCuadrillaMin(120);
            config.setDuracionJornadaMin(480);
            config.setVentanaTransporteMezclaMin(90);
            config.setMargenCamionMin(15);
            configRepo.save(config);

            // 2. Requisitos para PAVIMENTACION (RN-06 y RN-08)
            ComposicionMinimaTrabajo comp = new ComposicionMinimaTrabajo();
            comp.setTipoTrabajo("PAVIMENTACION");
            comp.setMinOperarios(2);
            comp.setTipoMaquinaRequerida("PAVIMENTADORA");
            comp.setCantidadMaquinas(1);
            comp.setRequiereMezcla(true);
            compRepo.save(comp);

            // 3. Cuadrilla ubicada en Córdoba Capital
            Cuadrilla c1 = new Cuadrilla();
            c1.setNombre("Cuadrilla Alfa");
            c1.setUltimaLatitud(-31.4167);
            c1.setUltimaLongitud(-64.1833);
            c1 = cuadrillaRepo.save(c1);

            // 4. Dos operarios activos en la cuadrilla (RN-06)
            Operario op1 = new Operario();
            op1.setNombre("Juan Pérez");
            op1.setEstado("ACTIVO");
            op1.setCuadrilla(c1);
            op1 = operarioRepo.save(op1);

            Operario op2 = new Operario();
            op2.setNombre("Carlos Gómez");
            op2.setEstado("ACTIVO");
            op2.setCuadrilla(c1);
            operarioRepo.save(op2);

            // 5. Certificación vigente para op1 (RN-04)
            Certificacion cert = new Certificacion();
            cert.setOperario(op1);
            cert.setTipoMaquina("PAVIMENTADORA");
            cert.setVencimiento(LocalDate.of(2027, 12, 31));
            certRepo.save(cert);

            // 6. Máquina disponible en la cuadrilla (RN-05 y RN-06)
            Maquina maq = new Maquina();
            maq.setCodigo("MAQ-01");
            maq.setTipo("PAVIMENTADORA");
            maq.setEstado("DISPONIBLE");
            maq.setCuadrilla(c1);
            maquinaRepo.save(maq);

            // 7. Planta de mezcla activa cerca de Córdoba (RN-08)
            PlantaMezcla planta = new PlantaMezcla();
            planta.setNombre("Planta Central");
            planta.setLatitud(-31.3989);
            planta.setLongitud(-64.1821);
            planta.setActiva(true);
            plantaRepo.save(planta);

            System.out.println(">>> Datos de prueba inicializados correctamente en ms-recursos <<<");
        };
    }
}