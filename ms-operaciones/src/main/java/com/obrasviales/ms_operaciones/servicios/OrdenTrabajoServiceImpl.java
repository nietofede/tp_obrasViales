package com.obrasviales.ms_operaciones.servicios;

import com.obrasviales.commons.exception.ConflictoEstadoException;
import com.obrasviales.commons.exception.ReglaNegocioException;
import com.obrasviales.ms_operaciones.clientes.ObrasClient;
import com.obrasviales.ms_operaciones.clientes.RecursosClient;
import com.obrasviales.ms_operaciones.dtos.CrearOrdenRequest;
import com.obrasviales.ms_operaciones.dtos.ContextoFaseDTO;
import com.obrasviales.ms_operaciones.dtos.DatosClimaDTO;
import com.obrasviales.ms_operaciones.dtos.OrdenTrabajoDTO;
import com.obrasviales.ms_operaciones.dtos.ReservaDTO;
import com.obrasviales.ms_operaciones.excepciones.OrdenNoEncontrada;
import com.obrasviales.ms_operaciones.excepciones.TransicionInvalida;
import com.obrasviales.ms_operaciones.modelo.EstadoOrden;
import com.obrasviales.ms_operaciones.modelo.EvaluacionClimatica;
import com.obrasviales.ms_operaciones.modelo.MotivoSuspension;
import com.obrasviales.ms_operaciones.modelo.OrdenTrabajo;
import com.obrasviales.ms_operaciones.modelo.TipoTrabajo;
import com.obrasviales.ms_operaciones.modelo.UmbralClimatico;
import com.obrasviales.ms_operaciones.repositorios.EvaluacionClimaticaRepository;
import com.obrasviales.ms_operaciones.repositorios.OrdenTrabajoRepository;
import com.obrasviales.ms_operaciones.repositorios.UmbralClimaticoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

@Service
@RequiredArgsConstructor
public class OrdenTrabajoServiceImpl implements OTServiceInterfaz {

    private final OrdenTrabajoRepository ordenTrabajoRepository;
    private final UmbralClimaticoRepository umbralClimaticoRepository;
    private final EvaluacionClimaticaRepository evaluacionClimaticaRepository;
    private final InterfazClimaService climaService;
    private final ObrasClient obrasClient;
    private final RecursosClient recursosClient;
    private final SuspensionService suspensionService;

    @Override
    public OrdenTrabajoDTO crearOrden(CrearOrdenRequest request) {
        ContextoFaseDTO contexto = obrasClient.obtenerContextoFase(request.getFaseId());
        if (!contexto.tramoId().equals(request.getTramoId())) {
            throw new ReglaNegocioException(
                    "La fase " + request.getFaseId() + " pertenece al tramo " + contexto.tramoId()
                            + ", no al tramo " + request.getTramoId() + ".",
                    "ORDEN-TRAMO");
        }

        OrdenTrabajo orden = new OrdenTrabajo();
        orden.setFaseId(request.getFaseId());
        orden.setTramoId(request.getTramoId());
        orden.setFechaProgramada(request.getFechaProgramada());
        orden.setEstado(EstadoOrden.PROGRAMADA);
        orden.setContadorSuspensiones(0);

        OrdenTrabajo guardada = ordenTrabajoRepository.save(orden);
        return mapearADto(guardada);
    }

    @Override
    @Transactional(readOnly = true)
    public OrdenTrabajoDTO obtenerPorId(Long id) {
        OrdenTrabajo orden = ordenTrabajoRepository.findById(id)
                .orElseThrow(() -> new OrdenNoEncontrada(id));
        return mapearADto(orden);
    }

    @Override
    @Transactional(readOnly = true)
    public List<OrdenTrabajoDTO> listarPorFechaYEstado(LocalDate fecha, EstadoOrden estado) {
        List<OrdenTrabajo> ordenes = (estado != null)
                ? ordenTrabajoRepository.findByFechaProgramadaAndEstado(fecha, estado)
                : ordenTrabajoRepository.findAll();
        return ordenes.stream().map(this::mapearADto).toList();
    }

    @Override
    @Transactional
    public OrdenTrabajoDTO cambiarEstado(Long id, EstadoOrden nuevoEstado) {
        OrdenTrabajo orden = ordenTrabajoRepository.findById(id)
                .orElseThrow(() -> new OrdenNoEncontrada(id));

        if (!orden.getEstado().puedeTransicionarHacia(nuevoEstado)) {
            throw new TransicionInvalida(
                    String.format("Transición no permitida desde %s hacia %s para la orden ID: %d",
                            orden.getEstado(), nuevoEstado, id)
            );
        }

        orden.setEstado(nuevoEstado);
        OrdenTrabajo actualizada = ordenTrabajoRepository.save(orden);
        return mapearADto(actualizada);
    }

