# Configurar Firebase para GEPSAC

La app usa **Firebase Authentication** (correo y contraseña) y **Cloud Firestore**. El modelo de datos está en
[`docs/MODELO_FIRESTORE.md`](../docs/MODELO_FIRESTORE.md) y las reglas de seguridad en [`firestore.rules`](firestore.rules).
Todo cabe en el plan gratuito **Spark** (no se usa Firebase Storage).

## 1. Crear el proyecto y conectar la app

1. En <https://console.firebase.google.com> crea un proyecto.
2. Agrega una app **Android** con el nombre de paquete `com.gepsac.app`.
3. Descarga `google-services.json` y colócalo en `app/google-services.json`. Sin ese archivo la app no compila.
4. Sincroniza Gradle en Android Studio.

## 2. Activar los servicios

- **Authentication → Método de acceso →** activa *Correo electrónico/contraseña*.
- **Firestore Database →** crear base de datos en modo *producción* (la ubicación no se puede cambiar después).
- **Firestore → Reglas →** pega el contenido de `firestore.rules` y publica.

## 3. Datos iniciales (a mano, una sola vez)

1. **Crear el administrador.** En *Authentication → Usuarios* agrega un usuario con su correo y una contraseña propia.
   Copia su **UID**.
2. En *Firestore* crea el documento `usuarios/<UID del admin>` con estos campos:

   | Campo | Tipo | Valor |
   |---|---|---|
   | nombre | string | Administrador GEPSAC |
   | email | string | el mismo correo del paso anterior |
   | telefono | string | 9 dígitos, empezando con 9 (ej. 999000111) |
   | rol | string | `admin` |

3. Crea el documento `contadores/pedidos` con un campo `ultimo` (number) = `100000`. De ahí salen los números de pedido.
4. Entra a la app con el admin y crea los productos desde la pestaña *Productos*.

Los clientes se registran solos desde la pantalla de login (siempre quedan con rol `cliente`).

## Notas

- Las reglas no pueden comprobar que los precios de un pedido coincidan con los del catálogo. Para una tienda real
  esa validación debe hacerse en una Cloud Function (plan Blaze).
- Cada vez que se evalúa una regla de administrador se lee el documento del usuario (cuenta como 1 lectura).
- Prueba cambios en las reglas con el *Simulador de reglas* de la consola antes de publicarlas.
