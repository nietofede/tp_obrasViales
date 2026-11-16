package com.obrasviales.ms_recursos.service;

import com.obrasviales.ms_recursos.dto.CatalogoDtos.MaquinaResponse;
import com.obrasviales.ms_recursos.dto.CatalogoDtos.OperarioResponse;
import com.obrasviales.ms_recursos.repository.MaquinaRepository;
import com.obrasviales.ms_recursos.repository.OperarioRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

// Consulta de máquinas y operarios. Los datos se cargan con Flyway; las reglas de elegibilidad
// (RN-05 a RN-08) están en ReservaService.
@Service
public class CatalogoService {

    private final MaquinaRepository maquinaRepository;
    private final OperarioRepository operarioRepository;

    public CatalogoService(MaquinaRepository maquinaRepository, OperarioRepository operarioRepository) {
        this.maquinaRepository = maquinaRepository;
        this.operarioRepository = operarioRepository;
    }

    @Transactional(readOnly = true)
    public List<MaquinaResponse> listarMaquinas() {
        return maquinaRepository.findAll().stream().map(MaquinaResponse::desde).toList();
    }

    @Transactional(readOnly = true)
    public List<OperarioResponse> listarOperarios() {
        return operarioRepository.findAll().stream().map(OperarioResponse::desde).toList();
    }
}
