# GEPSAC – E-commerce (Caffe y Kaphiy) · MVVM local, v2
Kotlin + XML + ViewBinding + ViewModel/LiveData. SIN base de datos (datos en memoria; se reinician al cerrar la app).

Acceso demo (botones en el login): admin@gepsac.com / admin123 · cliente@gepsac.com / cliente123

Flujo del pedido: Por registrar → Registrado → Pagado → En preparación → Despachado → Entregado
- Cliente envía el pedido (Por registrar) → admin lo registra con un botón → cliente paga → admin avanza.
- Admin puede crear pedidos a nombre de un cliente ("Nuevo pedido"): entran ya Registrados.
- Cada avance/retroceso pide confirmación. Desde "Pagado" no se puede retroceder.
- El pago es simulado.

Arquitectura: ui/{auth,catalogo,carrito,pedidos,seguimiento,admin} → viewmodel → data/Repository.
Para Firebase solo se reemplaza data/Repository.kt.