    @Override
    @Transactional
    public OrdenTrabajoDTO habilitarOrden(Long ordenId) {
        OrdenTrabajo orden = ordenTrabajoRepository.findById(ordenId)
                .orElseThrow(() -> new OrdenNoEncontrada(ordenId));

        //solo se habilita una orden programada (o con recursos ya asignados por un intento anterior)
        EstadoOrden estadoInicial = orden.getEstado();
        if (estadoInicial != EstadoOrden.PROGRAMADA && estadoInicial != EstadoOrden.RECURSOS_ASIGNADOS) {
            throw new TransicionInvalida(
                    "La orden debe estar PROGRAMADA o con recursos asignados para habilitarla (estado actual: " + estadoInicial + ").");
        }

        // los datos de la fase (tipo de trabajo, coordenadas, fase previa) los saca de ms-obras
        ContextoFaseDTO contexto = obrasClient.obtenerContextoFase(orden.getFaseId());

        // fase no se habilita si la anterior no esta completa
        if (!contexto.faseAnteriorCompletada()) {
            throw new ConflictoEstadoException(
                    "RN-01: la fase previa de la fase " + orden.getFaseId() + " no está completa; no se puede habilitar la orden.");
        }

        TipoTrabajo tipoTrabajo = tipoTrabajoDe(contexto);
        Double latitud = contexto.latitud();
        Double longitud = contexto.longitud();

        // buscamos los umbrales para el tipo de trabajo, si no hay da error
        UmbralClimatico umbral = umbralClimaticoRepository.findByTipoTrabajo(tipoTrabajo)
                .orElseThrow(() -> new ReglaNegocioException(
                        "No existen umbrales climáticos configurados para el tipo de trabajo: " + tipoTrabajo, "RN-09"));

        // si no hay reserva vigente se pide una. Si Recursos rechaza  o no responde
        // la excepcion corta aca y la orden queda como estaba
        if (estadoInicial == EstadoOrden.PROGRAMADA || orden.getReservaId() == null) {
            ReservaDTO reserva = recursosClient.proponerYReservar(
                    orden.getId(), tipoTrabajo.name(), orden.getFechaProgramada(), latitud, longitud);
            orden.setReservaId(reserva.id());
            orden.setHoraSalidaCamion(reserva.horaSalidaCamion() != null
                    ? orden.getFechaProgramada().atTime(reserva.horaSalidaCamion())
                    : null);
            orden.setEstado(EstadoOrden.RECURSOS_ASIGNADOS);
        }

        orden.setEstado(EstadoOrden.EVALUACION_CLIMATICA);

        boolean umbralesCumplidos;
        String detalleClima;
        try {
            // pedimos los datos
            DatosClimaDTO datosClima = climaService.obtenerCondicionesClimaticas(latitud, longitud);

            if (datosClima == null) {
                throw new IllegalStateException("El servicio de clima devolvió datos vacíos.");
            }

            // vemos si se cumplen los umbrales climaticos segun los resultados de la api
            boolean humedadOk = datosClima.getHumedadSuelo() <= umbral.getHumedadSueloMaxima();
            boolean lluviaOk = datosClima.getProbabilidadLluvia() <= umbral.getProbPrecipitacionMaxima();
            boolean temperaturaOk = datosClima.getTemperatura() >= umbral.getTemperaturaMinima();
            umbralesCumplidos = humedadOk && lluviaOk && temperaturaOk;
            detalleClima = "Umbrales incumplidos para " + tipoTrabajo + ": humedad del suelo " + datosClima.getHumedadSuelo()
                    + " (máx. " + umbral.getHumedadSueloMaxima() + "), probabilidad de lluvia " + datosClima.getProbabilidadLluvia()
                    + "% (máx. " + umbral.getProbPrecipitacionMaxima() + "%), temperatura " + datosClima.getTemperatura()
                    + " °C (mín. " + umbral.getTemperaturaMinima() + " °C).";

            // registro de la evaluacion climatica
            EvaluacionClimatica evaluacion = new EvaluacionClimatica();
            evaluacion.setOrdenTrabajo(orden);
            evaluacion.setHumedadSueloRegistrada(datosClima.getHumedadSuelo());
            evaluacion.setProbPrecipitacionRegistrada(datosClima.getProbabilidadLluvia());
            evaluacion.setTemperaturaRegistrada(datosClima.getTemperatura());
            evaluacion.setUmbralesCumplidos(umbralesCumplidos);
            evaluacionClimaticaRepository.save(evaluacion);
        } catch (Exception e) {
            // Fallback: ante fallo de red, timeout o circuito abierto, pasa a revision manual.
            // La reserva se mantiene (todavia puede confirmarse cuando se resuelva).
            orden.setEstado(EstadoOrden.PENDIENTE_CONFIRMACION_CLIMATICA);
            return mapearADto(ordenTrabajoRepository.save(orden));
        }

        if (umbralesCumplidos) {
            // Si Recursos no puede confirmar (reserva vencida, caido) la excepcion deshace todo y la orden
            // queda como estaba; al reintentar se vuelve a reservar.
            recursosClient.confirmar(orden.getReservaId());
            orden.setEstado(EstadoOrden.HABILITADA);
            orden.setContadorSuspensiones(0);
        } else {
            // libera la reserva, registra la suspension y reprograma o marca la fase en riesgo 
            suspensionService.suspender(orden, MotivoSuspension.CLIMA, detalleClima);
        }

        return mapearADto(ordenTrabajoRepository.save(orden));
    }

