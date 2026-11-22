"use strict";
// App web de GOVP para el equipo (no es parte de la entrega). Habla con el backend real a través de ServidorApp:
// /kc/... va a Keycloak y /gw/... al gateway. El menú y los botones dependen de los roles del token, pero quien
// decide de verdad es el backend: si algo no corresponde, responde 401, 403 o 422 y la app lo muestra.

// ======================================================================== Constantes

const KC_TOKEN = "/kc/realms/obras-viales/protocol/openid-connect/token";
const CLIENTE = "obras-public-client";
const ROLES = ["ADMIN", "PLANIFICADOR", "CAPATAZ", "AUDITOR"];
const TIPOS_TRABAJO = ["RELEVAMIENTO", "BASE", "SUBBASE", "CARPETA_ASFALTICA", "LOSA_HORMIGON"];
const ESTADOS_ORDEN = ["PROGRAMADA", "RECURSOS_ASIGNADOS", "EVALUACION_CLIMATICA", "HABILITADA", "EN_EJECUCION",
  "COMPLETADA", "PENDIENTE_CONFIRMACION_CLIMATICA", "SUSPENDIDA", "REPROGRAMADA", "CANCELADA"];
const USUARIOS_DEMO = ["admin", "planificador", "capataz", "auditor"];

const P = "PLANIFICADOR", C = "CAPATAZ", A = "ADMIN", U = "AUDITOR";

const MENU = [
  { grupo: null, items: [{ ruta: "inicio", etq: "Inicio", roles: ROLES }] },
  { grupo: "Obras", items: [
    { ruta: "obras", etq: "Obras, tramos y fases", roles: [P, C, U], desc: "Planificación, cronograma y materiales" }] },
  { grupo: "Operación diaria", items: [
    { ruta: "ordenes", etq: "Órdenes de trabajo", roles: [P, C, U], desc: "Programar, habilitar y ejecutar" },
    { ruta: "suspensiones", etq: "Suspensiones", roles: [P, U], desc: "Historial por fase u orden" }] },
  { grupo: "Recursos", items: [
    { ruta: "cuadrillas", etq: "Cuadrillas", roles: [A, P, C, U], desc: "Integrantes y ubicación (check-in)" },
    { ruta: "maquinas", etq: "Máquinas", roles: [A, P, C, U], desc: "Catálogo precargado" },
    { ruta: "operarios", etq: "Operarios", roles: [A, P, C, U], desc: "Personal de cada cuadrilla" }] },
  { grupo: "Configuración", items: [
    { ruta: "umbrales", etq: "Umbrales climáticos", roles: [A, P, C, U], desc: "Límites de clima por tipo de trabajo" }] }
];

// ======================================================================== Utilidades

const $ = id => document.getElementById(id);
const esc = v => String(v ?? "").replace(/[&<>"']/g, c => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" }[c]));
const hoy = (n = 0) => { const f = new Date(); f.setDate(f.getDate() + n); return f.toLocaleDateString("sv-SE"); };
const fecha = v => v ? new Date(String(v).length <= 10 ? v + "T00:00" : v).toLocaleDateString("es-AR") : "—";
const fechaHora = v => v ? new Date(v).toLocaleString("es-AR", { dateStyle: "short", timeStyle: "short" }) : "—";
const hora = v => v ? String(v).slice(0, 5) : "—";
const num = (v, dec = 2) => v === null || v === undefined ? "—" : Number(v).toLocaleString("es-AR", { maximumFractionDigits: dec });
const SIGLAS = { OSRM: "OSRM", ESTIMADA: "Estimada (línea recta)" };
const legible = s => !s ? "—" : SIGLAS[s] ?? String(s).replaceAll("_", " ").toLowerCase().replace(/^./, c => c.toUpperCase());

function pildora(valor) {
  const v = String(valor ?? "");
  const clase =
    ["HABILITADA", "COMPLETADA", "CONFIRMADA", "DISPONIBLE", "ACTIVO", "OSRM"].includes(v) ? "p-ok" :
    ["PENDIENTE_CONFIRMACION_CLIMATICA", "PROVISORIA", "EN_MANTENIMIENTO", "EN_RIESGO", "ESTIMADA", "RECURSOS_ASIGNADOS",
      "EVALUACION_CLIMATICA"].includes(v) ? "p-alerta" :
    ["SUSPENDIDA", "CANCELADA", "FUERA_DE_SERVICIO", "EXPIRADA", "INACTIVO"].includes(v) ? "p-mal" :
    ["EN_EJECUCION", "EN_CURSO", "PROGRAMADA"].includes(v) ? "p-info" : "";
  return `<span class="pildora ${clase}">${esc(legible(v))}</span>`;
}

function avance(p) {
  const v = Math.max(0, Math.min(100, Number(p) || 0));
  return `<span class="barra-avance"><span style="width:${v}%"></span></span>${v} %`;
}

function aviso(texto, mal = false) {
  const el = document.createElement("div");
  el.className = "aviso" + (mal ? " mal" : "");
  el.textContent = texto;
  $("avisos").appendChild(el);
  setTimeout(() => el.remove(), mal ? 9000 : 4500);
}

// ======================================================================== Sesión (Keycloak)

let sesion = null;

function decodificar(jwt) {
  const base = jwt.split(".")[1].replace(/-/g, "+").replace(/_/g, "/");
  const json = decodeURIComponent(atob(base).split("").map(c => "%" + c.charCodeAt(0).toString(16).padStart(2, "0")).join(""));
  return JSON.parse(json);
}

