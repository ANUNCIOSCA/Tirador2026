// Repórtalo 24 — servidor Fase 1
// Sirve el buscador + el generador, lee titulares de medios VE por RSS y
// arma la placa amarillista con PLANTILLAS (sin IA todavía — eso llega en Fase 1B).
// Regla de oro: el dato es sagrado. Las plantillas NO inventan hechos: toman el
// titular real del medio, lo ponen con filo y lo atribuyen a su fuente.

const express = require('express');
const path = require('path');

const app = express();
app.use(express.json({ limit: '1mb' }));
app.use(express.static(path.join(__dirname, 'public')));

// ------- Fuentes de noticias (RSS). Se pueden agregar/quitar sin tocar nada más -------
const FUENTES = [
  { nombre: 'Efecto Cocuyo', url: 'https://efectococuyo.com/feed/' },
  { nombre: 'La Patilla',    url: 'https://www.lapatilla.com/feed/' },
  { nombre: 'Tal Cual',      url: 'https://talcualdigital.com/feed/' },
  { nombre: 'Runrun.es',     url: 'https://runrun.es/feed/' },
  { nombre: 'El Nacional',   url: 'https://www.elnacional.com/feed/' },
  { nombre: 'El Pitazo',     url: 'https://elpitazo.net/feed/' }
];

