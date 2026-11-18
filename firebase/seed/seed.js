// Datos iniciales de GEPSAC en Firebase. Se ejecuta UNA vez (si se repite, no duplica nada).
//
// Crea:
//   1. contadores/pedidos        { ultimo: 100000 }   (de aquí salen los números de pedido)
//   2. el usuario administrador  (en Authentication) + su documento usuarios/{uid} con rol "admin"
//   3. (opcional) 3 productos de ejemplo, si SEED_PRODUCTOS=1
//
// Necesita la clave privada de la cuenta de servicio en ./serviceAccountKey.json (ver firebase/README.md).
// Esa clave da control total del proyecto: NUNCA se sube a git ni se comparte.
// El correo y la contraseña del admin se pasan por variables de entorno, no se escriben en este archivo.

const fs = require('fs');
const path = require('path');
const admin = require('firebase-admin');

const RUTA_CLAVE = path.join(__dirname, 'serviceAccountKey.json');
if (!fs.existsSync(RUTA_CLAVE)) {
  console.error('Falta firebase/seed/serviceAccountKey.json (la clave privada de la cuenta de servicio).');
  process.exit(1);
}

const EMAIL = (process.env.ADMIN_EMAIL || '').trim().toLowerCase();
const PASSWORD = process.env.ADMIN_PASSWORD || '';
const NOMBRE = (process.env.ADMIN_NOMBRE || 'Administrador GEPSAC').trim();
const TELEFONO = (process.env.ADMIN_TELEFONO || '999000111').replace(/\s/g, '');

if (!EMAIL || !EMAIL.includes('@')) {
  console.error('Define ADMIN_EMAIL con el correo del administrador.');
  process.exit(1);
}
if (PASSWORD.length < 6) {
  console.error('Define ADMIN_PASSWORD (mínimo 6 caracteres).');
  process.exit(1);
}
if (!/^9\d{8}$/.test(TELEFONO)) {
  console.error('ADMIN_TELEFONO debe tener 9 dígitos y empezar con 9.');
  process.exit(1);
}

admin.initializeApp({ credential: admin.credential.cert(require(RUTA_CLAVE)) });
const db = admin.firestore();
const auth = admin.auth();
const ahora = () => admin.firestore.FieldValue.serverTimestamp();

async function contador() {
  const ref = db.doc('contadores/pedidos');
  if ((await ref.get()).exists) {
    console.log('= contadores/pedidos ya existe, no se toca');
    return;
  }
  await ref.set({ ultimo: 100000 });
  console.log('+ contadores/pedidos creado (ultimo = 100000)');
}

async function administrador() {
  let usuario;
  try {
    usuario = await auth.getUserByEmail(EMAIL);
    console.log('= el usuario ' + EMAIL + ' ya existe en Authentication (no se cambia su contraseña)');
  } catch (e) {
    if (e.code !== 'auth/user-not-found') throw e;
    usuario = await auth.createUser({ email: EMAIL, password: PASSWORD, displayName: NOMBRE });
    console.log('+ usuario ' + EMAIL + ' creado en Authentication');
  }
  await db.doc('usuarios/' + usuario.uid).set(
    { nombre: NOMBRE, email: EMAIL, telefono: TELEFONO, rol: 'admin', creadoEn: ahora() },
    { merge: true }
  );
  console.log('+ usuarios/' + usuario.uid + ' con rol "admin"');
}

async function productos() {
  if (process.env.SEED_PRODUCTOS !== '1') {
    console.log('- productos de ejemplo omitidos (usa SEED_PRODUCTOS=1 para crearlos)');
    return;
  }
  const col = db.collection('productos');
  if (!(await col.limit(1).get()).empty) {
    console.log('= ya hay productos, no se crean los de ejemplo');
    return;
  }
  const ejemplos = [
    { nombre: 'Café Caffe Blend', marca: 'Caffe', presentacion: '250 gr', precio: 12 },
    { nombre: 'Café Caffe Blend', marca: 'Caffe', presentacion: '500 gr', precio: 26 },
    { nombre: 'Café Kaphiy Blend', marca: 'Kaphiy', presentacion: '500 gr', precio: 27 },
  ];
  for (const p of ejemplos) {
    await col.add({ ...p, activo: true, imagen: '', actualizadoEn: ahora() });
  }
  console.log('+ ' + ejemplos.length + ' productos de ejemplo creados');
}

(async () => {
  await contador();
  await administrador();
  await productos();
  console.log('\nListo. Borra la clave privada (serviceAccountKey.json) y revócala en la consola de Firebase.');
})().catch((e) => {
  console.error('\nError:', e.message);
  process.exit(1);
});
