# Modelo de datos GEPSAC en Firebase (Auth + Firestore)

Es el modelo que implementa la app (`model/firebase/`). Las reglas de seguridad están en
[`firebase/firestore.rules`](../firebase/firestore.rules) y los pasos de configuración en [`firebase/README.md`](../firebase/README.md).

## 1. Decisiones de diseño

| Tema | Decisión | Motivo |
|---|---|---|
| Contraseñas | Las gestiona **Firebase Auth**. `Usuario` ya no tiene `password`. | Antes estaban en texto plano en el objeto. |
| Id de usuario | `usuarios/{uid}` usa el `uid` de Auth. | Las reglas comparan `request.auth.uid` con el documento. |
| Marca y estados | Constantes en la app (`MARCAS`, `ESTADOS`), no colecciones. | Son listas cerradas de 2 y 6 valores. Las reglas validan los valores permitidos. |
| Ítems del pedido | **Array dentro del documento `pedidos`**, con nombre y precio copiados al momento de comprar. | Siempre se leen junto al pedido, y así el historial no cambia si luego se edita el precio. |
| Pago | **Mapa `pago` dentro del pedido** (relación 1 a 1). | Un pedido tiene un solo pago. |
| Tiempos por etapa | Mapa `tiempos` con una clave por etapa. | Reemplaza la lista `List<Long>` con 0L. Retroceder borra la clave. |
| Número de pedido | Contador en `contadores/pedidos`, incrementado en la misma transacción que crea el pedido. | El anterior (`timestamp % 1000000`) podía repetirse. |
| Imagen del producto | Foto reducida (480 px, JPEG) en **Base64 dentro del documento** del producto. | Firebase Storage exige el plan Blaze. Una foto pesa ~30–100 KB y el límite del documento es 1 MiB. |
| Fechas | `Timestamp` de Firestore; las de etapa y creación las pone el servidor (`serverTimestamp`). | Evita depender del reloj del teléfono. |

## 2. Colecciones

### `usuarios/{uid}`
| Campo | Tipo | Regla |
|---|---|---|
| nombre | string | 3 a 50 |
| email | string | igual al de Auth |
| telefono | string | `^9\d{8}`, **sin espacios** |
| rol | string | `"cliente"` o `"admin"` |
| creadoEn | timestamp | |

El registro desde la app solo permite `cliente`. El primer admin se crea a mano en la consola.

### `productos/{id}`
| Campo | Tipo | Regla |
|---|---|---|
| nombre | string | 3 a 40 |
| marca | string | `"Caffe"` o `"Kaphiy"` |
| presentacion | string | 1 a 20 (ej. `"250 gr"`) |
| precio | number | mayor a 0 y hasta 9999 |
| activo | bool | |
| imagen | string | Base64 del JPEG, vacío si no hay (máx. 300 000 caracteres) |
| actualizadoEn | timestamp | |

### `pedidos/{id}`
| Campo | Tipo | Regla |
|---|---|---|
| numero | int | viene del contador |
| clienteId | string | uid del cliente |
| clienteNombre, clienteEmail | string | copia del momento (el admin los ve sin leer `usuarios`) |
| creadoPor | string | uid de quien lo creó (cliente o admin) |
| telefono | string | `^9\d{8}`, sin espacios |
| direccion | string | 8 a 120 |
| fechaEntrega | timestamp | hoy o futura (se valida en la app) |
| observacion | string | hasta 150 |
| items | array de mapas | `{productoId, nombre, precio, cantidad}`, cantidad de 1 a 100 |
| total | number | suma de `precio * cantidad` |
| estado | int | 0 a 5 (índice de `ESTADOS`) |
| tiempos | map | `{ "0": ts, "1": ts, ... }`, una clave por etapa alcanzada |
| pago | map (opcional) | `{metodo, monto, referencia, fecha}` |
| creadoEn | timestamp | |

**No se guarda nunca** número de tarjeta, vencimiento ni CVV. De `PagoDialog` solo se conserva el método, el monto y la
referencia (código de operación, N° de operación o `Tarjeta ****1234`).

### `contadores/pedidos`
`{ ultimo: int }`. Se crea a mano con `100000`.

## 3. Reglas de negocio que también aplican las reglas de Firestore

- Cliente: crea pedidos "Por registrar" y solo puede pagar un pedido propio "Registrado".
- Admin: crea pedidos "Registrado", avanza o retrocede una etapa (no desde Registrado hacia adelante ni desde Pagado hacia atrás) y elimina solo pedidos sin pagar.
- Nadie puede cambiar `items`, `total` ni `clienteId` de un pedido existente.

**Límite:** Firestore Rules no puede recorrer el array `items`, así que no verifica que cada precio coincida con
`productos`. Para una tienda real esa validación va en una Cloud Function (plan Blaze).

## 4. Índices

Ninguno. La consulta del cliente es `whereEqualTo("clienteId")` sin `orderBy`; la app ordena la lista. Si más adelante se
agrega un orden o filtro combinado en la consulta, Firestore mostrará en el log un enlace para crear el índice.

## 5. Arquitectura en el código

```
com.gepsac.app
├── GepsacApp.kt              Application: guarda el contenedor de dependencias
├── di/                       Inyección manual
│   ├── AppContainer.kt         crea los repositorios (único lugar que conoce Firebase)
│   └── Fabrica.kt              cómo se construye cada ViewModel
├── model/                    MODEL: datos y reglas de negocio
│   ├── (raíz)                  Usuario, Producto, Pedido, NuevoPedido (puro Kotlin, sin Android ni Firebase)
│   ├── repository/             interfaces: AuthRepository, ProductoRepository, PedidoRepository, UsuarioRepository
│   └── firebase/               implementaciones con Auth/Firestore, mapeos documento <-> modelo, errores
├── view/                     VIEW: lo que se ve (Activities, Fragments, Adapters, diálogos)
│   ├── base/                   NavActivity (barra inferior)
│   ├── common/                 formato, vistas auxiliares, colores, imágenes
│   ├── auth/  cliente/         LoginActivity · ClienteActivity
│   ├── catalogo/  carrito/     CatalogoFragment, ProductoAdapter · CarritoFragment, CarritoAdapter
│   ├── pedidos/  seguimiento/  PedidosFragment, PedidoAdapter · SeguimientoActivity, PagoDialog
│   └── admin/                  AdminActivity, productos, clientes, nuevo pedido
├── viewmodel/                VIEWMODEL: un ViewModel por pantalla (Auth, Catalogo, Carrito, Pedidos,
│                             Seguimiento, AdminProductos, AdminClientes, NuevoPedido)
└── util/                     Validar, Fechas, Evento (mensajes de un solo uso), FlowExt
```

Flujo de dependencias (solo hacia abajo): `view → viewmodel → interfaz de Repository ← implementación Firebase`.
Las pantallas y los ViewModels nunca importan Firebase.
