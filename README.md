# GEPSAC – E-commerce (Caffe y Kaphiy) · MVVM + Firebase
Kotlin + XML + ViewBinding + ViewModel/LiveData + Coroutines. Datos en **Firebase** (Authentication + Cloud Firestore).

**Antes de ejecutar:** configura Firebase siguiendo [`firebase/README.md`](firebase/README.md)
(necesitas `app/google-services.json`, activar Auth y Firestore, publicar las reglas y crear el administrador).

Flujo del pedido: Por registrar → Registrado → Pagado → En preparación → Despachado → Entregado
- Cliente envía el pedido (Por registrar) → admin lo registra con un botón → cliente paga → admin avanza.
- Admin puede crear pedidos a nombre de un cliente ("Nuevo pedido"): entran ya Registrados.
- Cada avance/retroceso pide confirmación. Desde "Pagado" no se puede retroceder.
- El pago es simulado y no se guardan datos de tarjeta.

## Arquitectura (MVVM + Repository)
```
Fragment / Activity  →  ViewModel  →  interfaz Repository  ←  implementación Firebase
      (ui/*)            (ui/*)         (data/repository)        (data/firebase)
```
- `data/model` modelos · `data/repository` interfaces · `data/firebase` Auth y Firestore
- `ui/<pantalla>` cada pantalla con su ViewModel · `ui/common` utilidades de vista
- `di` crea los repositorios y los ViewModels · `util` validaciones, fechas y eventos

Detalle del modelo y de las carpetas: [`docs/MODELO_FIRESTORE.md`](docs/MODELO_FIRESTORE.md).
