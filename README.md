# Repórtalo 24 — App Android nativa

App nativa (Kotlin + Jetpack Compose) para buscar noticias de Venezuela, redactarlas
en tono amarillista y generar placas (imagen) listas para publicar. **Sin servidor:**
lee los RSS y genera todo en el teléfono.

## v1 (en construcción)
- **Titulares:** busca noticias por RSS de medios VE (Efecto Cocuyo, La Patilla, Tal Cual, Runrun.es, El Nacional, El Pitazo) + sugerencias amarillistas.
- **Generar:** placa nativa con franja, título (2 líneas autoajustadas), subtítulo con palabra en rojo, categoría, foto (zoom) y logo. PNG con foto y PNG transparente.
- **Borradores:** guarda, edita y reabre tus placas.
- **Marca:** nombre del medio, @usuario, logo, franja por defecto y gestión de fuentes RSS.
- **Copia de seguridad:** respaldo JSON restaurable (borradores + fuentes + marca).
- **Compartir desde otras apps:** compartes una noticia → arma la placa.

## Próximo (v2)
- Redacción con IA (Claude) con la clave guardada en el teléfono.
- Generador de video (texto sobre un video).

## Compilar
APK release vía GitHub Actions (pestaña **Actions** → artefacto `Reportalo24-v1.0.0`),
o en Android Studio con JDK 17 y Android SDK 35.

## Carpeta `web/`
Versión web previa (buscador + servidor Node). Se conserva como referencia; la app
nativa la reemplaza.
