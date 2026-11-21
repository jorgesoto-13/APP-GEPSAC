// Saca de Firebase el modelo físico REAL de GEPSAC y lo guarda en JSON.
//
// Lee las colecciones usuarios, productos, pedidos y contadores y genera docs/export_firestore.json con:
//   - cuántos documentos tiene cada colección,
//   - cada campo con su tipo y en cuántos documentos aparece (así se ve cuáles son obligatorios),
//   - un documento de ejemplo por colección.
//
// Privacidad: por defecto los datos personales (nombre, correo, teléfono, dirección) salen ENMASCARADOS y las
// imágenes en Base64 se reemplazan por su tamaño. Así el archivo se puede entregar sin exponer a nadie.
// Para ver los datos tal cual: node exportar-modelo.js --sin-enmascarar
//
// Necesita la clave de la cuenta de servicio en ./serviceAccountKey.json (la misma del seed; nunca se sube a git).
// Es de SOLO LECTURA: no modifica nada en Firebase.

const fs = require('fs');
const path = require('path');
const admin = require('firebase-admin');

const RUTA_CLAVE = path.join(__dirname, 'serviceAccountKey.json');
if (!fs.existsSync(RUTA_CLAVE)) {
  console.error('Falta firebase/seed/serviceAccountKey.json (consola de Firebase > Configuración del proyecto > Cuentas de servicio > Generar nueva clave privada).');
  process.exit(1);
}
const ENMASCARAR = !process.argv.includes('--sin-enmascarar');
const COLECCIONES = ['usuarios', 'productos', 'pedidos', 'contadores'];
const CAMPOS_PERSONALES = ['nombre', 'email', 'telefono', 'direccion', 'clienteNombre', 'clienteEmail', 'direcciones', 'observacion'];

admin.initializeApp({ credential: admin.credential.cert(require(RUTA_CLAVE)) });
const db = admin.firestore();

function tipoDe(v) {
  if (v === null || v === undefined) return 'null';
  if (v instanceof admin.firestore.Timestamp) return 'timestamp';
  if (v instanceof admin.firestore.GeoPoint) return 'geopoint';
  if (v instanceof admin.firestore.DocumentReference) return 'reference';
  if (Array.isArray(v)) return 'array';
  if (typeof v === 'object') return 'map';
  if (typeof v === 'number') return Number.isInteger(v) ? 'number (entero)' : 'number';
  return typeof v; // string, boolean
}

// Recorre un valor y anota cada campo (con su ruta) en `campos`.
function anotar(valor, ruta, campos) {
  const t = tipoDe(valor);
  const c = (campos[ruta] ||= { tipos: new Set(), documentos: 0 });
  c.tipos.add(t);
  if (t === 'map') {
    for (const [k, v] of Object.entries(valor)) anotar(v, ruta + '.' + k, campos);
  } else if (t === 'array') {
    for (const e of valor) anotar(e, ruta + '[]', campos);
  }
}

function rutasDe(valor, ruta, acumulado) {
  const t = tipoDe(valor);
  acumulado.add(ruta);
  if (t === 'map') for (const [k, v] of Object.entries(valor)) rutasDe(v, ruta + '.' + k, acumulado);
  else if (t === 'array') for (const e of valor) rutasDe(e, ruta + '[]', acumulado);
}

function aTextoJson(v, clave) {
  const t = tipoDe(v);
  if (t === 'timestamp') return v.toDate().toISOString();
  if (t === 'array') return v.map((e) => aTextoJson(e, clave));
  if (t === 'map') return Object.fromEntries(Object.entries(v).map(([k, x]) => [k, aTextoJson(x, k)]));
  if (t === 'string') {
    if (v.length > 200) return '<texto largo (' + v.length + ' caracteres)>'; // imágenes y comprobantes en Base64
    if (ENMASCARAR && CAMPOS_PERSONALES.includes(clave)) return enmascarar(v, clave);
  }
  return v;
}

function enmascarar(v, clave) {
  if (clave === 'email' || clave === 'clienteEmail') return v.replace(/^(.).*(@.*)$/, '$1***$2');
  if (clave === 'telefono') return v.length > 4 ? v[0] + '*'.repeat(v.length - 4) + v.slice(-3) : '***';
  return v.length > 3 ? v.slice(0, 3) + '*'.repeat(Math.min(v.length - 3, 8)) : '***';
}

(async () => {
  const salida = {
    generado: new Date().toISOString(),
    proyecto: admin.app().options.credential.projectId || require(RUTA_CLAVE).project_id,
    motor: 'Cloud Firestore',
    datos_personales: ENMASCARAR ? 'enmascarados' : 'sin enmascarar',
    colecciones: {},
  };

  for (const nombre of COLECCIONES) {
    const snap = await db.collection(nombre).limit(500).get();
    const campos = {};
    snap.forEach((doc) => {
      const d = doc.data();
      const vistos = new Set();
      for (const [k, v] of Object.entries(d)) { anotar(v, k, campos); rutasDe(v, k, vistos); }
      for (const r of vistos) campos[r].documentos += 1;
    });

    const total = snap.size;
    const resumen = {};
    for (const [ruta, c] of Object.entries(campos).sort()) {
      resumen[ruta] = {
        tipos: [...c.tipos].filter((t) => t !== 'null').concat(c.tipos.has('null') ? ['null'] : []),
        aparece_en: c.documentos + ' de ' + total + ' documentos',
        obligatorio: total > 0 && c.documentos === total,
      };
    }
    const ejemplo = snap.docs[0] ? { id_documento: ENMASCARAR ? '<id>' : snap.docs[0].id, ...aTextoJson(snap.docs[0].data(), '') } : null;
    salida.colecciones[nombre] = { total_documentos: total, campos: resumen, ejemplo };
    console.log(nombre + ': ' + total + ' documentos, ' + Object.keys(resumen).length + ' campos');
  }

  const destino = path.join(__dirname, '..', '..', 'docs', 'export_firestore.json');
  fs.writeFileSync(destino, JSON.stringify(salida, null, 2) + '\n', 'utf8');
  console.log('\nListo: ' + destino);
  console.log('Recuerda borrar serviceAccountKey.json y revocarla en la consola de Firebase cuando termines.');
})().catch((e) => {
  console.error('\nError:', e.message);
  process.exit(1);
});