function guardarSesion(r) {
  const datos = decodificar(r.access_token);
  sesion = {
    access: r.access_token,
    refresh: r.refresh_token,
    vence: Date.now() + r.expires_in * 1000,
    usuario: datos.preferred_username,
    nombre: datos.name || datos.preferred_username,
    roles: (datos.realm_access?.roles || []).filter(x => ROLES.includes(x))
  };
  try { sessionStorage.setItem("govp-sesion", JSON.stringify(sesion)); } catch (e) { /* sin almacenamiento */ }
}

async function pedirToken(datos) {
  const res = await fetch(KC_TOKEN, { method: "POST", body: new URLSearchParams({ client_id: CLIENTE, ...datos }) });
  const j = await res.json().catch(() => ({}));
  if (!res.ok) {
    throw new Error(j.error === "invalid_grant" ? "Usuario o contraseña incorrectos."
      : j.detail || j.error_description || `Keycloak respondió ${res.status}.`);
  }
  return j;
}

async function ingresar(usuario, clave) {
  guardarSesion(await pedirToken({ grant_type: "password", username: usuario, password: clave }));
}

async function renovar() {
  guardarSesion(await pedirToken({ grant_type: "refresh_token", refresh_token: sesion.refresh }));
}

async function tokenVigente() {
  if (!sesion) throw new Error("Sin sesión");
  if (sesion.vence - Date.now() < 30000) await renovar();
  return sesion.access;
}

function salir(mensaje) {
  sesion = null;
  try { sessionStorage.removeItem("govp-sesion"); } catch (e) { /* nada */ }
  mostrarLogin(mensaje);
}

const tiene = (...roles) => !!sesion && roles.some(r => sesion.roles.includes(r));

// ======================================================================== Cliente de la API

class ErrorApi extends Error {
  constructor(status, cuerpo) {
    super(describirError(status, cuerpo));
    this.status = status;
    this.cuerpo = cuerpo;
  }
}

function describirError(status, c) {
  if (status === 0) return "No hay conexión con el servidor de la app.";
  if (!c || typeof c !== "object") return `Error ${status}.`;
  const partes = [];
  partes.push(c.title ? `${c.title} (${status})` : `Error ${status}`);
  if (c.detail) partes.push(c.detail);
  if (c.codigoRegla) partes.push(`Regla: ${c.codigoRegla}`);
  if (c.errores) Object.entries(c.errores).forEach(([campo, msg]) => partes.push(`· ${campo}: ${msg}`));
  if (c.correlationId) partes.push(`Correlation-id: ${c.correlationId}`);
  return partes.join("\n");
}

async function api(metodo, ruta, cuerpo, reintento = true) {
  let token;
  try { token = await tokenVigente(); } catch (e) { salir("La sesión venció. Volvé a ingresar."); throw e; }
  const headers = { Authorization: "Bearer " + token };
  if (cuerpo !== undefined) headers["Content-Type"] = "application/json";
  let res;
  try {
    res = await fetch("/gw" + ruta, { method: metodo, headers, body: cuerpo !== undefined ? JSON.stringify(cuerpo) : undefined });
  } catch (e) {
    throw new ErrorApi(0, null);
  }
  if (res.status === 401 && reintento) {
    try { await renovar(); } catch (e) { salir("La sesión venció. Volvé a ingresar."); throw e; }
    return api(metodo, ruta, cuerpo, false);
  }
  const texto = await res.text();
  let datos = null;
  try { datos = texto ? JSON.parse(texto) : null; } catch (e) { datos = texto; }
  if (!res.ok) throw new ErrorApi(res.status, datos);
  return datos;
}

const GET = r => api("GET", r);
const qs = obj => { const p = new URLSearchParams(); Object.entries(obj).forEach(([k, v]) => { if (v !== "" && v != null) p.set(k, v); }); const s = p.toString(); return s ? "?" + s : ""; };

// ======================================================================== Tablas y datos

function tabla(columnas, filas, opciones = {}) {
  if (!filas || filas.length === 0) return `<div class="tabla"><div class="vacio">${esc(opciones.vacio || "No hay datos.")}</div></div>`;
  const cab = columnas.map(c => `<th${c.num ? ' style="text-align:right"' : ""}>${esc(c.t)}</th>`).join("");
  const cuerpo = filas.map((f, i) => {
    const attrs = opciones.enlace ? ` class="click" data-ir="${esc(opciones.enlace(f))}"` : "";
    return `<tr${attrs} data-i="${i}">${columnas.map(c => `<td${c.num ? ' class="num"' : ""}>${c.v(f) ?? "—"}</td>`).join("")}</tr>`;
  }).join("");
  return `<div class="tabla"><table><thead><tr>${cab}</tr></thead><tbody>${cuerpo}</tbody></table></div>`;
}

function datos(pares) {
  return `<dl class="datos">${pares.map(([k, v]) => `<dt>${esc(k)}</dt><dd>${v ?? "—"}</dd>`).join("")}</dl>`;
}

function cabecera(titulo, sub, botones = "") {
  return `<div class="cabecera"><div class="titulos"><h1>${esc(titulo)}</h1>${sub ? `<p class="sub">${sub}</p>` : ""}</div>
    <div class="botonera">${botones}</div></div>`;
}

const boton = (accion, texto, clase = "") => `<button class="boton ${clase}" data-accion="${esc(accion)}">${esc(texto)}</button>`;

// Conecta los botones con data-accion de la vista a sus funciones, y las filas con data-ir a su ruta.
function conectar(acciones) {
  const cont = $("contenido");
  cont.querySelectorAll("[data-accion]").forEach(b => {
    const [nombre, arg] = b.dataset.accion.split(":");
    if (acciones[nombre]) b.addEventListener("click", ev => { ev.stopPropagation(); acciones[nombre](arg); });
  });
  cont.querySelectorAll("tr[data-ir]").forEach(tr => tr.addEventListener("click", () => { location.hash = tr.dataset.ir; }));
}