// ------- Utilidades de texto -------
function limpiar(s) {
  return (s || '')
    .replace(/<!\[CDATA\[|\]\]>/g, '')
    .replace(/<[^>]+>/g, ' ')
    .replace(/&#8217;|&#8216;|&#039;|&apos;/g, "'")
    .replace(/&#8220;|&#8221;|&quot;/g, '"')
    .replace(/&#8230;/g, '…')
    .replace(/&amp;/g, '&')
    .replace(/&nbsp;/g, ' ')
    .replace(/\s+/g, ' ')
    .trim();
}

// Parser RSS mínimo (sin dependencias): saca título, enlace, fecha y resumen de cada <item>
function parseRss(xml, fuente) {
  const items = [];
  const bloques = xml.split(/<item[\s>]/i).slice(1);
  for (const b of bloques) {
    const corte = b.indexOf('</item>');
    const seg = corte >= 0 ? b.slice(0, corte) : b;
    const g = (re) => { const m = seg.match(re); return m ? limpiar(m[1]) : ''; };
    const titulo = g(/<title[^>]*>([\s\S]*?)<\/title>/i);
    const link = g(/<link[^>]*>([\s\S]*?)<\/link>/i);
    const fecha = g(/<pubDate[^>]*>([\s\S]*?)<\/pubDate>/i);
    const desc = g(/<description[^>]*>([\s\S]*?)<\/description>/i) ||
                 g(/<content:encoded[^>]*>([\s\S]*?)<\/content:encoded>/i);
    if (titulo) items.push({ titulo, link, fecha, resumen: desc.slice(0, 320), fuente });
  }
  return items;
}

async function traerFeed(f) {
  try {
    const r = await fetch(f.url, {
      headers: { 'User-Agent': 'Reportalo24/1.0 (+https://reportalo24)' },
      signal: AbortSignal.timeout(12000)
    });
    if (!r.ok) return [];
    return parseRss(await r.text(), f.nombre);
  } catch (e) {
    return [];
  }
}

// ------- Categoría y franja (por palabras clave) -------
const CATS = [
  { cat: 'ECONOMÍA',      kw: ['dólar', 'dolar', 'bcv', 'inflación', 'inflacion', 'salario', 'bolívar', 'bolivar', 'precio', 'gasolina', 'economía', 'economia', 'sueldo', 'cesta'] },
  { cat: 'DEPORTE',       kw: ['vinotinto', 'béisbol', 'beisbol', 'fútbol', 'futbol', 'lvbp', 'grandes ligas', 'pelota', 'magallanes', 'caracas bbc', 'leones'] },
  { cat: 'INTERNACIONAL', kw: ['eeuu', 'estados unidos', 'colombia', 'onu', 'trump', 'brasil', 'españa', 'espana', 'migración', 'migracion', 'frontera', 'ocha', 'ue'] },
  { cat: 'POLÍTICA',      kw: ['maduro', 'gobierno', 'chavismo', 'oposición', 'oposicion', 'elecciones', 'asamblea', 'tsj', 'psuv', 'régimen', 'regimen', 'diosdado', 'decreto', 'gaceta', 'machado', 'gonzález urrutia'] },
  { cat: 'NACIONAL',      kw: ['caracas', 'maracaibo', 'valencia', 'apagón', 'apagon', 'hospital', 'servicios', 'agua', 'luz', 'sucesos', 'detenido'] }
];
function detectarCat(txt) {
  const t = (txt || '').toLowerCase();
  for (const c of CATS) if (c.kw.some((k) => t.includes(k))) return c.cat;
  return 'NACIONAL';
}
const FRANJAS = {
  'POLÍTICA': 'ÚLTIMA HORA',
  'ECONOMÍA': 'ALERTA ECONÓMICA',
  'DEPORTE': 'DEPORTES',
  'INTERNACIONAL': 'INTERNACIONAL',
  'NACIONAL': 'REPORTE DEL DÍA'
};

// Parte un texto en 2 líneas lo más parejas posible (para el título de la placa)
function dosLineas(s) {
  s = (s || '').trim();
  if (s.length <= 24) return s;
  const words = s.split(' ');
  const mitad = Math.ceil(s.length / 2);
  let l1 = '';
  for (const w of words) {
    if (l1 && (l1 + ' ' + w).length > mitad) break;
    l1 = l1 ? l1 + ' ' + w : w;
  }
  const l2 = s.slice(l1.length).trim();
  return l2 ? l1 + '\n' + l2 : l1;
}

// Resalta en **negrita roja** la cifra más llamativa del subtítulo (si la hay)
function resaltar(s) {
  const num = s.match(/\d[\d.,]*\s?(mil millones|millones|mil|%|dólares|dolares|bs|bolívares|bolivares|años|anos|personas|muertos|heridos|detenidos)?/i);
  if (num && num[0].trim().length > 1) return s.replace(num[0], '**' + num[0].trim() + '**');
  return s;
}

function armarHashtags(cat, txt) {
  const base = ['#Venezuela', '#NoticiasVenezuela', '#Reportalo24', '#VenezuelaHoy', '#Noticias'];
  const porCat = {
    'POLÍTICA': ['#Política', '#Maduro', '#DDHHVenezuela'],
    'ECONOMÍA': ['#Dólar', '#Economía', '#InflaciónVE'],
    'DEPORTE': ['#Deportes', '#Vinotinto', '#Béisbol'],
    'INTERNACIONAL': ['#Internacional', '#Migración', '#DiásporaVenezolana'],
    'NACIONAL': ['#Caracas', '#ServiciosVE', '#Sucesos']
  };
  return [...base, ...(porCat[cat] || [])].slice(0, 12);
}

function armarCopy(titulo, resumen, cat, fuente) {
  const gancho = resumen ? resumen.replace(/\s+/g, ' ').slice(0, 220) : titulo;
  const credito = fuente ? `\n\n📰 Vía ${fuente}` : '';
  return `🚨 ${titulo}\n\n${gancho}${credito}\n\n¿Qué opinas? Déjalo en los comentarios 👇\n📲 Sigue a @reportalo24 para más\n\n${armarHashtags(cat, titulo + ' ' + resumen).join(' ')}`;
}

// ------- API -------

// Buscar titulares reales por RSS (opcional ?tema=)
app.get('/api/noticias', async (req, res) => {
  const tema = (req.query.tema || '').toLowerCase().trim();
  const listas = await Promise.all(FUENTES.map(traerFeed));
  let items = listas.flat();
  if (tema) items = items.filter((i) => (i.titulo + ' ' + i.resumen).toLowerCase().includes(tema));
  items.sort((a, b) => new Date(b.fecha || 0) - new Date(a.fecha || 0));
  items = items.slice(0, 30);
  res.json({
    ok: items.length > 0,
    total: items.length,
    items,
    nota: items.length ? undefined : 'No se pudieron leer titulares ahora (o ninguno coincide con el tema).'
  });
});

// Armar la placa amarillista a partir de un titular (PLANTILLAS, sin IA todavía)
app.post('/api/redactar', (req, res) => {
  const titulo = (req.body.titulo || '').trim();
  const resumen = (req.body.resumen || '').trim();
  const fuente = (req.body.fuente || '').trim();
  if (!titulo) return res.status(400).json({ ok: false, error: 'Falta el título' });

  const cat = req.body.categoria || detectarCat(titulo + ' ' + resumen);
  const franja = FRANJAS[cat] || 'ÚLTIMA HORA';
  const tituloPlaca = dosLineas(titulo.toUpperCase());
  let sub = (resumen || titulo).replace(/\s+/g, ' ').trim();
  if (sub.length > 175) sub = sub.slice(0, 172).replace(/\s+\S*$/, '') + '…';
  sub = resaltar(sub);

  res.json({
    ok: true,
    franja,
    titulo: tituloPlaca,
    subtitulo: sub,
    categoria: cat,
    copy: armarCopy(titulo, resumen, cat, fuente),
    hashtags: armarHashtags(cat, titulo + ' ' + resumen)
  });
});

// Sugerencias de titulares amarillistas mientras se escribe un tema (autocompletar)
app.get('/api/sugerencias', (req, res) => {
  const tema = (req.query.tema || '').trim();
  if (!tema) return res.json({ ok: true, sugerencias: [] });
  const T = tema.toUpperCase();
  res.json({
    ok: true,
    sugerencias: [
      `CONFIRMADO: lo último sobre ${T}`,
      `LO QUE NO TE CONTARON sobre ${T}`,
      `ALERTA por ${T} en Venezuela`,
      `La verdad sobre ${T} que no quieren que sepas`,
      `${T}: así golpea tu bolsillo`
    ]
  });
});

app.get('/api/salud', (req, res) => res.json({ ok: true, servicio: 'reportalo24', fuentes: FUENTES.length }));

const PORT = process.env.PORT || 3000;
if (require.main === module) {
  app.listen(PORT, () => console.log('Repórtalo 24 escuchando en http://localhost:' + PORT));
}

// Exporta utilidades para pruebas (no afecta el despliegue)
module.exports = { app, parseRss, detectarCat, dosLineas, resaltar, armarHashtags, armarCopy };
