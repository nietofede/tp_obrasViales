package com.obrasviales.ms_recursos.service;

import com.obrasviales.ms_recursos.dto.CatalogoDtos.MaquinaResponse;
import com.obrasviales.ms_recursos.dto.CatalogoDtos.OperarioResponse;
import com.obrasviales.ms_recursos.entity.Certificacion;
import com.obrasviales.ms_recursos.entity.Cuadrilla;
import com.obrasviales.ms_recursos.entity.Maquina;
import com.obrasviales.ms_recursos.entity.Operario;
import com.obrasviales.ms_recursos.repository.MaquinaRepository;
import com.obrasviales.ms_recursos.repository.OperarioRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

// Consulta de máquinas y operarios: cada dato sale tal como está cargado, con la cuadrilla y las certificaciones.
@ExtendWith(MockitoExtension.class)
class CatalogoServiceTest {

    @Mock
    private MaquinaRepository maquinaRepository;
    @Mock
    private OperarioRepository operarioRepository;

    @InjectMocks
    private CatalogoService service;

    @Test
    void listarMaquinasInformaSuCuadrilla() {
        Cuadrilla alfa = new Cuadrilla();
        alfa.setId(1L);
        Maquina asignada = new Maquina();
        asignada.setId(1L);
        asignada.setCodigo("PAV-01");
        asignada.setTipo("PAVIMENTADORA");
        asignada.setCuadrilla(alfa);
        Maquina libre = new Maquina();
        libre.setId(2L);
        libre.setCodigo("ROD-09");
        libre.setTipo("RODILLO_COMPACTADOR");
        when(maquinaRepository.findAll()).thenReturn(List.of(asignada, libre));

        List<MaquinaResponse> lista = service.listarMaquinas();

        assertThat(lista).extracting(MaquinaResponse::codigo).containsExactly("PAV-01", "ROD-09");
        assertThat(lista).extracting(MaquinaResponse::cuadrillaId).containsExactly(1L, null);
        assertThat(lista).extracting(MaquinaResponse::estado).containsOnly("DISPONIBLE");
    }

    @Test
    void listarOperariosIncluyeSusCertificaciones() {
        Operario o = new Operario();
        o.setId(1L);
        o.setNombre("Juan Pérez");
        Certificacion c = new Certificacion();
        c.setOperario(o);
        c.setTipoMaquina("PAVIMENTADORA");
        c.setVencimiento(LocalDate.of(2027, 12, 31));
        o.getCertificaciones().add(c);
        when(operarioRepository.findAll()).thenReturn(List.of(o));

        List<OperarioResponse> lista = service.listarOperarios();

        assertThat(lista).singleElement().satisfies(r -> {
            assertThat(r.nombre()).isEqualTo("Juan Pérez");
            assertThat(r.estado()).isEqualTo("ACTIVO");
            assertThat(r.cuadrillaId()).isNull();
            assertThat(r.certificaciones()).singleElement()
                    .satisfies(x -> assertThat(x.tipoMaquina()).isEqualTo("PAVIMENTADORA"));
        });
    }
}
