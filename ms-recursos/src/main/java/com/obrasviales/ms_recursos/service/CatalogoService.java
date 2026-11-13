package com.obrasviales.ms_recursos.service;

import com.obrasviales.commons.exception.ConflictoEstadoException;
import com.obrasviales.commons.exception.RecursoNoEncontradoException;
import com.obrasviales.ms_recursos.dto.CatalogoDtos.CertificacionRequest;
import com.obrasviales.ms_recursos.dto.CatalogoDtos.CertificacionResponse;
import com.obrasviales.ms_recursos.dto.CatalogoDtos.MaquinaRequest;
import com.obrasviales.ms_recursos.dto.CatalogoDtos.MaquinaResponse;
import com.obrasviales.ms_recursos.dto.CatalogoDtos.OperarioRequest;
import com.obrasviales.ms_recursos.dto.CatalogoDtos.OperarioResponse;
import com.obrasviales.ms_recursos.dto.CatalogoDtos.PlantaRequest;
import com.obrasviales.ms_recursos.dto.CatalogoDtos.PlantaResponse;
import com.obrasviales.ms_recursos.dto.ReservaResponse;
import com.obrasviales.ms_recursos.entity.Certificacion;
import com.obrasviales.ms_recursos.entity.Maquina;
import com.obrasviales.ms_recursos.entity.Operario;
import com.obrasviales.ms_recursos.entity.PlantaMezcla;
import com.obrasviales.ms_recursos.repository.CertificacionRepository;
import com.obrasviales.ms_recursos.repository.MantenimientoRepository;
import com.obrasviales.ms_recursos.repository.MaquinaRepository;
import com.obrasviales.ms_recursos.repository.OperarioRepository;
import com.obrasviales.ms_recursos.repository.PlantaMezclaRepository;
import com.obrasviales.ms_recursos.repository.ReservaRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

// Altas, cambios y consultas de los catálogos de recursos. Las reglas de elegibilidad (RN-04 a RN-08) siguen
// en ReservaService; acá solo se cuida que los datos de entrada sean coherentes (códigos únicos, estados válidos).
@Service
public class CatalogoService {

    private final MaquinaRepository maquinaRepository;
    private final OperarioRepository operarioRepository;
    private final CertificacionRepository certificacionRepository;
    private final PlantaMezclaRepository plantaRepository;
    private final MantenimientoRepository mantenimientoRepository;
    private final ReservaRepository reservaRepository;

    public CatalogoService(MaquinaRepository maquinaRepository,
                           OperarioRepository operarioRepository,
                           CertificacionRepository certificacionRepository,
                           PlantaMezclaRepository plantaRepository,
                           MantenimientoRepository mantenimientoRepository,
                           ReservaRepository reservaRepository) {
        this.maquinaRepository = maquinaRepository;
        this.operarioRepository = operarioRepository;
        this.certificacionRepository = certificacionRepository;
        this.plantaRepository = plantaRepository;
        this.mantenimientoRepository = mantenimientoRepository;
        this.reservaRepository = reservaRepository;
    }

    // ---------- Máquinas ----------

    @Transactional
    public MaquinaResponse crearMaquina(MaquinaRequest req) {
        if (maquinaRepository.existsByCodigo(req.codigo())) {
            throw new ConflictoEstadoException("Ya existe una máquina con el código " + req.codigo());
        }
        Maquina m = new Maquina();
        m.setCodigo(req.codigo());
        m.setTipo(req.tipo());
        if (req.estado() != null) {
            m.setEstado(req.estado());
        }
        if (req.horasUso() != null) {
            m.setHorasUso(req.horasUso());
        }
        return MaquinaResponse.desde(maquinaRepository.save(m), null);
    }

