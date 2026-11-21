-- ============================================================================
-- GEPSAC · Modelo físico relacional · Oracle Database 12c
-- Se deriva del modelo lógico (docs/MODELOS_CONCEPTUAL_LOGICO_FISICO.md).
-- NOTA: la app real usa Cloud Firestore; este script es el equivalente relacional.
-- NO se ejecutó en una base Oracle real: probarlo antes de entregarlo.
-- Oracle guarda la cadena vacía '' como NULL, por eso observacion, imagen y comprobante admiten NULL.
-- Lo que NO es parte de la base de datos: el carrito, el tema claro/oscuro y los avisos (viven en el teléfono).
-- ============================================================================

CREATE TABLE usuario (
    id_usuario  VARCHAR2(40)   NOT NULL,
    nombre      VARCHAR2(50)   NOT NULL,
    email       VARCHAR2(100)  NOT NULL,
    telefono    CHAR(9)        NOT NULL,
    rol         VARCHAR2(10)   DEFAULT 'cliente' NOT NULL,
    creado_en   TIMESTAMP      DEFAULT SYSTIMESTAMP NOT NULL
);

ALTER TABLE usuario ADD CONSTRAINT usuario_pk PRIMARY KEY ( id_usuario );
ALTER TABLE usuario ADD CONSTRAINT usuario_email_uk UNIQUE ( email );
ALTER TABLE usuario ADD CONSTRAINT usuario_rol_ck CHECK ( rol IN ( 'cliente', 'admin' ) );
ALTER TABLE usuario ADD CONSTRAINT usuario_telefono_ck CHECK ( REGEXP_LIKE ( telefono, '^9[0-9]{8}$' ) );
ALTER TABLE usuario ADD CONSTRAINT usuario_nombre_ck CHECK ( LENGTH ( nombre ) >= 3 );

-- Direcciones de entrega guardadas por el cliente (en Firestore: arreglo 'direcciones' del usuario). Máximo 5 por usuario.
CREATE TABLE direccion_guardada (
    id_direccion  NUMBER(19) GENERATED ALWAYS AS IDENTITY,
    id_usuario    VARCHAR2(40)   NOT NULL,
    direccion     VARCHAR2(120)  NOT NULL
);

ALTER TABLE direccion_guardada ADD CONSTRAINT direccion_guardada_pk PRIMARY KEY ( id_direccion );
ALTER TABLE direccion_guardada ADD CONSTRAINT direccion_guardada_uk UNIQUE ( id_usuario, direccion );
ALTER TABLE direccion_guardada ADD CONSTRAINT direccion_guardada_largo_ck CHECK ( LENGTH ( direccion ) >= 8 );

CREATE TABLE marca (
    id_marca  NUMBER(3) GENERATED ALWAYS AS IDENTITY,
    nombre    VARCHAR2(30) NOT NULL
);

ALTER TABLE marca ADD CONSTRAINT marca_pk PRIMARY KEY ( id_marca );
ALTER TABLE marca ADD CONSTRAINT marca_nombre_uk UNIQUE ( nombre );

CREATE TABLE producto (
    id_producto     VARCHAR2(40)  NOT NULL,
    id_marca        NUMBER(3)     NOT NULL,
    nombre          VARCHAR2(40)  NOT NULL,
    presentacion    VARCHAR2(20)  NOT NULL,
    precio          NUMBER(6,2)   NOT NULL,
    activo          NUMBER(1)     DEFAULT 1 NOT NULL,
    imagen          CLOB,
    actualizado_en  TIMESTAMP     DEFAULT SYSTIMESTAMP NOT NULL
);

ALTER TABLE producto ADD CONSTRAINT producto_pk PRIMARY KEY ( id_producto );
ALTER TABLE producto ADD CONSTRAINT producto_uk UNIQUE ( id_marca, nombre, presentacion );
ALTER TABLE producto ADD CONSTRAINT producto_precio_ck CHECK ( precio > 0 AND precio <= 9999 );
ALTER TABLE producto ADD CONSTRAINT producto_activo_ck CHECK ( activo IN ( 0, 1 ) );