// ======================================================================== Formularios (diálogo)

let enviarDialogo = null;

function formulario({ titulo, ayuda = "", campos = [], boton: textoBoton = "Guardar", enviar, html = "", soloLectura = false }) {
  $("dialogo-titulo").textContent = titulo;
  $("dialogo-ayuda").innerHTML = ayuda;
  $("dialogo-error").hidden = true;
  $("dialogo-aceptar").textContent = textoBoton;
  $("dialogo-aceptar").hidden = soloLectura;
  $("dialogo-cancelar").textContent = soloLectura ? "Cerrar" : "Cancelar";
  $("dialogo-campos").innerHTML = html + campos.map(campoHtml).join("");
  enviarDialogo = enviar ? async () => enviar(leerCampos(campos)) : null;
  $("dialogo").showModal();
  const primero = $("dialogo-campos").querySelector("input, select, textarea");
  if (primero) primero.focus();
}

function mostrarInfo(titulo, html) {
  formulario({ titulo, html, soloLectura: true });
}

function campoHtml(c) {
  const id = "c-" + c.n;
  const req = c.req ? " required" : "";
  const ayuda = c.ayuda ? `<div class="nota">${esc(c.ayuda)}</div>` : "";
  const valor = c.valor ?? "";
  if (c.tipo === "select") {
    const opts = (c.opciones || []).map(o => {
      const [v, t] = Array.isArray(o) ? o : [o, legible(o)];
      return `<option value="${esc(v)}"${String(v) === String(valor) ? " selected" : ""}>${esc(t)}</option>`;
    }).join("");
    return `<label for="${id}">${esc(c.etq)}</label><select id="${id}" name="${c.n}"${req}>${c.vacia ? `<option value="">${esc(c.vacia)}</option>` : ""}${opts}</select>${ayuda}`;
  }
  if (c.tipo === "checks") {
    const items = (c.opciones || []).map(o => `<label class="check"><input type="checkbox" name="${c.n}" value="${esc(o.valor)}"${o.marcado ? " checked" : ""}>${esc(o.etiqueta)}</label>`).join("");
    return `<label>${esc(c.etq)}</label><div class="lista-checks">${items || '<span class="nota">No hay opciones.</span>'}</div>${ayuda}`;
  }
  if (c.tipo === "textarea") return `<label for="${id}">${esc(c.etq)}</label><textarea id="${id}" name="${c.n}"${req}>${esc(valor)}</textarea>${ayuda}`;
  if (c.tipo === "booleano") return `<label class="check"><input type="checkbox" id="${id}" name="${c.n}"${valor ? " checked" : ""}>${esc(c.etq)}</label>${ayuda}`;
  const extra = [c.min !== undefined ? ` min="${c.min}"` : "", c.max !== undefined ? ` max="${c.max}"` : "", c.paso ? ` step="${c.paso}"` : ""].join("");
  return `<label for="${id}">${esc(c.etq)}</label><input id="${id}" name="${c.n}" type="${c.tipo || "text"}" value="${esc(valor)}"${req}${extra}>${ayuda}`;
}

function leerCampos(campos) {
  const raiz = $("dialogo-campos");
  const v = {};
  for (const c of campos) {
    if (c.tipo === "checks") {
      v[c.n] = [...raiz.querySelectorAll(`input[name="${c.n}"]:checked`)].map(i => Number(i.value));
    } else if (c.tipo === "booleano") {
      v[c.n] = raiz.querySelector(`[name="${c.n}"]`).checked;
    } else {
      const crudo = raiz.querySelector(`[name="${c.n}"]`).value.trim();
      v[c.n] = crudo === "" ? null : (c.tipo === "number" ? Number(crudo) : crudo);
    }
  }
  return v;
}

$("form-dialogo").addEventListener("submit", async ev => {
  ev.preventDefault();
  if (!enviarDialogo) { $("dialogo").close(); return; }
  const faltantes = [...$("dialogo-campos").querySelectorAll("[required]")].filter(i => !i.value.trim());
  if (faltantes.length) {
    $("dialogo-error").textContent = "Completá los campos obligatorios.";
    $("dialogo-error").hidden = false;
    faltantes[0].focus();
    return;
  }
  const b = $("dialogo-aceptar");
  b.disabled = true;
  try {
    const mensaje = await enviarDialogo();
    if ($("dialogo").open && enviarDialogo) $("dialogo").close();
    if (mensaje) aviso(mensaje);
    render();
  } catch (e) {
    $("dialogo-error").textContent = e.message;
    $("dialogo-error").hidden = false;
  } finally {
    b.disabled = false;
  }
});
$("dialogo-cancelar").addEventListener("click", () => $("dialogo").close());

// Ejecuta una acción directa (sin formulario) mostrando el resultado o el error.
async function accionDirecta(promesa, mensaje) {
  try {
    const r = await promesa;
    aviso(typeof mensaje === "function" ? mensaje(r) : mensaje);
    render();
    return r;
  } catch (e) {
    aviso(e.message, true);
    return null;
  }
}

// ======================================================================== Vistas

const VISTAS = {};

VISTAS.inicio = async () => {
  const atajos = MENU.flatMap(g => g.items).filter(i => i.desc && tiene(...i.roles));
  return {
    html: cabecera(`Hola, ${sesion.nombre}`, `Roles: ${sesion.roles.map(legible).join(", ") || "ninguno"}`) +
      `<div class="atajos">${atajos.map(i => `<a class="atajo" href="#${i.ruta}"><b>${esc(i.etq)}</b><span>${esc(i.desc)}</span></a>`).join("")}</div>
      <p class="nota" style="margin-top:18px">Herramienta interna del equipo. Lo que no te deja hacer tu rol lo rechaza el backend con 403.</p>`
  };
};