    // El código es la identidad de la máquina y no cambia; se actualizan el tipo y el estado.
    @Transactional
    public MaquinaResponse actualizarMaquina(Long id, MaquinaRequest req) {
        Maquina m = maquinaRepository.findById(id)
                .orElseThrow(() -> noEncontrado("Máquina", id));
        m.setTipo(req.tipo());
        if (req.estado() != null) {
            m.setEstado(req.estado());
        }
        if (req.horasUso() != null) {
            m.setHorasUso(req.horasUso());
        }
        return MaquinaResponse.desde(maquinaRepository.save(m), null);
    }

    // Con fecha, cada máquina informa si tiene un mantenimiento programado ese día (insumo de RN-05).
    @Transactional(readOnly = true)
    public List<MaquinaResponse> listarMaquinas(LocalDate fecha) {
        return maquinaRepository.findAll().stream()
                .map(m -> MaquinaResponse.desde(m,
                        fecha == null ? null : mantenimientoRepository.tieneMantenimientoEnFecha(m.getId(), fecha)))
                .toList();
    }

    // ---------- Operarios y certificaciones ----------

    @Transactional
    public OperarioResponse crearOperario(OperarioRequest req) {
        if (req.keycloakUsername() != null && operarioRepository.existsByKeycloakUsername(req.keycloakUsername())) {
            throw new ConflictoEstadoException("Ya existe un operario con el usuario " + req.keycloakUsername());
        }
        Operario o = new Operario();
        o.setNombre(req.nombre());
        o.setKeycloakUsername(req.keycloakUsername());
        o.setRolOperativo(req.rolOperativo() != null ? req.rolOperativo() : "PEON");
        return OperarioResponse.desde(operarioRepository.save(o));
    }

    @Transactional(readOnly = true)
    public List<OperarioResponse> listarOperarios() {
        return operarioRepository.findAll().stream().map(OperarioResponse::desde).toList();
    }

    @Transactional
    public CertificacionResponse agregarCertificacion(Long operarioId, CertificacionRequest req) {
        Operario o = operarioRepository.findById(operarioId)
                .orElseThrow(() -> noEncontrado("Operario", operarioId));
        Certificacion c = new Certificacion();
        c.setOperario(o);
        c.setTipoMaquina(req.tipoMaquina());
        c.setVencimiento(req.vencimiento());
        return CertificacionResponse.desde(certificacionRepository.save(c));
    }

    // Lo que ve el operario logueado: las reservas vigentes de su cuadrilla, de la fecha pedida o, si no se
    // indica, de hoy en adelante.
    @Transactional(readOnly = true)
    public List<ReservaResponse> asignacionesDe(String usuarioKeycloak, LocalDate fecha) {
        Operario o = operarioRepository.findByKeycloakUsername(usuarioKeycloak)
                .orElseThrow(() -> new RecursoNoEncontradoException(
                        "No hay un operario registrado para el usuario " + usuarioKeycloak));
        if (o.getCuadrilla() == null) {
            return List.of();
        }
        return reservaRepository.asignacionesVigentes(o.getCuadrilla().getId(), LocalDate.now(),
                        LocalDateTime.now())
                .stream()
                .filter(r -> fecha == null || r.getFechaReserva().equals(fecha))
                .map(ReservaResponse::desde).toList();
    }

    // ---------- Plantas de mezcla ----------

    @Transactional
    public PlantaResponse crearPlanta(PlantaRequest req) {
        PlantaMezcla p = new PlantaMezcla();
        p.setNombre(req.nombre());
        p.setLatitud(req.latitud());
        p.setLongitud(req.longitud());
        p.setActiva(req.activa() == null || req.activa());
        return PlantaResponse.desde(plantaRepository.save(p));
    }

    @Transactional(readOnly = true)
    public List<PlantaResponse> listarPlantas() {
        return plantaRepository.findAll().stream().map(PlantaResponse::desde).toList();
    }

    private RecursoNoEncontradoException noEncontrado(String que, Long id) {
        return new RecursoNoEncontradoException(que + " no encontrada/o: " + id);
    }
}