CREATE TABLE etapa (
    id_etapa  NUMBER(1)     NOT NULL,
    nombre    VARCHAR2(20)  NOT NULL
);

ALTER TABLE etapa ADD CONSTRAINT etapa_pk PRIMARY KEY ( id_etapa );
ALTER TABLE etapa ADD CONSTRAINT etapa_nombre_uk UNIQUE ( nombre );
ALTER TABLE etapa ADD CONSTRAINT etapa_id_ck CHECK ( id_etapa BETWEEN 0 AND 5 );

CREATE SEQUENCE seq_numero_pedido START WITH 100001 INCREMENT BY 1 NOCACHE;

CREATE TABLE pedido (
    id_pedido        VARCHAR2(40)   NOT NULL,
    numero           NUMBER(19)     DEFAULT seq_numero_pedido.NEXTVAL NOT NULL,
    id_cliente       VARCHAR2(40)   NOT NULL,
    id_creador       VARCHAR2(40)   NOT NULL,
    id_etapa_actual  NUMBER(1)      DEFAULT 0 NOT NULL,
    telefono         CHAR(9)        NOT NULL,
    direccion        VARCHAR2(120)  NOT NULL,
    latitud          NUMBER(9,6),
    longitud         NUMBER(9,6),
    fecha_entrega    DATE           NOT NULL,
    observacion      VARCHAR2(150),
    total            NUMBER(10,2)   NOT NULL,
    creado_en        TIMESTAMP      DEFAULT SYSTIMESTAMP NOT NULL
);

ALTER TABLE pedido ADD CONSTRAINT pedido_pk PRIMARY KEY ( id_pedido );
ALTER TABLE pedido ADD CONSTRAINT pedido_numero_uk UNIQUE ( numero );
ALTER TABLE pedido ADD CONSTRAINT pedido_telefono_ck CHECK ( REGEXP_LIKE ( telefono, '^9[0-9]{8}$' ) );
ALTER TABLE pedido ADD CONSTRAINT pedido_direccion_ck CHECK ( LENGTH ( direccion ) >= 8 );
ALTER TABLE pedido ADD CONSTRAINT pedido_total_ck CHECK ( total > 0 );
-- El punto del mapa es opcional, pero si se marca deben venir las dos coordenadas y ser válidas.
ALTER TABLE pedido ADD CONSTRAINT pedido_ubicacion_ck CHECK (
    ( latitud IS NULL AND longitud IS NULL )
    OR ( latitud BETWEEN -90 AND 90 AND longitud BETWEEN -180 AND 180 ) );

CREATE TABLE detalle_pedido (
    id_pedido        VARCHAR2(40)  NOT NULL,
    id_producto      VARCHAR2(40)  NOT NULL,
    nombre_producto  VARCHAR2(60)  NOT NULL,
    precio_unitario  NUMBER(6,2)   NOT NULL,
    cantidad         NUMBER(3)     NOT NULL
);

ALTER TABLE detalle_pedido ADD CONSTRAINT detalle_pedido_pk PRIMARY KEY ( id_pedido, id_producto );
ALTER TABLE detalle_pedido ADD CONSTRAINT detalle_pedido_cantidad_ck CHECK ( cantidad BETWEEN 1 AND 100 );
ALTER TABLE detalle_pedido ADD CONSTRAINT detalle_pedido_precio_ck CHECK ( precio_unitario > 0 );

CREATE TABLE historial_etapa (
    id_pedido  VARCHAR2(40)  NOT NULL,
    id_etapa   NUMBER(1)     NOT NULL,
    fecha      TIMESTAMP     DEFAULT SYSTIMESTAMP NOT NULL
);

ALTER TABLE historial_etapa ADD CONSTRAINT historial_etapa_pk PRIMARY KEY ( id_pedido, id_etapa );

CREATE TABLE pago (
    id_pago     NUMBER(19) GENERATED ALWAYS AS IDENTITY,
    id_pedido   VARCHAR2(40)  NOT NULL,
    metodo      VARCHAR2(15)  NOT NULL,
    monto       NUMBER(10,2)  NOT NULL,
    referencia  VARCHAR2(30)  NOT NULL,
    comprobante CLOB,
    fecha       TIMESTAMP     DEFAULT SYSTIMESTAMP NOT NULL
);

