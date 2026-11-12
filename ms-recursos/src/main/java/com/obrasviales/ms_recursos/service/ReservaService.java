package com.obrasviales.ms_recursos.service;

import com.obrasviales.ms_recursos.client.ProveedorRutas;
import com.obrasviales.ms_recursos.entity.*;
import com.obrasviales.ms_recursos.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

@Service
public class ReservaService {

    private final ReservaRepository reservaRepository;
    private final CuadrillaRepository cuadrillaRepository;
    private final OperarioRepository operarioRepository;
    private final MaquinaRepository maquinaRepository;
    private final CertificacionRepository certificacionRepository;
    private final ComposicionMinimaRepository composicionMinimaRepository;
    private final ConfiguracionDesplazamientoRepository configRepository;
    private final PlantaMezclaRepository plantaRepository;
    private final MantenimientoRepository mantenimientoRepository;
    private final ProveedorRutas proveedorRutas;

    public ReservaService(ReservaRepository reservaRepository,
                          CuadrillaRepository cuadrillaRepository,
                          OperarioRepository operarioRepository,
                          MaquinaRepository maquinaRepository,
                          CertificacionRepository certificacionRepository,
                          ComposicionMinimaRepository composicionMinimaRepository,
                          ConfiguracionDesplazamientoRepository configRepository,
                          PlantaMezclaRepository plantaRepository,
                          MantenimientoRepository mantenimientoRepository,
                          ProveedorRutas proveedorRutas) {
        this.reservaRepository = reservaRepository;
        this.cuadrillaRepository = cuadrillaRepository;
        this.operarioRepository = operarioRepository;
        this.maquinaRepository = maquinaRepository;
        this.certificacionRepository = certificacionRepository;
        this.composicionMinimaRepository = composicionMinimaRepository;
        this.configRepository = configRepository;
        this.plantaRepository = plantaRepository;
        this.mantenimientoRepository = mantenimientoRepository;
        this.proveedorRutas = proveedorRutas;
    }

