package com.obrasviales.ms_recursos.entity;

import jakarta.persistence.*;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "operarios")
public class Operario {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cuadrilla_id")
    private Cuadrilla cuadrilla;

    @Column(name = "keycloak_username", unique = true, length = 100)
    private String keycloakUsername;

    @Column(nullable = false, length = 150)
    private String nombre;

    @Column(name = "rol_operativo", nullable = false, length = 50)
    private String rolOperativo = "OPERARIO";

    @Column(nullable = false, length = 50)
    private String estado = "ACTIVO";

    @OneToMany(mappedBy = "operario", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Certificacion> certificaciones = new ArrayList<>();

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Cuadrilla getCuadrilla() { return cuadrilla; }
    public void setCuadrilla(Cuadrilla cuadrilla) { this.cuadrilla = cuadrilla; }

    public String getKeycloakUsername() { return keycloakUsername; }
    public void setKeycloakUsername(String keycloakUsername) { this.keycloakUsername = keycloakUsername; }

    public String getNombre() { return nombre; }
    public void setNombre(String nombre) { this.nombre = nombre; }

    public String getRolOperativo() { return rolOperativo; }
    public void setRolOperativo(String rolOperativo) { this.rolOperativo = rolOperativo; }

    public String getEstado() { return estado; }
    public void setEstado(String estado) { this.estado = estado; }

    public List<Certificacion> getCertificaciones() { return certificaciones; }
    public void setCertificaciones(List<Certificacion> certificaciones) { this.certificaciones = certificaciones; }
    
}
