-- ============================================================================
-- GEPSAC · Modelo físico relacional · PostgreSQL
-- Se deriva del modelo lógico (docs/MODELOS_CONCEPTUAL_LOGICO_FISICO.md).
-- NOTA: la app real usa Cloud Firestore; este script es el equivalente relacional.
-- NO se ejecutó en una base PostgreSQL real: probarlo (por ejemplo en pgAdmin) antes de entregarlo.
-- Lo que NO es parte de la base de datos: el carrito, el tema claro/oscuro y los avisos (viven en el teléfono).
-- ============================================================================

CREATE TABLE usuario (
    id_usuario  VARCHAR(40)  PRIMARY KEY,
    nombre      VARCHAR(50)  NOT NULL CHECK (char_length(nombre) >= 3),
    email       VARCHAR(100) NOT NULL UNIQUE,
    telefono    CHAR(9)      NOT NULL CHECK (telefono ~ '^9[0-9]{8}$'),
    rol         VARCHAR(10)  NOT NULL DEFAULT 'cliente' CHECK (rol IN ('cliente', 'admin')),
    creado_en   TIMESTAMPTZ  NOT NULL DEFAULT now()
);

-- Direcciones de entrega guardadas por el cliente (en Firestore: arreglo 'direcciones' del usuario). Máximo 5 por usuario.
CREATE TABLE direccion_guardada (
    id_direccion  BIGSERIAL     PRIMARY KEY,
    id_usuario    VARCHAR(40)   NOT NULL REFERENCES usuario (id_usuario) ON DELETE CASCADE,
    direccion     VARCHAR(120)  NOT NULL CHECK (char_length(direccion) >= 8),
    UNIQUE (id_usuario, direccion)
);

CREATE TABLE marca (
    id_marca  SMALLSERIAL PRIMARY KEY,
    nombre    VARCHAR(30) NOT NULL UNIQUE
);

CREATE TABLE producto (
    id_producto     VARCHAR(40)   PRIMARY KEY,
    id_marca        SMALLINT      NOT NULL REFERENCES marca (id_marca),
    nombre          VARCHAR(40)   NOT NULL CHECK (char_length(nombre) >= 3),
    presentacion    VARCHAR(20)   NOT NULL,
    precio          NUMERIC(6,2)  NOT NULL CHECK (precio > 0 AND precio <= 9999),
    activo          BOOLEAN       NOT NULL DEFAULT TRUE,
    imagen          TEXT          NOT NULL DEFAULT '',
    actualizado_en  TIMESTAMPTZ   NOT NULL DEFAULT now(),
    UNIQUE (id_marca, nombre, presentacion)
);

CREATE TABLE etapa (
    id_etapa  SMALLINT    PRIMARY KEY CHECK (id_etapa BETWEEN 0 AND 5),
    nombre    VARCHAR(20) NOT NULL UNIQUE
);

CREATE SEQUENCE seq_numero_pedido START 100001;

CREATE TABLE pedido (
    id_pedido        VARCHAR(40)   PRIMARY KEY,
    numero           BIGINT        NOT NULL UNIQUE DEFAULT nextval('seq_numero_pedido'),
    id_cliente       VARCHAR(40)   NOT NULL REFERENCES usuario (id_usuario),
    id_creador       VARCHAR(40)   NOT NULL REFERENCES usuario (id_usuario),
    id_etapa_actual  SMALLINT      NOT NULL DEFAULT 0 REFERENCES etapa (id_etapa),
    telefono         CHAR(9)       NOT NULL CHECK (telefono ~ '^9[0-9]{8}$'),
    direccion        VARCHAR(120)  NOT NULL CHECK (char_length(direccion) >= 8),
    latitud          NUMERIC(9,6),
    longitud         NUMERIC(9,6),
    fecha_entrega    DATE          NOT NULL,
    observacion      VARCHAR(150)  NOT NULL DEFAULT '',
    total            NUMERIC(10,2) NOT NULL CHECK (total > 0),
    creado_en        TIMESTAMPTZ   NOT NULL DEFAULT now(),
    -- El punto del mapa es opcional, pero si se marca deben venir las dos coordenadas y ser válidas.
    CONSTRAINT pedido_ubicacion_ck CHECK (
        (latitud IS NULL AND longitud IS NULL)
        OR (latitud BETWEEN -90 AND 90 AND longitud BETWEEN -180 AND 180))
);

CREATE TABLE detalle_pedido (
    id_pedido        VARCHAR(40)  NOT NULL REFERENCES pedido (id_pedido) ON DELETE CASCADE,
    id_producto      VARCHAR(40)  NOT NULL REFERENCES producto (id_producto) ON DELETE RESTRICT,
    nombre_producto  VARCHAR(60)  NOT NULL,
    precio_unitario  NUMERIC(6,2) NOT NULL CHECK (precio_unitario > 0),
    cantidad         SMALLINT     NOT NULL CHECK (cantidad BETWEEN 1 AND 100),
    PRIMARY KEY (id_pedido, id_producto)
);

CREATE TABLE historial_etapa (
    id_pedido  VARCHAR(40) NOT NULL REFERENCES pedido (id_pedido) ON DELETE CASCADE,
    id_etapa   SMALLINT    NOT NULL REFERENCES etapa (id_etapa),
    fecha      TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (id_pedido, id_etapa)
);

CREATE TABLE pago (
    id_pago     BIGSERIAL     PRIMARY KEY,
    id_pedido   VARCHAR(40)   NOT NULL UNIQUE REFERENCES pedido (id_pedido) ON DELETE CASCADE,
    metodo      VARCHAR(15)   NOT NULL CHECK (metodo IN ('Yape', 'Plin', 'Transferencia', 'Tarjeta')),
    monto       NUMERIC(10,2) NOT NULL CHECK (monto > 0),
    referencia  VARCHAR(30)   NOT NULL,
    comprobante TEXT,
    fecha       TIMESTAMPTZ   NOT NULL DEFAULT now(),
    -- La captura del pago (JPEG en Base64) es obligatoria con Yape, Plin y Transferencia; con Tarjeta no lleva.
    CONSTRAINT pago_comprobante_ck CHECK (metodo = 'Tarjeta' OR comprobante IS NOT NULL)
);

-- Datos fijos (catálogos)
INSERT INTO marca (nombre) VALUES ('Caffe'), ('Kaphiy');
INSERT INTO etapa (id_etapa, nombre) VALUES
    (0, 'Por registrar'), (1, 'Registrado'), (2, 'Pagado'),
    (3, 'En preparación'), (4, 'Despachado'), (5, 'Entregado');
