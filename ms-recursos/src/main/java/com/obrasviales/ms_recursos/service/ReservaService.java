package com.obrasviales.ms_recursos.service;

import com.obrasviales.commons.exception.ConflictoEstadoException;
import com.obrasviales.commons.exception.RecursoNoEncontradoException;
import com.obrasviales.ms_recursos.client.ProveedorRutas;
import com.obrasviales.ms_recursos.entity.*;
import com.obrasviales.ms_recursos.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import com.obrasviales.commons.exception.ReglaNegocioException;
import java.util.ArrayList;
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

    // Orden en que se revisan las reglas de elegibilidad. Si ninguna cuadrilla sirve, se informa la regla de la
    // cuadrilla que llegó más lejos en esa secuencia (la que estuvo más cerca de ser elegible).
    private static final List<String> ORDEN_REGLAS = List.of("RN-05", "RN-06", "RN-04", "RN-07");

    // Resultado de evaluar una cuadrilla: o fue rechazada (regla y detalle) o es elegible (con su ruta al tramo).
    private record Evaluacion(String regla, String detalle, ProveedorRutas.ResultadoRuta ruta) {
        static Evaluacion rechazada(String regla, String detalle) {
            return new Evaluacion(regla, detalle, null);
        }

        static Evaluacion elegida(ProveedorRutas.ResultadoRuta ruta) {
            return new Evaluacion(null, null, ruta);
        }

        boolean esElegida() {
            return regla == null;
        }
    }

    @Transactional
    public Reserva proponerYReservar(Long ordenTrabajoId, String tipoTrabajoStr, LocalDate fecha,
                                     double tramoLat, double tramoLon) {

        LocalDateTime ahora = LocalDateTime.now();

        Optional<Reserva> existente = reservaRepository.findByOrdenTrabajoId(ordenTrabajoId);
        if (existente.isPresent() && bloqueaRecursos(existente.get(), ahora)) {
            return existente.get();
        }

        ConfiguracionDesplazamiento config = configRepository.findById(1L)
                .orElseThrow(() -> new IllegalStateException("Configuración de desplazamiento no encontrada"));
        ComposicionMinimaTrabajo requisitos = composicionMinimaRepository.findById(tipoTrabajoStr)
                .orElseThrow(() -> new ReglaNegocioException("Tipo de trabajo no configurado: " + tipoTrabajoStr, "RN-06"));
        
        Cuadrilla cuadrillaElegida = null;
        ProveedorRutas.ResultadoRuta rutaCuadrilla = null;
        List<String> motivos = new ArrayList<>();
        String reglaMasAvanzada = null;
        
        for (Cuadrilla c : cuadrillaRepository.findAll()) {
            // RN-05: Verificar que la cuadrilla no tenga otra reserva activa en esa fecha
            Evaluacion evaluacion = evaluarCuadrilla(c, requisitos, config, fecha, ahora, tramoLat, tramoLon);

            if (evaluacion.esElegida()) {
                cuadrillaElegida = c;
                rutaCuadrilla = evaluacion.ruta();
                break;
            }
            motivos.add(c.getNombre() + ": " + evaluacion.regla() + ", " + evaluacion.detalle());
            if (reglaMasAvanzada == null || ORDEN_REGLAS.indexOf(evaluacion.regla()) > ORDEN_REGLAS.indexOf(reglaMasAvanzada)) {
                reglaMasAvanzada = evaluacion.regla();
            }
            
        }

        if (cuadrillaElegida == null) {
            String detalle = motivos.isEmpty() ? "no hay cuadrillas cargadas" : String.join("; ", motivos);
            throw new ReglaNegocioException("Ninguna cuadrilla es elegible. " + detalle,
                    reglaMasAvanzada != null ? reglaMasAvanzada : "RN-06");
        }

        // 4. RN-08: Si el trabajo requiere mezcla, buscar planta dentro de la ventana térmica y calcular salida del camión
        PlantaMezcla plantaElegida = null;
        ProveedorRutas.ResultadoRuta rutaMezcla = null;
        LocalTime horaLlegadaCuadrilla = LocalTime.of(8, 0).plusMinutes(rutaCuadrilla.duracionMinutos());
        LocalTime horaSalidaCamion = null;

        if (Boolean.TRUE.equals(requisitos.getRequiereMezcla())) {
            List<String> motivosPlantas = new ArrayList<>();
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
                motivosPlantas.add(p.getNombre() + ": " + candidata.duracionMinutos() + " min de viaje");
            }
            if (plantaElegida == null) {
                String detalle = motivosPlantas.isEmpty() ? "no hay plantas activas" : String.join("; ", motivosPlantas);
                throw new ReglaNegocioException("Ninguna planta llega dentro de la ventana de transporte de "
                        + config.getVentanaTransporteMezclaMin() + " min. " + detalle, "RN-08");
            }
        }

        // 5. Crear y guardar la reserva PROVISORIA con vencimiento (TTL de 30 minutos)
        Reserva reserva = existente.orElseGet(Reserva::new);
        reserva.setOrdenTrabajoId(ordenTrabajoId);
        reserva.setCuadrilla(cuadrillaElegida);
        reserva.setPlanta(plantaElegida);
        reserva.setFechaReserva(fecha);
        reserva.setEstado("PROVISORIA");
        reserva.setFechaVencimientoTtl(ahora.plusMinutes(30));
        reserva.setTiempoViajeCuadrillaMin(rutaCuadrilla.duracionMinutos());
        reserva.setHoraLlegadaCuadrilla(horaLlegadaCuadrilla);
        reserva.setFuenteRuta(rutaCuadrilla.fuente());
        reserva.setTiempoViajeMezclaMin(null);
        reserva.setHoraSalidaCamion(null);

        if (plantaElegida != null && rutaMezcla != null) {
            reserva.setTiempoViajeMezclaMin(rutaMezcla.duracionMinutos());
            reserva.setHoraSalidaCamion(horaSalidaCamion);
        }
        return reservaRepository.save(reserva);
    }


    // Revisa las reglas de una cuadrilla en orden: RN-05, RN-06, RN-04 y RN-07. Devuelve la primera que incumple.
    private Evaluacion evaluarCuadrilla(Cuadrilla c, ComposicionMinimaTrabajo requisitos, ConfiguracionDesplazamiento config,
                                        LocalDate fecha, LocalDateTime ahora, double tramoLat, double tramoLon) {
        // RN-05: la cuadrilla no puede tener otra reserva vigente ese día
        if (reservaRepository.existeReservaSolapada(c.getId(), fecha, ahora)) {
            return Evaluacion.rechazada("RN-05", "ya tiene una reserva vigente para ese día");
        }

        // RN-06: cantidad mínima de operarios activos
        List<Operario> operarios = operarioRepository.findByCuadrillaIdAndEstado(c.getId(), "ACTIVO");
        if (operarios.size() < requisitos.getMinOperarios()) {
            return Evaluacion.rechazada("RN-06", "tiene " + operarios.size() + " operarios activos y se necesitan "
                    + requisitos.getMinOperarios());
        }

        String tipoMaquina = requisitos.getTipoMaquinaRequerida();
        if (tipoMaquina != null) {
            // RN-06: máquinas del tipo requerido, disponibles y sin mantenimiento ese día
            long maquinas = maquinaRepository.findByCuadrillaIdAndEstado(c.getId(), "DISPONIBLE")
                    .stream()
                    .filter(m -> m.getTipo().equalsIgnoreCase(tipoMaquina))
                    .filter(m -> !mantenimientoRepository.tieneMantenimientoEnFecha(m.getId(), fecha))
                    .count();
            if (maquinas < requisitos.getCantidadMaquinas()) {
                return Evaluacion.rechazada("RN-06", "tiene " + maquinas + " máquinas " + tipoMaquina
                        + " disponibles ese día y se necesitan " + requisitos.getCantidadMaquinas());
            }

            // RN-04: al menos un operario con certificación vigente ese día para esa máquina
            boolean tieneCertificado = operarios.stream().anyMatch(op ->
                    certificacionRepository.existsByOperarioIdAndTipoMaquinaAndVencimientoGreaterThanEqual(
                            op.getId(), tipoMaquina, fecha));
            if (!tieneCertificado) {
                return Evaluacion.rechazada("RN-04", "ningún operario tiene certificación vigente para " + tipoMaquina);
            }
        }

        // RN-07: el viaje al tramo no supera el máximo y la ida y vuelta entra en la jornada
        ProveedorRutas.ResultadoRuta ruta = proveedorRutas.calcularRuta(
                c.getUltimaLatitud(), c.getUltimaLongitud(), tramoLat, tramoLon);
        if (ruta.duracionMinutos() > config.getViajeMaximoCuadrillaMin()) {
            return Evaluacion.rechazada("RN-07", "el viaje al tramo es de " + ruta.duracionMinutos()
                    + " min y el máximo es " + config.getViajeMaximoCuadrillaMin() + " min");
        }
        if (ruta.duracionMinutos() * 2 >= config.getDuracionJornadaMin()) {
            return Evaluacion.rechazada("RN-07", "la ida y vuelta de " + (ruta.duracionMinutos() * 2)
                    + " min no entra en la jornada de " + config.getDuracionJornadaMin() + " min");
        }
        return Evaluacion.elegida(ruta);
    }

   
    // Sin noRollBack la transaccion se revierte al lanzar la excepcion y el estado EXPIRADA nunca queda guardado.
    @Transactional(noRollbackFor = ConflictoEstadoException.class)
    public Reserva confirmarReserva(Long id) {
        Reserva r = reservaRepository.findById(id)
                .orElseThrow(() -> new RecursoNoEncontradoException("Reserva no encontrada: " + id));

        if ("CONFIRMADA".equals(r.getEstado())) {
            return r; // idempotente: Operaciones puede reintentar
        }
        
        if (!"PROVISORIA".equals(r.getEstado())) {
            throw new ConflictoEstadoException("No se puede confirmar una reserva en estado " + r.getEstado());
        }

        if (r.getFechaVencimientoTtl().isBefore(LocalDateTime.now())) {
            r.setEstado("EXPIRADA");
            reservaRepository.save(r);
            throw new ConflictoEstadoException("La reserva provisoria ya expiró por TTL");
        }
        
        r.setEstado("CONFIRMADA");
        return reservaRepository.save(r);
    }

    @Transactional
    public Reserva liberarReserva(Long id) {
        Reserva r = reservaRepository.findById(id)
                .orElseThrow(() -> new RecursoNoEncontradoException("Reserva no encontrada: " + id));
        if ("PROVISORIA".equals(r.getEstado()) || "CONFIRMADA".equals(r.getEstado())) {
            r.setEstado("LIBERADA");
            return reservaRepository.save(r);
        }

        return r; // LIBERADA o EXPIRADA: ya no bloquea recursos, liberar es idempotente
    }

    @Transactional(readOnly = true)
    public Reserva obtener(Long id) {
        return reservaRepository.findById(id)
                .orElseThrow(() -> new RecursoNoEncontradoException("Reserva no encontrada: " + id));
    }

    // Pasa a EXPIRADA las provisorias cuyo TTL ya venció. Las vencidas ya no bloquean recursos aunque nadie
    // las toque (ver bloqueaRecursos), pero así el estado guardado también refleja la realidad.
    @Transactional
    public int expirarVencidas() {
        return reservaRepository.expirarReservasVencidas(LocalDateTime.now());
    }

    // Una reserva bloquea recursos solo si está CONFIRMADA, o PROVISORIA y todavía no venció su TTL.
    private boolean bloqueaRecursos(Reserva r, LocalDateTime ahora) {
        return "CONFIRMADA".equals(r.getEstado())
                || ("PROVISORIA".equals(r.getEstado()) && r.getFechaVencimientoTtl().isAfter(ahora));
    }

}
