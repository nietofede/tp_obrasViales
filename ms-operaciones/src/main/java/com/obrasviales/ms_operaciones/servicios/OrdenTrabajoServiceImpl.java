package com.obrasviales.ms_operaciones.servicios;

import com.obrasviales.ms_operaciones.dtos.CrearOrdenRequest;
import com.obrasviales.ms_operaciones.dtos.DatosClimaDTO;
import com.obrasviales.ms_operaciones.dtos.OrdenTrabajoDTO;
import com.obrasviales.ms_operaciones.excepciones.OrdenNoEncontrada;
import com.obrasviales.ms_operaciones.excepciones.TransicionInvalida;
import com.obrasviales.ms_operaciones.modelo.EstadoOrden;
import com.obrasviales.ms_operaciones.modelo.EvaluacionClimatica;
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

    @Override
    @Transactional
    public OrdenTrabajoDTO crearOrden(CrearOrdenRequest request) {
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
    public OrdenTrabajoDTO evaluarYActualizarEstadoClimatico(Long ordenId, Double latitud, Double longitud, TipoTrabajo tipoTrabajo) {
        OrdenTrabajo orden = ordenTrabajoRepository.findById(ordenId)
                .orElseThrow(() -> new OrdenNoEncontrada(ordenId));

        if (!orden.getEstado().puedeTransicionarHacia(EstadoOrden.EVALUACION_CLIMATICA)) {
            throw new TransicionInvalida(
                    "La orden debe tener recursos asignados antes de realizar la evaluación climática."
            );
        }

        // buscamos los umbrales para el tipo de trabajo, si no hay bueno un error 
        UmbralClimatico umbral = umbralClimaticoRepository.findByTipoTrabajo(tipoTrabajo)
                .orElseThrow(() -> new IllegalArgumentException(
                        "No existen umbrales climáticos configurados para el tipo de trabajo: " + tipoTrabajo));

        orden.setEstado(EstadoOrden.EVALUACION_CLIMATICA);

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

            boolean umbralesCumplidos = humedadOk && lluviaOk && temperaturaOk;

            // registro de la evaluacion climatica 
            EvaluacionClimatica evaluacion = new EvaluacionClimatica();
            evaluacion.setOrdenTrabajo(orden);
            evaluacion.setHumedadSueloRegistrada(datosClima.getHumedadSuelo());
            evaluacion.setProbPrecipitacionRegistrada(datosClima.getProbabilidadLluvia());
            evaluacion.setTemperaturaRegistrada(datosClima.getTemperatura());
            evaluacion.setUmbralesCumplidos(umbralesCumplidos);
            evaluacionClimaticaRepository.save(evaluacion);

            // transicion de estado segun los resultados 
            if (umbralesCumplidos) {
                orden.setEstado(EstadoOrden.HABILITADA);
            } else {
                orden.setEstado(EstadoOrden.SUSPENDIDA);
                int suspensionesActuales = (orden.getContadorSuspensiones() != null) ? orden.getContadorSuspensiones() : 0;
                orden.setContadorSuspensiones(suspensionesActuales + 1);
            }

        } catch (Exception e) {
            // Fallback: Ante fallo de red, timeout o circuito abierto, pasa a revisión manual
            orden.setEstado(EstadoOrden.PENDIENTE_CONFIRMACION_CLIMATICA);
        }

        OrdenTrabajo actualizada = ordenTrabajoRepository.save(orden);
        return mapearADto(actualizada);
    }

    private OrdenTrabajoDTO mapearADto(OrdenTrabajo orden) {
        OrdenTrabajoDTO dto = new OrdenTrabajoDTO();
        dto.setId(orden.getId());
        dto.setFaseId(orden.getFaseId());
        dto.setTramoId(orden.getTramoId());
        dto.setFechaProgramada(orden.getFechaProgramada());
        dto.setEstado(orden.getEstado());
        dto.setReservaId(orden.getReservaId());
        dto.setHoraSalidaCamion(orden.getHoraSalidaCamion());
        dto.setContadorSuspensiones(orden.getContadorSuspensiones());
        return dto;
    }

    @Override
    @Transactional(readOnly = true)
    public List<EvaluacionClimatica> obtenerHistorialClimatico(Long ordenId) {
        OrdenTrabajo orden = ordenTrabajoRepository.findById(ordenId)
                .orElseThrow(() -> new RuntimeException("Orden de trabajo no encontrada con id: " + ordenId));
        
        return evaluacionClimaticaRepository.obtenerHistorialPorOrden(orden.getId());
    }
}