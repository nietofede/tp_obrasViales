package com.obrasviales.ms_recursos.service;

import com.obrasviales.commons.exception.ConflictoEstadoException;
import com.obrasviales.commons.exception.RecursoNoEncontradoException;
import com.obrasviales.commons.exception.ReglaNegocioException;
import com.obrasviales.ms_recursos.dto.CatalogoDtos.CuadrillaRequest;
import com.obrasviales.ms_recursos.dto.CatalogoDtos.CuadrillaResponse;
import com.obrasviales.ms_recursos.dto.CatalogoDtos.UbicacionRequest;
import com.obrasviales.ms_recursos.entity.Cuadrilla;
import com.obrasviales.ms_recursos.entity.Maquina;
import com.obrasviales.ms_recursos.entity.Operario;
import com.obrasviales.ms_recursos.repository.CertificacionRepository;
import com.obrasviales.ms_recursos.repository.CuadrillaRepository;
import com.obrasviales.ms_recursos.repository.MaquinaRepository;
import com.obrasviales.ms_recursos.repository.OperarioRepository;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

// Alta y modificación de cuadrillas y check-in de ubicación. Al armar una cuadrilla se cuidan las dos reglas que
// se pueden decidir sin conocer el trabajo: RN-04 (cada máquina tiene un operario con certificación vigente para su
// tipo) y que cada persona o máquina esté en una sola cuadrilla. La composición mínima por tipo de trabajo (RN-06)
// se evalúa al reservar, porque depende del trabajo.
@Service
public class CuadrillaService {

    private final CuadrillaRepository cuadrillaRepository;
    private final OperarioRepository operarioRepository;
    private final MaquinaRepository maquinaRepository;
    private final CertificacionRepository certificacionRepository;

    public CuadrillaService(CuadrillaRepository cuadrillaRepository,
                            OperarioRepository operarioRepository,
                            MaquinaRepository maquinaRepository,
                            CertificacionRepository certificacionRepository) {
        this.cuadrillaRepository = cuadrillaRepository;
        this.operarioRepository = operarioRepository;
        this.maquinaRepository = maquinaRepository;
        this.certificacionRepository = certificacionRepository;
    }

    @Transactional
    public CuadrillaResponse crear(CuadrillaRequest req) {
        if (cuadrillaRepository.existsByNombre(req.nombre())) {
            throw new ConflictoEstadoException("Ya existe una cuadrilla con el nombre " + req.nombre());
        }
        Cuadrilla c = new Cuadrilla();
        c.setNombre(req.nombre());
        c.setUltimaLatitud(req.latitudBase());
        c.setUltimaLongitud(req.longitudBase());
        c.setFechaCheckin(LocalDateTime.now());
        c = cuadrillaRepository.save(c);
        asignar(c, req.operarioIds(), req.maquinaIds());
        return armar(c);
    }

    @Transactional
    public CuadrillaResponse actualizar(Long id, CuadrillaRequest req) {
        Cuadrilla c = buscar(id);
        if (cuadrillaRepository.existsByNombreAndIdNot(req.nombre(), id)) {
            throw new ConflictoEstadoException("Ya existe otra cuadrilla con el nombre " + req.nombre());
        }
        c.setNombre(req.nombre());
        c.setUltimaLatitud(req.latitudBase());
        c.setUltimaLongitud(req.longitudBase());
        c = cuadrillaRepository.save(c);
        asignar(c, req.operarioIds(), req.maquinaIds());
        return armar(c);
    }

    @Transactional(readOnly = true)
    public List<CuadrillaResponse> listar() {
        return cuadrillaRepository.findAll().stream().map(this::armar).toList();
    }

    @Transactional(readOnly = true)
    public CuadrillaResponse obtener(Long id) {
        return armar(buscar(id));
    }

    // El capataz informa dónde está su cuadrilla. Solo puede hacerlo el CAPATAZ de esa cuadrilla.
    @Transactional
    public CuadrillaResponse registrarUbicacion(Long cuadrillaId, UbicacionRequest req, String usuarioKeycloak) {
        Cuadrilla c = buscar(cuadrillaId);
        Operario capataz = operarioRepository.findByKeycloakUsername(usuarioKeycloak).orElse(null);
        boolean esCapatazDeLaCuadrilla = capataz != null
                && "CAPATAZ".equals(capataz.getRolOperativo())
                && capataz.getCuadrilla() != null
                && capataz.getCuadrilla().getId().equals(c.getId());
        if (!esCapatazDeLaCuadrilla) {
            throw new AccessDeniedException("Solo el capataz de la cuadrilla puede registrar su ubicación");
        }
        c.setUltimaLatitud(req.latitud());
        c.setUltimaLongitud(req.longitud());
        c.setFechaCheckin(LocalDateTime.now());
        return armar(cuadrillaRepository.save(c));
    }