ALTER TABLE pago ADD CONSTRAINT pago_pk PRIMARY KEY ( id_pago );
ALTER TABLE pago ADD CONSTRAINT pago_pedido_uk UNIQUE ( id_pedido );
ALTER TABLE pago ADD CONSTRAINT pago_metodo_ck CHECK ( metodo IN ( 'Yape', 'Plin', 'Transferencia', 'Tarjeta' ) );
ALTER TABLE pago ADD CONSTRAINT pago_monto_ck CHECK ( monto > 0 );
-- La captura del pago (JPEG en Base64) es obligatoria con Yape, Plin y Transferencia; con Tarjeta no lleva.
ALTER TABLE pago ADD CONSTRAINT pago_comprobante_ck CHECK ( metodo = 'Tarjeta' OR comprobante IS NOT NULL );

-- ---------------------------------------------------------------------------
-- Claves foráneas
-- ---------------------------------------------------------------------------
ALTER TABLE direccion_guardada ADD CONSTRAINT direccion_guardada_usuario_fk
    FOREIGN KEY ( id_usuario ) REFERENCES usuario ( id_usuario ) ON DELETE CASCADE;

ALTER TABLE producto ADD CONSTRAINT producto_marca_fk
    FOREIGN KEY ( id_marca ) REFERENCES marca ( id_marca );

ALTER TABLE pedido ADD CONSTRAINT pedido_usuario_cliente_fk
    FOREIGN KEY ( id_cliente ) REFERENCES usuario ( id_usuario );

ALTER TABLE pedido ADD CONSTRAINT pedido_usuario_creador_fk
    FOREIGN KEY ( id_creador ) REFERENCES usuario ( id_usuario );

ALTER TABLE pedido ADD CONSTRAINT pedido_etapa_fk
    FOREIGN KEY ( id_etapa_actual ) REFERENCES etapa ( id_etapa );

ALTER TABLE detalle_pedido ADD CONSTRAINT detalle_pedido_pedido_fk
    FOREIGN KEY ( id_pedido ) REFERENCES pedido ( id_pedido ) ON DELETE CASCADE;

ALTER TABLE detalle_pedido ADD CONSTRAINT detalle_pedido_producto_fk
    FOREIGN KEY ( id_producto ) REFERENCES producto ( id_producto );

ALTER TABLE historial_etapa ADD CONSTRAINT historial_etapa_pedido_fk
    FOREIGN KEY ( id_pedido ) REFERENCES pedido ( id_pedido ) ON DELETE CASCADE;

ALTER TABLE historial_etapa ADD CONSTRAINT historial_etapa_etapa_fk
    FOREIGN KEY ( id_etapa ) REFERENCES etapa ( id_etapa );

ALTER TABLE pago ADD CONSTRAINT pago_pedido_fk
    FOREIGN KEY ( id_pedido ) REFERENCES pedido ( id_pedido ) ON DELETE CASCADE;

-- ---------------------------------------------------------------------------
-- Datos fijos (catálogos)
-- ---------------------------------------------------------------------------
INSERT INTO marca ( nombre ) VALUES ( 'Caffe' );
INSERT INTO marca ( nombre ) VALUES ( 'Kaphiy' );

INSERT INTO etapa ( id_etapa, nombre ) VALUES ( 0, 'Por registrar' );
INSERT INTO etapa ( id_etapa, nombre ) VALUES ( 1, 'Registrado' );
INSERT INTO etapa ( id_etapa, nombre ) VALUES ( 2, 'Pagado' );
INSERT INTO etapa ( id_etapa, nombre ) VALUES ( 3, 'En preparación' );
INSERT INTO etapa ( id_etapa, nombre ) VALUES ( 4, 'Despachado' );
INSERT INTO etapa ( id_etapa, nombre ) VALUES ( 5, 'Entregado' );

COMMIT;