// ---------------------------------------------------------------- Obras

VISTAS.obras = async () => {
  const obras = await GET("/api/obras");
  return {
    html: cabecera("Obras", "Planificación de obras, tramos y fases (RN-01 a RN-03)",
      tiene(P) ? boton("nueva", "Nueva obra", "primario") : "") +
      tabla([
        { t: "Nombre", v: o => `<b>${esc(o.nombre)}</b>` },
        { t: "Estado", v: o => pildora(o.estado) },
        { t: "Avance", v: o => avance(o.avance) },
        { t: "Descripción", v: o => esc(o.descripcion) }
      ], obras, { enlace: o => "obra/" + o.id, vacio: "Todavía no hay obras." }),
    acciones: {
      nueva: () => formulario({
        titulo: "Nueva obra", campos: camposObra(),
        enviar: async v => { const o = await api("POST", "/api/obras", v); location.hash = "obra/" + o.id; return "Obra creada."; }
      })
    }
  };
};

const camposObra = () => [
  { n: "nombre", etq: "Nombre", req: true },
  { n: "descripcion", etq: "Descripción", tipo: "textarea" }
];

const camposTramo = () => [
  { n: "nombre", etq: "Nombre", req: true },
  { n: "superficieM2", etq: "Superficie (m²)", tipo: "number", min: 0, paso: "any", req: true, ayuda: "Con la superficie se calculan los materiales (RN-03)." },
  { n: "latitud", etq: "Latitud", tipo: "number", min: -90, max: 90, paso: "any", req: true },
  { n: "longitud", etq: "Longitud", tipo: "number", min: -180, max: 180, paso: "any", req: true }
];

VISTAS.obra = async id => {
  const [obra, crono] = await Promise.all([GET(`/api/obras/${id}`), GET(`/api/obras/${id}/cronograma`)]);
  const todas = obra.tramos.flatMap(t => t.fases);
  const nombreFase = fid => { const f = todas.find(x => x.id === fid); return f ? `#${f.id} ${legible(f.tipo)}` : (fid ? "#" + fid : "—"); };

  const tramosHtml = obra.tramos.map(({ tramo, fases }) => `
    <div class="tarjeta">
      <div class="cabecera"><div class="titulos"><h2>${esc(tramo.nombre)}</h2>
        <p class="sub">${num(tramo.superficieM2)} m² · ${num(tramo.latitud, 4)}, ${num(tramo.longitud, 4)}</p></div>
        <div class="botonera">${tiene(P) ? boton("nuevaFase:" + tramo.id, "Nueva fase", "primario") : ""}</div></div>
      ${tabla([
        { t: "Fase", v: f => `#${f.id} <b>${esc(legible(f.tipo))}</b>` },
        { t: "Estado", v: f => pildora(f.estado) },
        { t: "Inicio", v: f => fecha(f.fechaInicioPlanificada) },
        { t: "Fin", v: f => fecha(f.fechaFinPlanificada) },
        { t: "Fase previa", v: f => esc(nombreFase(f.faseAnteriorId)) },
        { t: "Avance", v: f => avance(f.avance) },
        { t: "", v: f => `<div class="botonera">
            ${tiene(P, C) ? boton("materiales:" + f.id, "Materiales", "chico") : ""}
            ${tiene(P) ? boton("reprogramar:" + f.id, "Reprogramar", "chico") : ""}</div>` }
      ], fases, { vacio: "Este tramo no tiene fases." })}
    </div>`).join("");

  const tramoDe = tid => obra.tramos.find(t => t.tramo.id === Number(tid));
  return {
    html: `<p class="nota"><a href="#obras">← Obras</a></p>` +
      cabecera(obra.nombre, `${pildora(obra.estado)} ${avance(obra.avance)}`,
        tiene(P) ? boton("nuevoTramo", "Agregar tramo", "primario") : "") +
      (obra.descripcion ? `<p>${esc(obra.descripcion)}</p>` : "") +
      tramosHtml +
      `<div class="tarjeta"><h2>Cronograma</h2><p class="nota">Todas las fases de la obra con su dependencia. Correr una fase corre en cascada las que dependen de ella (RN-02).</p>
       ${tabla([
         { t: "Fase", v: f => `#${f.id} ${esc(legible(f.tipo))}` },
         { t: "Tramo", v: f => esc(tramoDe(f.tramoId)?.tramo.nombre ?? f.tramoId) },
         { t: "Inicio", v: f => fecha(f.fechaInicioPlanificada) },
         { t: "Fin", v: f => fecha(f.fechaFinPlanificada) },
         { t: "Depende de", v: f => esc(nombreFase(f.faseAnteriorId)) },
         { t: "Estado", v: f => pildora(f.estado) }
       ], crono)}</div>`,
    acciones: {
      nuevoTramo: () => formulario({ titulo: "Agregar tramo", campos: camposTramo(),
        enviar: async v => { await api("POST", `/api/obras/${id}/tramos`, v); return "Tramo agregado."; } }),
      nuevaFase: tid => formulario({
        titulo: "Nueva fase", ayuda: "Una fase no puede iniciar si la previa no está completa (RN-01). No se aceptan dependencias circulares.",
        campos: [
          { n: "tipo", etq: "Tipo de trabajo", tipo: "select", opciones: TIPOS_TRABAJO, req: true },
          { n: "fechaInicioPlanificada", etq: "Inicio planificado", tipo: "date", req: true },
          { n: "fechaFinPlanificada", etq: "Fin planificado", tipo: "date", req: true },
          { n: "faseAnteriorId", etq: "Fase previa", tipo: "select", vacia: "Sin fase previa",
            opciones: todas.map(f => [f.id, `#${f.id} ${legible(f.tipo)}`]) }
        ],
        enviar: async v => {
          await api("POST", `/api/obras/tramos/${tid}/fases`, { ...v, faseAnteriorId: v.faseAnteriorId ? Number(v.faseAnteriorId) : null });
          return "Fase creada.";
        }
      }),
      reprogramar: fid => formulario({
        titulo: `Reprogramar fase #${fid}`, ayuda: "Las fases que dependen de esta se corren en cascada (RN-02). Una fase en riesgo (RN-12) vuelve a pendiente: después creá la orden nueva.",
        campos: [{ n: "nuevaFechaInicio", etq: "Nueva fecha de inicio", tipo: "date", req: true }],
        enviar: async v => { await api("PATCH", `/api/obras/fases/${fid}/programacion`, v); return "Fase reprogramada."; }
      }),
      materiales: async fid => {
        try {
          const m = await GET(`/api/obras/fases/${fid}/materiales`);
          mostrarInfo(`Materiales de la fase #${fid}`, `<p class="ayuda">Calculados con la superficie del tramo y el rendimiento del tipo de trabajo (RN-03).</p>` +
            tabla([{ t: "Material", v: x => esc(x.material) }, { t: "Cantidad", v: x => num(x.cantidad), num: true }, { t: "Unidad", v: x => esc(x.unidad) }], m));
        } catch (e) { aviso(e.message, true); }
      }
    }
  };
};