    private Cuadrilla buscar(Long id) {
        return cuadrillaRepository.findById(id)
                .orElseThrow(() -> new RecursoNoEncontradoException("Cuadrilla no encontrada: " + id));
    }

    private CuadrillaResponse armar(Cuadrilla c) {
        return CuadrillaResponse.desde(c,
                operarioRepository.findByCuadrillaId(c.getId()),
                maquinaRepository.findByCuadrillaId(c.getId()));
    }

    private void asignar(Cuadrilla c, List<Long> operarioIds, List<Long> maquinaIds) {
        List<Operario> operarios = new ArrayList<>();
        for (Long id : new LinkedHashSet<>(operarioIds)) {
            Operario o = operarioRepository.findById(id)
                    .orElseThrow(() -> new RecursoNoEncontradoException("Operario no encontrado: " + id));
            if (!"ACTIVO".equals(o.getEstado())) {
                throw new ConflictoEstadoException("El operario " + o.getNombre() + " no está activo");
            }
            if (o.getCuadrilla() != null && !o.getCuadrilla().getId().equals(c.getId())) {
                throw new ConflictoEstadoException(
                        "El operario " + o.getNombre() + " ya pertenece a otra cuadrilla");
            }
            operarios.add(o);
        }
        List<Maquina> maquinas = new ArrayList<>();
        for (Long id : new LinkedHashSet<>(maquinaIds)) {
            Maquina m = maquinaRepository.findById(id)
                    .orElseThrow(() -> new RecursoNoEncontradoException("Máquina no encontrada: " + id));
            if ("FUERA_DE_SERVICIO".equals(m.getEstado())) {
                throw new ConflictoEstadoException("La máquina " + m.getCodigo() + " está fuera de servicio");
            }
            if (m.getCuadrilla() != null && !m.getCuadrilla().getId().equals(c.getId())) {
                throw new ConflictoEstadoException(
                        "La máquina " + m.getCodigo() + " ya pertenece a otra cuadrilla");
            }
            maquinas.add(m);
        }

        if (operarios.isEmpty()) {
            throw new ReglaNegocioException("Una cuadrilla necesita al menos un operario", "RN-06");
        }
        // RN-04: cada tipo de máquina de la cuadrilla necesita al menos un operario con certificación vigente.
        LocalDate hoy = LocalDate.now();
        Set<String> tipos = new LinkedHashSet<>();
        maquinas.forEach(m -> tipos.add(m.getTipo()));
        for (String tipo : tipos) {
            boolean alguienCertificado = operarios.stream().anyMatch(o ->
                    certificacionRepository.existsByOperarioIdAndTipoMaquinaAndVencimientoGreaterThanEqual(
                            o.getId(), tipo, hoy));
            if (!alguienCertificado) {
                throw new ReglaNegocioException(
                        "Ningún operario de la cuadrilla tiene certificación vigente para " + tipo, "RN-04");
            }
        }

        // Quienes estaban en la cuadrilla y ya no figuran en la lista quedan libres.
        for (Operario previo : operarioRepository.findByCuadrillaId(c.getId())) {
            if (operarios.stream().noneMatch(o -> o.getId().equals(previo.getId()))) {
                previo.setCuadrilla(null);
                operarioRepository.save(previo);
            }
        }
        for (Maquina previa : maquinaRepository.findByCuadrillaId(c.getId())) {
            if (maquinas.stream().noneMatch(m -> m.getId().equals(previa.getId()))) {
                previa.setCuadrilla(null);
                maquinaRepository.save(previa);
            }
        }
        operarios.forEach(o -> {
            o.setCuadrilla(c);
            operarioRepository.save(o);
        });
        maquinas.forEach(m -> {
            m.setCuadrilla(c);
            maquinaRepository.save(m);
        });
    }
}