    // Usado por JobHabilitacionDiaria: evalua solo el clima de una orden con recursos ya asignados
    @Override
    @Transactional
    public OrdenTrabajoDTO evaluarYActualizarEstadoClimatico(Long ordenId, Double latitud, Double longitud, TipoTrabajo tipoTrabajo) {
        OrdenTrabajo orden = ordenTrabajoRepository.findById(ordenId)
                .orElseThrow(() -> new OrdenNoEncontrada(ordenId));

        if (!orden.getEstado().puedeTransicionarHacia(EstadoOrden.EVALUACION_CLIMATICA)) {
            throw new TransicionInvalida(
                    "La orden debe tener recursos asignados antes de realizar la evaluación climática."
            );
        }

        UmbralClimatico umbral = umbralClimaticoRepository.findByTipoTrabajo(tipoTrabajo)
                .orElseThrow(() -> new ReglaNegocioException(
                        "No existen umbrales climáticos configurados para el tipo de trabajo: " + tipoTrabajo, "RN-09"));

        orden.setEstado(EstadoOrden.EVALUACION_CLIMATICA);

        try {
            DatosClimaDTO datosClima = climaService.obtenerCondicionesClimaticas(latitud, longitud);

            if (datosClima == null) {
                throw new IllegalStateException("El servicio de clima devolvió datos vacíos.");
            }

            boolean humedadOk = datosClima.getHumedadSuelo() <= umbral.getHumedadSueloMaxima();
            boolean lluviaOk = datosClima.getProbabilidadLluvia() <= umbral.getProbPrecipitacionMaxima();
            boolean temperaturaOk = datosClima.getTemperatura() >= umbral.getTemperaturaMinima();
            boolean umbralesCumplidos = humedadOk && lluviaOk && temperaturaOk;

            EvaluacionClimatica evaluacion = new EvaluacionClimatica();
            evaluacion.setOrdenTrabajo(orden);
            evaluacion.setHumedadSueloRegistrada(datosClima.getHumedadSuelo());
            evaluacion.setProbPrecipitacionRegistrada(datosClima.getProbabilidadLluvia());
            evaluacion.setTemperaturaRegistrada(datosClima.getTemperatura());
            evaluacion.setUmbralesCumplidos(umbralesCumplidos);
            evaluacionClimaticaRepository.save(evaluacion);

            if (umbralesCumplidos) {
                orden.setEstado(EstadoOrden.HABILITADA);
            } else {
                orden.setEstado(EstadoOrden.SUSPENDIDA);
                int suspensionesActuales = (orden.getContadorSuspensiones() != null) ? orden.getContadorSuspensiones() : 0;
                orden.setContadorSuspensiones(suspensionesActuales + 1);
            }
        } catch (Exception e) {
            // Fallback: ante fallo de red, timeout o circuito abierto, pasa a revision manual
            orden.setEstado(EstadoOrden.PENDIENTE_CONFIRMACION_CLIMATICA);
        }

        return mapearADto(ordenTrabajoRepository.save(orden));
    }

    // obras manda el tipo de trabajo como texto, si pasa algo da error y no se habilita orden
    private TipoTrabajo tipoTrabajoDe(ContextoFaseDTO contexto) {
        try {
            return TipoTrabajo.valueOf(contexto.tipoTrabajo());
        } catch (IllegalArgumentException | NullPointerException e) {
            throw new ReglaNegocioException(
                    "El tipo de trabajo '" + contexto.tipoTrabajo() + "' de la fase no es reconocido por ms-operaciones.", "RN-09");
        }
    }

    private OrdenTrabajoDTO mapearADto(OrdenTrabajo orden) {
        return OrdenTrabajoDTO.desde(orden);
    }

    @Override
    @Transactional(readOnly = true)
    public List<EvaluacionClimatica> obtenerHistorialClimatico(Long ordenId) {
        OrdenTrabajo orden = ordenTrabajoRepository.findById(ordenId)
                .orElseThrow(() -> new OrdenNoEncontrada(ordenId));
        
        return evaluacionClimaticaRepository.obtenerHistorialPorOrden(orden.getId());
    }
}