// ---------------------------------------------------------------- Órdenes

async function opcionesDeFases() {
  const obras = await GET("/api/obras");
  const detalles = await Promise.all(obras.map(o => GET(`/api/obras/${o.id}`)));
  return detalles.flatMap(o => o.tramos.flatMap(({ tramo, fases }) =>
    fases.map(f => [`${f.id}|${tramo.id}`, `${o.nombre} · ${tramo.nombre} · #${f.id} ${legible(f.tipo)} (${legible(f.estado)})`])));
}

let filtroOrdenes = { fecha: "", estado: "", obraId: "" };

VISTAS.ordenes = async () => {
  const [ordenes, obras] = await Promise.all([GET("/api/operaciones/ordenes" + qs(filtroOrdenes)), GET("/api/obras").catch(() => [])]);
  const botones = (tiene(P) ? boton("nueva", "Nueva orden") : "") + (tiene(P) ? boton("habilitarDia", "Habilitar un día", "primario") : "");
  return {
    html: cabecera("Órdenes de trabajo", "Del día programado a la ejecución, con decisión climática (RN-09 a RN-12)", botones) +
      `<div class="filtros">
        <div><label for="f-fecha">Fecha</label><input type="date" id="f-fecha" value="${esc(filtroOrdenes.fecha)}"></div>
        <div><label for="f-estado">Estado</label><select id="f-estado"><option value="">Todos</option>
          ${ESTADOS_ORDEN.map(e => `<option value="${e}"${filtroOrdenes.estado === e ? " selected" : ""}>${esc(legible(e))}</option>`).join("")}</select></div>
        <div><label for="f-obra">Obra</label><select id="f-obra"><option value="">Todas</option>
          ${obras.map(o => `<option value="${o.id}"${String(filtroOrdenes.obraId) === String(o.id) ? " selected" : ""}>${esc(o.nombre)}</option>`).join("")}</select></div>
        <div style="min-width:0">${boton("filtrar", "Filtrar")} ${boton("limpiar", "Limpiar")}</div>
      </div>` +
      tabla([
        { t: "Orden", v: o => `<b>#${o.id}</b>` },
        { t: "Fecha", v: o => fecha(o.fechaProgramada) },
        { t: "Estado", v: o => pildora(o.estado) },
        { t: "Fase", v: o => "#" + o.faseId },
        { t: "Tramo", v: o => "#" + o.tramoId },
        { t: "Reserva", v: o => o.reservaId ? "#" + o.reservaId : "—" },
        { t: "Salida del camión", v: o => o.horaSalidaCamion ? fechaHora(o.horaSalidaCamion) : "—" },
        { t: "Suspensiones", v: o => num(o.contadorSuspensiones, 0), num: true }
      ], ordenes, { enlace: o => "orden/" + o.id, vacio: "No hay órdenes con esos filtros." }),
    acciones: {
      filtrar: () => { filtroOrdenes = { fecha: $("f-fecha").value, estado: $("f-estado").value, obraId: $("f-obra").value }; render(); },
      limpiar: () => { filtroOrdenes = { fecha: "", estado: "", obraId: "" }; render(); },
      nueva: async () => {
        try {
          const opciones = await opcionesDeFases();
          formulario({
            titulo: "Nueva orden de trabajo", ayuda: "Queda PROGRAMADA. Se habilita el día de la fecha (job diario o botón Habilitar).",
            campos: [
              { n: "fase", etq: "Fase", tipo: "select", opciones, req: true },
              { n: "fechaProgramada", etq: "Fecha", tipo: "date", valor: hoy(1), req: true }
            ],
            enviar: async v => {
              const [faseId, tramoId] = v.fase.split("|").map(Number);
              const o = await api("POST", "/api/operaciones/ordenes", { faseId, tramoId, fechaProgramada: v.fechaProgramada });
              location.hash = "orden/" + o.id;
              return `Orden #${o.id} programada.`;
            }
          });
        } catch (e) { aviso(e.message, true); }
      },
      habilitarDia: () => formulario({
        titulo: "Habilitar las órdenes de un día", boton: "Habilitar",
        ayuda: "Para cada orden PROGRAMADA de la fecha: contexto de Obras, reserva en Recursos (con OSRM) y clima de 08:00 a 13:00.",
        campos: [{ n: "fecha", etq: "Fecha", tipo: "date", valor: hoy(), req: true }],
        enviar: async v => {
          const r = await api("POST", "/api/operaciones/habilitaciones-diarias", v);
          setTimeout(() => mostrarInfo(`Resultado del ${fecha(v.fecha)}`, tabla([
            { t: "Orden", v: x => `<a href="#orden/${x.ordenId}">#${x.ordenId}</a>` },
            { t: "Estado", v: x => pildora(x.estado) },
            { t: "Motivo", v: x => esc(x.motivo ?? "") }
          ], r, { vacio: "No había órdenes para habilitar en esa fecha." })), 50);
          return `Habilitación del ${fecha(v.fecha)}: ${r.length} orden(es) procesada(s).`;
        }
      })
    }
  };
};

