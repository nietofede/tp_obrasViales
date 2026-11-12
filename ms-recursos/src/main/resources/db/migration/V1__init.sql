CREATE TABLE cuadrillas (
    id BIGSERIAL PRIMARY KEY,
    nombre VARCHAR(100) NOT NULL UNIQUE,
    ultima_latitud DOUBLE PRECISION NOT NULL CHECK (ultima_latitud BETWEEN -90 AND 90),
    ultima_longitud DOUBLE PRECISION NOT NULL CHECK (ultima_longitud BETWEEN -180 AND 180),
    fecha_checkin TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE operarios (
    id BIGSERIAL PRIMARY KEY,
    cuadrilla_id BIGINT REFERENCES cuadrillas(id),
    keycloak_username VARCHAR(100) UNIQUE,
    nombre VARCHAR (150) NOT NULL,
    rol_operativo VARCHAR(50) NOT NULL DEFAULT 'OPERARIO',
    estado VARCHAR(50) NOT NULL DEFAULT 'ACTIVO'
);

CREATE TABLE certificaciones (
    id BIGSERIAL PRIMARY KEY,
    operario_id BIGINT NOT NULL REFERENCES operarios(id) ON DELETE CASCADE,
    tipo_maquina VARCHAR(100) NOT NULL,
    vencimiento DATE NOT NULL
);

CREATE TABLE maquinas (
    id BIGSERIAL PRIMARY KEY,
    cuadrilla_id BIGINT REFERENCES cuadrillas(id),
    codigo VARCHAR(50) NOT NULL UNIQUE,
    tipo VARCHAR(100) NOT NULL,
    estado VARCHAR(50) NOT NULL DEFAULT 'DISPONIBLE',
    horas_uso INT NOT NULL DEFAULT 0
);

CREATE TABLE mantenimientos (
    id BIGSERIAL PRIMARY KEY,
    maquina_id BIGINT NOT NULL REFERENCES maquinas(id) ON DELETE CASCADE,
    fecha_inicio DATE NOT NULL,
    fecha_fin DATE NOT NULL,
    motivo VARCHAR(255) NOT NULL
);

CREATE TABLE plantas_mezcla (
    id BIGSERIAL PRIMARY KEY,
    nombre VARCHAR(150) NOT NULL,
    latitud DOUBLE PRECISION NOT NULL CHECK (latitud BETWEEN -90 AND 90),
    longitud DOUBLE PRECISION NOT NULL CHECK (longitud BETWEEN -180 AND 180),
    activa BOOLEAN NOT NULL DEFAULT TRUE
);

CREATE TABLE composicion_minima_trabajo (
    tipo_trabajo VARCHAR(100) PRIMARY KEY,
    min_operarios INT NOT NULL,
    tipo_maquina_requerida VARCHAR(100),
    cantidad_maquinas INT NOT NULL DEFAULT 1,
    requiere_mezcla BOOLEAN NOT NULL DEFAULT FALSE
);

CREATE TABLE configuracion_desplazamiento (
    id BIGINT PRIMARY KEY DEFAULT 1 CHECK (id = 1),
    viaje_maximo_cuadrilla_min INT NOT NULL DEFAULT 90,
    duracion_jornada_min INT NOT NULL DEFAULT 480,
    ventana_transporte_mezcla_min INT NOT NULL DEFAULT 60,
    margen_camion_min INT NOT NULL DEFAULT 15
);

CREATE TABLE reservas (
    id BIGSERIAL PRIMARY KEY,
    cuadrilla_id BIGINT NOT NULL REFERENCES cuadrillas(id),
    planta_id BIGINT REFERENCES plantas_mezcla(id),
    orden_trabajo_id BIGINT NOT NULL UNIQUE,
    fecha_reserva DATE NOT NULL,
    fecha_vencimiento_ttl TIMESTAMP NOT NULL,
    estado VARCHAR(50) NOT NULL DEFAULT 'PROVISORIA',
    hora_llegada_cuadrilla TIME,
    hora_salida_camion TIME,
    tiempo_viaje_cuadrilla_min INT,
    tiempo_viaje_mezcla_min INT,
    fuente_ruta VARCHAR(20) NOT NULL DEFAULT 'OSRM'
);