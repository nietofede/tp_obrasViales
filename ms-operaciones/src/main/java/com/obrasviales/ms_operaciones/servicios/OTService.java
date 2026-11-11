package com.obrasviales.ms_operaciones.servicios;

import com.obrasviales.ms_operaciones.dtos.CrearOrdenRequest;
import com.obrasviales.ms_operaciones.dtos.OrdenTrabajoDTO;
import com.obrasviales.ms_operaciones.excepciones.OrdenNoEncontrada;
import com.obrasviales.ms_operaciones.excepciones.TransicionInvalida;
import com.obrasviales.ms_operaciones.modelo.EstadoOrden;
import com.obrasviales.ms_operaciones.modelo.OrdenTrabajo;
import com.obrasviales.ms_operaciones.repositorios.OrdenTrabajoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

@Service
@RequiredArgsConstructor
public class OTService implements OTServiceInterfaz {

    private final OrdenTrabajoRepository ordenTrabajoRepository;

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

        // Validación RN-11
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
}