VISTAS.orden = async id => {
  const o = await GET(`/api/operaciones/ordenes/${id}`);
  const evaluaciones = await GET(`/api/operaciones/ordenes/${id}/evaluaciones-climaticas`).catch(() => []);
  const e = o.estado;
  const b = [];
  if (tiene(P) && ["PROGRAMADA", "RECURSOS_ASIGNADOS"].includes(e)) b.push(boton("habilitar", "Habilitar ahora", "primario"));
  if (tiene(C) && e === "PENDIENTE_CONFIRMACION_CLIMATICA") b.push(boton("resolver", "Resolver clima", "primario"));
  if (tiene(C) && e === "HABILITADA") b.push(boton("iniciar", "Iniciar", "primario"), boton("rechazar", "Rechazar", "peligro"));
  if (tiene(C) && e === "EN_EJECUCION") b.push(boton("avance", "Registrar avance", "primario"));

  return {
    html: `<p class="nota"><a href="#ordenes">← Órdenes</a></p>` +
      cabecera(`Orden #${o.id}`, `${pildora(e)} · ${fecha(o.fechaProgramada)}`, b.join("")) +
      `<div class="rejilla">
        <div class="tarjeta"><h2>Orden</h2>${datos([
          ["Estado", pildora(e)],
          ["Fecha programada", fecha(o.fechaProgramada)],
          ["Fase", "#" + o.faseId],
          ["Tramo", "#" + o.tramoId],
          ["Reserva de recursos", o.reservaId ? "#" + o.reservaId : "Todavía no tiene"],
          ["Salida del camión", o.horaSalidaCamion ? fechaHora(o.horaSalidaCamion) : "—"],
          ["Suspensiones", num(o.contadorSuspensiones, 0)]
        ])}</div>
      </div>
      <div class="tarjeta"><h2>Evaluaciones climáticas</h2>
        <p class="nota">Peor valor de la ventana de 08:00 a 13:00 de la fecha de la orden (RN-09). Sin dato confiable, la orden queda pendiente y decide el CAPATAZ (RN-10).</p>
        ${tabla([
          { t: "Fecha", v: x => fechaHora(x.fechaEvaluacion) },
          { t: "Humedad del suelo (m³/m³)", v: x => num(x.humedadSueloRegistrada, 3), num: true },
          { t: "Prob. de lluvia (%)", v: x => num(x.probPrecipitacionRegistrada, 0), num: true },
          { t: "Temperatura (°C)", v: x => num(x.temperaturaRegistrada, 1), num: true },
          { t: "Resultado", v: x => x.umbralesCumplidos ? '<span class="pildora p-ok">Cumple</span>' : '<span class="pildora p-mal">No cumple</span>' }
        ], evaluaciones, { vacio: "Todavía no se evaluó el clima." })}</div>`,
    acciones: {
      habilitar: () => accionDirecta(api("POST", `/api/operaciones/ordenes/${id}/habilitacion`), r => `Orden #${id}: ${legible(r?.estado)}.`),
      iniciar: () => accionDirecta(api("PATCH", `/api/operaciones/ordenes/${id}/inicio`), "Orden en ejecución."),
      resolver: () => formulario({
        titulo: "Resolver clima incierto", ayuda: "Open-Meteo no dio un dato confiable. Decidí con lo que ves en el lugar (RN-10).",
        campos: [
          { n: "decision", etq: "Decisión", tipo: "select", opciones: [["HABILITAR", "Habilitar"], ["SUSPENDER", "Suspender"]], req: true },
          { n: "motivo", etq: "Motivo", tipo: "textarea" }
        ],
        enviar: async v => { const r = await api("PATCH", `/api/operaciones/ordenes/${id}/resolucion-climatica`, v); return `Orden #${id}: ${legible(r?.estado)}.`; }
      }),
      rechazar: () => formulario({
        titulo: "Rechazar la orden", boton: "Rechazar", ayuda: "Se libera la reserva, se corre la fase y se crea una orden nueva (saga de suspensión).",
        campos: [{ n: "motivo", etq: "Motivo", tipo: "textarea", req: true }],
        enviar: async v => { await api("PATCH", `/api/operaciones/ordenes/${id}/rechazo`, v); return "Orden rechazada."; }
      }),
      avance: () => formulario({
        titulo: "Registrar avance", ayuda: "Porcentaje acumulado de la fase. No puede bajar. Con el 100 % la fase y la orden quedan completadas.",
        campos: [{ n: "porcentaje", etq: "Avance (%)", tipo: "number", min: 1, max: 100, req: true }],
        enviar: async v => { const r = await api("POST", `/api/operaciones/ordenes/${id}/avances`, v); return `Avance de la fase: ${num(r?.avanceFase, 0)} %. Orden: ${legible(r?.estadoOrden)}.`; }
      })
    }
  };
};

