# Repórtalo 24 — App (Fase 1)

Buscador de noticias de Venezuela + generador de placas (imagen, y luego video).

## Qué hace hoy (Fase 1)
- **Buscar noticias** reales de medios VE por RSS (Efecto Cocuyo, La Patilla, Tal Cual, Runrun.es, El Nacional, El Pitazo).
- **Armar la placa** amarillista (franja + título + subtítulo + copy + hashtags) con plantillas — sin IA todavía.
- **Generador** de imagen con franja roja, categoría, palabra clave en rojo, PNG con foto y PNG transparente.

## Estructura
- `server.js` — servidor Node/Express: sirve la web y expone `/api/noticias`, `/api/redactar`, `/api/sugerencias`.
- `public/buscador.html` — pantalla de búsqueda y armado.
- `public/generador.html` — generador de placas.

## Probar local
```bash
npm install
npm start
# abrir http://localhost:3000/buscador.html
```

## Desplegar en Render (gratis, desde el celular)
1. Entra a render.com y crea una cuenta.
2. New > Blueprint (o Web Service) y conecta este repositorio.
3. Render detecta `render.yaml` / `package.json` y publica. Te da una dirección `https://...onrender.com`.
4. Abre esa dirección + `/buscador.html`.

## Próximo (Fase 1B)
- Redacción con IA (Claude) usando el estilo del skill *Noticias Venezuela*. La clave se guarda como variable de entorno en Render (`ANTHROPIC_API_KEY`), nunca en el código.
