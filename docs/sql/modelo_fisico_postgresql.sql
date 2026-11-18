-- ============================================================================
-- GEPSAC · Modelo físico relacional · PostgreSQL
-- Se deriva del modelo lógico (docs/MODELOS_CONCEPTUAL_LOGICO_FISICO.md).
-- NOTA: la app real usa Cloud Firestore; este script es el equivalente relacional.
-- NO se ejecutó en una base PostgreSQL real: probarlo (por ejemplo en pgAdmin) antes de entregarlo.
-- ============================================================================

CREATE TABLE usuario (
    id_usuario  VARCHAR(40)  PRIMARY KEY,
    nombre      VARCHAR(50)  NOT NULL CHECK (char_length(nombre) >= 3),
    email       VARCHAR(100) NOT NULL UNIQUE,
    telefono    CHAR(9)      NOT NULL CHECK (telefono ~ '^9[0-9]{8}$'),
    rol         VARCHAR(10)  NOT NULL DEFAULT 'cliente' CHECK (rol IN ('cliente', 'admin')),
    creado_en   TIMESTAMPTZ  NOT NULL DEFAULT now()
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
    fecha_entrega    DATE          NOT NULL,
    observacion      VARCHAR(150)  NOT NULL DEFAULT '',
    total            NUMERIC(10,2) NOT NULL CHECK (total > 0),
    creado_en        TIMESTAMPTZ   NOT NULL DEFAULT now()
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
    fecha       TIMESTAMPTZ   NOT NULL DEFAULT now()
);

-- Datos fijos (catálogos)
INSERT INTO marca (nombre) VALUES ('Caffe'), ('Kaphiy');
INSERT INTO etapa (id_etapa, nombre) VALUES
    (0, 'Por registrar'), (1, 'Registrado'), (2, 'Pagado'),
    (3, 'En preparación'), (4, 'Despachado'), (5, 'Entregado');