VISTAS.suspensiones = async () => {
  const f = filtroSusp;
  const lista = await GET("/api/operaciones/suspensiones" + qs(f));
  return {
    html: cabecera("Suspensiones", "Historial de suspensiones por clima o rechazo del CAPATAZ") +
      `<div class="filtros">
        <div><label for="f-fase">Fase</label><input type="number" id="f-fase" min="1" value="${esc(f.faseId)}"></div>
        <div><label for="f-orden">Orden</label><input type="number" id="f-orden" min="1" value="${esc(f.ordenId)}"></div>
        <div style="min-width:0">${boton("filtrar", "Filtrar")} ${boton("limpiar", "Limpiar")}</div></div>` +
      tabla([
        { t: "Fecha", v: s => fechaHora(s.fecha) },
        { t: "Orden", v: s => `<a href="#orden/${s.ordenTrabajoId}">#${s.ordenTrabajoId}</a>` },
        { t: "Fase", v: s => "#" + s.faseId },
        { t: "Motivo", v: s => pildora(s.motivo) },
        { t: "Vigente", v: s => s.vigente ? "Sí" : "No" },
        { t: "Detalle", v: s => esc(s.detalle) }
      ], lista, { vacio: "No hay suspensiones." }),
    acciones: {
      filtrar: () => { filtroSusp = { faseId: $("f-fase").value, ordenId: $("f-orden").value }; render(); },
      limpiar: () => { filtroSusp = { faseId: "", ordenId: "" }; render(); }
    }
  };
};
let filtroSusp = { faseId: "", ordenId: "" };

// ---------------------------------------------------------------- Recursos

VISTAS.cuadrillas = async () => {
  const [cuadrillas, operarios, maquinas] = await Promise.all([
    GET("/api/recursos/cuadrillas"), GET("/api/recursos/operarios").catch(() => []), GET("/api/recursos/maquinas").catch(() => [])]);
  const nomOp = i => operarios.find(x => x.id === i)?.nombre ?? "#" + i;
  const codMaq = i => maquinas.find(x => x.id === i)?.codigo ?? "#" + i;
  return {
    html: cabecera("Cuadrillas", "Operarios y máquinas que trabajan juntos (precargados), con su última ubicación") +
      tabla([
        { t: "Cuadrilla", v: c => `<b>${esc(c.nombre)}</b>` },
        { t: "Ubicación", v: c => `${num(c.ultimaLatitud, 4)}, ${num(c.ultimaLongitud, 4)}` },
        { t: "Último check-in", v: c => fechaHora(c.fechaCheckin) },
        { t: "Operarios", v: c => esc(c.operarioIds.map(nomOp).join(", ")) || "—" },
        { t: "Máquinas", v: c => esc(c.maquinaIds.map(codMaq).join(", ")) || "—" },
        { t: "", v: c => tiene(C) ? boton("checkin:" + c.id, "Check-in", "chico primario") : "" }
      ], cuadrillas, { vacio: "No hay cuadrillas." }),
    acciones: {
      checkin: cid => { const c = cuadrillas.find(x => x.id === Number(cid));
        formulario({ titulo: `Check-in de ${c.nombre}`, boton: "Registrar ubicación",
          ayuda: "Solo el capataz de esta cuadrilla puede registrar su ubicación. Se usa para calcular el viaje al tramo (RN-07).",
          campos: [
            { n: "latitud", etq: "Latitud", tipo: "number", paso: "any", min: -90, max: 90, req: true, valor: c.ultimaLatitud },
            { n: "longitud", etq: "Longitud", tipo: "number", paso: "any", min: -180, max: 180, req: true, valor: c.ultimaLongitud }],
          enviar: async v => { await api("POST", `/api/recursos/cuadrillas/${cid}/ubicaciones`, v); return "Ubicación registrada."; } }); }
    }
  };
};

VISTAS.maquinas = async () => {
  const lista = await GET("/api/recursos/maquinas");
  return {
    html: cabecera("Máquinas", "Catálogo precargado (lo usa RN-06 al reservar)") +
      tabla([
        { t: "Código", v: m => `<b>${esc(m.codigo)}</b>` },
        { t: "Tipo", v: m => esc(legible(m.tipo)) },
        { t: "Estado", v: m => pildora(m.estado) },
        { t: "Horas de uso", v: m => num(m.horasUso, 0), num: true },
        { t: "Cuadrilla", v: m => m.cuadrillaId ? "#" + m.cuadrillaId : "Libre" }
      ], lista, { vacio: "No hay máquinas." })
  };
};

VISTAS.operarios = async () => {
  const lista = await GET("/api/recursos/operarios");
  return {
    html: cabecera("Operarios", "Personal precargado de cada cuadrilla (cuenta para RN-06)") +
      tabla([
        { t: "Nombre", v: x => `<b>${esc(x.nombre)}</b>` },
        { t: "Usuario", v: x => esc(x.keycloakUsername ?? "—") },
        { t: "Rol operativo", v: x => esc(legible(x.rolOperativo)) },
        { t: "Estado", v: x => pildora(x.estado) },
        { t: "Cuadrilla", v: x => x.cuadrillaId ? "#" + x.cuadrillaId : "Libre" }
      ], lista, { vacio: "No hay operarios." })
  };
};

// ---------------------------------------------------------------- Configuración