    @Transactional
    public Reserva proponerYReservar(Long ordenTrabajoId, String tipoTrabajoStr, LocalDate fecha,
                                     double tramoLat, double tramoLon) {
        Optional<Reserva> existente = reservaRepository.findByOrdenTrabajoId(ordenTrabajoId);
        if (existente.isPresent()) {
            return existente.get();
        }

        LocalDateTime ahora = LocalDateTime.now();

        ConfiguracionDesplazamiento config = configRepository.findById(1L)
                .orElseThrow(() -> new IllegalStateException("Configuración de desplazamiento no encontrada"));
        ComposicionMinimaTrabajo requisitos = composicionMinimaRepository.findById(tipoTrabajoStr)
                .orElseThrow(() -> new IllegalArgumentException("Tipo de trabajo no configurado: " + tipoTrabajoStr));
        
        List<Cuadrilla> cuadrillas = cuadrillaRepository.findAll();
        Cuadrilla cuadrillaElegida = null;
        ProveedorRutas.ResultadoRuta rutaCuadrilla = null;

        for (Cuadrilla c : cuadrillas) {
            // RN-05: Verificar que la cuadrilla no tenga otra reserva activa en esa fecha
            if (reservaRepository.existeReservaSolapada(c.getId(), fecha, ahora)) {
                continue;
            }
            // RN-06: Verificar cantidad mínima de operarios activos
            List<Operario> operarios = operarioRepository.findByCuadrillaIdAndEstado(c.getId(), "ACTIVO");
            if (operarios.size() < requisitos.getMinOperarios()) {
                continue;
            }

            // RN-05 y RN-06: Verificar máquinas disponibles sin mantenimiento programado en esa fecha
            if (requisitos.getTipoMaquinaRequerida() != null) {
                List<Maquina> maquinasDisponibles = maquinaRepository.findByCuadrillaIdAndEstado(c.getId(), "DISPONIBLE")
                        .stream()
                        .filter(m -> m.getTipo().equalsIgnoreCase(requisitos.getTipoMaquinaRequerida()))
                        .filter(m -> !mantenimientoRepository.tieneMantenimientoEnFecha(m.getId(), fecha))
                        .toList();

                if (maquinasDisponibles.size() < requisitos.getCantidadMaquinas()) {
                    continue;
                }

                // RN-04: Al menos un operario debe tener certificación vigente en la fecha para esa máquina
                boolean tieneCertificado = operarios.stream().anyMatch(op ->
                        certificacionRepository.existsByOperarioIdAndTipoMaquinaAndVencimientoGreaterThanEqual(
                                op.getId(), requisitos.getTipoMaquinaRequerida(), fecha
                        )
                );
                if (!tieneCertificado) {
                    continue;
                }
            }

            // RN-07: Calcular viaje de la cuadrilla al tramo y validar límite de viaje y jornada
            ProveedorRutas.ResultadoRuta ruta = proveedorRutas.calcularRuta(
                    c.getUltimaLatitud(), c.getUltimaLongitud(), tramoLat, tramoLon
            );

            if (ruta.duracionMinutos() <= config.getViajeMaximoCuadrillaMin()
                    && (ruta.duracionMinutos() * 2) < config.getDuracionJornadaMin()) {
                cuadrillaElegida = c;
                rutaCuadrilla = ruta;
                break;
            }
        }

        if (cuadrillaElegida == null) {
            throw new IllegalStateException("RECHAZADA: Ninguna cuadrilla cumple disponibilidad, composición mínima, certificaciones o tiempo máximo de viaje.");
        }

        // 4. RN-08: Si el trabajo requiere mezcla, buscar planta dentro de la ventana térmica y calcular salida del camión
        PlantaMezcla plantaElegida = null;
        ProveedorRutas.ResultadoRuta rutaMezcla = null;
        LocalTime horaLlegadaCuadrilla = LocalTime.of(8, 0).plusMinutes(rutaCuadrilla.duracionMinutos());
        LocalTime horaSalidaCamion = null;

        if (Boolean.TRUE.equals(requisitos.getRequiereMezcla())) {
            List<PlantaMezcla> plantas = plantaRepository.findByActivaTrue();
            for (PlantaMezcla p : plantas) {
                ProveedorRutas.ResultadoRuta candidata = proveedorRutas.calcularRuta(
                        p.getLatitud(), p.getLongitud(), tramoLat, tramoLon
                );
                if (candidata.duracionMinutos() <= config.getVentanaTransporteMezclaMin()) {
                    plantaElegida = p;
                    rutaMezcla = candidata;
                    horaSalidaCamion = horaLlegadaCuadrilla
                            .minusMinutes(candidata.duracionMinutos() + config.getMargenCamionMin());
                    break;
                }
            }
            if (plantaElegida == null) {
                throw new IllegalStateException("RECHAZADA (RN-08): Ninguna planta activa cumple la ventana térmica de transporte de mezcla.");
            }
        }

        // 5. Crear y guardar la reserva PROVISORIA con vencimiento (TTL de 30 minutos)
        Reserva reserva = new Reserva();
        reserva.setOrdenTrabajoId(ordenTrabajoId);
        reserva.setCuadrilla(cuadrillaElegida);
        reserva.setPlanta(plantaElegida);
        reserva.setFechaReserva(fecha);
        reserva.setEstado("PROVISORIA");
        reserva.setFechaVencimientoTtl(ahora.plusMinutes(30));
        reserva.setTiempoViajeCuadrillaMin(rutaCuadrilla.duracionMinutos());
        reserva.setHoraLlegadaCuadrilla(horaLlegadaCuadrilla);
        reserva.setFuenteRuta(rutaCuadrilla.fuente());

        if (plantaElegida != null && rutaMezcla != null) {
            reserva.setTiempoViajeMezclaMin(rutaMezcla.duracionMinutos());
            reserva.setHoraSalidaCamion(horaSalidaCamion);
        }
        return reservaRepository.save(reserva);
    }

    @Transactional
    public Reserva confirmarReserva(Long id) {
        Reserva r = reservaRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Reserva no encontrada: " + id));
        if ("PROVISORIA".equals(r.getEstado()) && r.getFechaVencimientoTtl().isBefore(LocalDateTime.now())) {
            r.setEstado("EXPIRADA");
            reservaRepository.save(r);
            throw new IllegalStateException("La reserva provisoria ya expiró por TTL");
        }
        r.setEstado("CONFIRMADA");
        return reservaRepository.save(r);
    }

    @Transactional
    public Reserva liberarReserva(Long id) {
        Reserva r = reservaRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Reserva no encontrada: " + id));
        r.setEstado("LIBERADA");
        return reservaRepository.save(r);
    }
}