VISTAS.umbrales = async () => {
  const lista = await GET("/api/operaciones/umbrales-climaticos");
  return {
    html: cabecera("Umbrales climáticos", "Se habilita si la humedad del suelo y la lluvia no superan el máximo y la temperatura alcanza el mínimo (RN-09)") +
      tabla([
        { t: "Tipo de trabajo", v: u => `<b>${esc(legible(u.tipoTrabajo))}</b>` },
        { t: "Humedad del suelo máx. (m³/m³)", v: u => num(u.humedadSueloMaxima, 3), num: true },
        { t: "Prob. de lluvia máx. (%)", v: u => num(u.probPrecipitacionMaxima, 0), num: true },
        { t: "Temperatura mín. (°C)", v: u => num(u.temperaturaMinima, 1), num: true },
        { t: "", v: u => tiene(A) ? boton("editar:" + u.tipoTrabajo, "Editar", "chico") : "" }
      ], lista),
    acciones: {
      editar: tipo => { const u = lista.find(x => x.tipoTrabajo === tipo);
        formulario({ titulo: `Umbrales de ${legible(tipo)}`,
          ayuda: "La humedad del suelo es una fracción volumétrica: 0,40 es 40 % de agua en volumen. Bajar los valores fuerza una suspensión en la demo.",
          campos: [
            { n: "humedadSueloMaxima", etq: "Humedad del suelo máxima (m³/m³)", tipo: "number", paso: "any", min: 0, max: 1, req: true, valor: u.humedadSueloMaxima },
            { n: "probPrecipitacionMaxima", etq: "Probabilidad de lluvia máxima (%)", tipo: "number", paso: "any", min: 0, max: 100, req: true, valor: u.probPrecipitacionMaxima },
            { n: "temperaturaMinima", etq: "Temperatura mínima (°C)", tipo: "number", paso: "any", req: true, valor: u.temperaturaMinima }],
          enviar: async v => { await api("PUT", `/api/operaciones/umbrales-climaticos/${tipo}`, v); return "Umbrales actualizados."; } }); }
    }
  };
};

// ======================================================================== Router y arranque

function rutaActual() {
  const [nombre, param] = (location.hash.slice(1) || "inicio").split("/");
  return { nombre, param };
}

function rolesDeRuta(nombre) {
  const base = { obra: "obras", orden: "ordenes" }[nombre] || nombre;
  return MENU.flatMap(g => g.items).find(i => i.ruta === base)?.roles;
}

function dibujarMenu() {
  const { nombre } = rutaActual();
  const activa = { obra: "obras", orden: "ordenes" }[nombre] || nombre;
  $("menu").innerHTML = MENU.map(g => {
    const items = g.items.filter(i => tiene(...i.roles));
    if (!items.length) return "";
    return (g.grupo ? `<div class="grupo">${esc(g.grupo)}</div>` : "") +
      items.map(i => `<a href="#${i.ruta}"${i.ruta === activa ? ' class="activo" aria-current="page"' : ""}>${esc(i.etq)}</a>`).join("");
  }).join("");
}

let generacion = 0;

async function render() {
  if (!sesion) return;
  const { nombre, param } = rutaActual();
  dibujarMenu();
  $("menu").classList.remove("abierto");
  const cont = $("contenido");
  const roles = rolesDeRuta(nombre);
  if (!VISTAS[nombre] || !roles) { cont.innerHTML = cabecera("No existe esa sección", ""); return; }
  if (!tiene(...roles)) { cont.innerHTML = cabecera("Sin permiso", "Tu rol no tiene acceso a esta sección."); return; }
  const mia = ++generacion;
  if (!cont.innerHTML) cont.innerHTML = `<div class="cargando">Cargando…</div>`;
  try {
    const vista = await VISTAS[nombre](param);
    if (mia !== generacion) return;
    cont.innerHTML = vista.html;
    conectar(vista.acciones || {});
  } catch (e) {
    if (mia !== generacion) return;
    cont.innerHTML = cabecera("No se pudo cargar", "") + `<p class="error">${esc(e.message)}</p>`;
  }
}

function mostrarLogin(mensaje) {
  $("app").hidden = true;
  $("login").hidden = false;
  $("error-login").hidden = !mensaje;
  $("error-login").textContent = mensaje || "";
  $("clave").value = "";
  $("usuario").focus();
}

function mostrarApp() {
  $("login").hidden = true;
  $("app").hidden = false;
  $("usuario-actual").innerHTML = `<b>${esc(sesion.usuario)}</b> · ${esc(sesion.roles.map(legible).join(", "))}`;
  $("contenido").innerHTML = "";
  render();
  comprobarStack();
}

async function comprobarStack() {
  try {
    const r = await fetch("/gw/actuator/health");
    $("estado-stack").textContent = r.ok ? "Backend en línea" : "El gateway no responde";
  } catch (e) {
    $("estado-stack").textContent = "Sin conexión";
  }
}

$("form-login").addEventListener("submit", async ev => {
  ev.preventDefault();
  const b = $("ingresar");
  b.disabled = true;
  $("error-login").hidden = true;
  try {
    await ingresar($("usuario").value.trim(), $("clave").value);
    if (!location.hash) location.hash = "inicio";
    mostrarApp();
  } catch (e) {
    $("error-login").textContent = e.message;
    $("error-login").hidden = false;
  } finally {
    b.disabled = false;
  }
});

$("demo-lista").innerHTML = USUARIOS_DEMO.map(u => `<button type="button" class="boton chico" data-demo="${u}">${u}</button>`).join("");
$("demo-lista").querySelectorAll("[data-demo]").forEach(b => b.addEventListener("click", () => {
  $("usuario").value = b.dataset.demo;
  $("clave").value = b.dataset.demo + "123";
}));

$("salir").addEventListener("click", () => salir());
$("abrir-menu").addEventListener("click", () => $("menu").classList.toggle("abierto"));
window.addEventListener("hashchange", () => { $("contenido").innerHTML = ""; render(); });

(function arrancar() {
  try { sesion = JSON.parse(sessionStorage.getItem("govp-sesion") || "null"); } catch (e) { sesion = null; }
  if (sesion && sesion.vence > Date.now() - 1800 * 1000) mostrarApp(); else mostrarLogin();
})();
