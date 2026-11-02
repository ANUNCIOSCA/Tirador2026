# Repórtalo 24 — Documentación y custodia

> Reporte completo para resguardar la app. Con este documento, el código del
> repositorio y la **llave de firma**, cualquier persona (o IA) puede entender,
> compilar, actualizar y continuar el proyecto.
>
> **App:** Repórtalo 24 · **Versión:** 1.6.0 (versionCode 7) · **Fecha del reporte:** 2026-11-02
> **Propietario:** ANUNCIOSCA / Repórtalo 24 · **Correo:** llamadosafuera247@gmail.com

---

## 1. Qué es

App **Android nativa** para un periódico digital: busca noticias de Venezuela,
las redacta en tono amarillista/crítico (formato R24) y genera **placas** (imagen)
y **videos** con franja, título y subtítulo para redes sociales. Todo corre en el
teléfono; solo usa internet para leer los RSS y (opcional) la IA.

- **Nombre del paquete (applicationId):** `com.reportalo24.app`
- **Sin servidor propio:** la app lee los RSS directo y genera todo localmente.
- **IA opcional:** redacción con la API de **Groq** (clave guardada solo en el teléfono).

## 2. Repositorio y acceso

- **Repositorio:** `https://github.com/ANUNCIOSCA/Tirador2026`
- **Rama principal:** `main` (código fuente).
- **Rama `reportalo24-ci-output`:** la genera el CI; contiene el último `.apk` compilado
  y `status.txt`. Enlace de descarga directa del APK (si el repo es público):
  `https://github.com/ANUNCIOSCA/Tirador2026/raw/reportalo24-ci-output/Reportalo24-vX.Y.Z.apk`
- La versión web previa (buscador + servidor Node) quedó archivada en la carpeta `web/`.

## 3. Arquitectura y stack

| Elemento | Valor |
|---|---|
| Lenguaje | Kotlin |
| UI | Jetpack Compose + Material 3 |
| minSdk / targetSdk / compileSdk | 26 / 35 / 35 |
| Android Gradle Plugin | 8.7.3 |
| Kotlin | 2.1.0 (+ plugin compose 2.1.0) |
| Gradle | 8.11.1 |
| JDK | 17 |
| Compose BOM | 2024.12.01 |
| Almacenamiento | JSON en SharedPreferences (sin base de datos externa) |
| Red | `HttpURLConnection` nativo (RSS) y API de Groq (IA) |
| Video | MediaCodec + OpenGL ES (overlay) + MediaMuxer |

**Dependencias (app/build.gradle.kts):** compose-bom 2024.12.01, core-ktx 1.15.0,
activity-compose 1.9.3, lifecycle-runtime-ktx 2.8.7, compose ui / ui-graphics /
foundation / material3 / material-icons-extended.

## 4. Estructura de archivos (código en `app/src/main/java/com/reportalo24/app/`)

| Archivo | Qué hace |
|---|---|
| `MainActivity.kt` | Shell: 4 pestañas (Titulares, Generar, Borradores, Marca), navegación, recibe texto compartido de otras apps. |
| `Theme.kt` | Colores de marca (teal/rojo) y tema Material 3. |
| `Models.kt` | Modelos: `Noticia`, `Borrador`, `FuenteRss`, `PerfilMarca`, `AppConfig`, `Categorias`, `FormatoPlaca`, `EstadoBorrador`. |
| `Stores.kt` | Persistencia JSON: `BorradorStore`, `FuenteStore`, `PerfilStore`, `ConfigStore`. |
| `AppState.kt` | Estado central en memoria + guardado inmediato en disco. |
| `Rss.kt` | Lee y parsea los feeds RSS (sin servidor). |
| `Redactor.kt` | Redacción por **plantillas** (fórmulas R24, categoría, franja, hashtags, copy). |
| `GroqRedactor.kt` | Redacción con **IA (Groq)**; contiene el *prompt editorial R24*. |
| `PlacaRenderer.kt` | Dibuja la placa en Canvas de Android (franja, título 2 líneas, subtítulo con palabra en rojo, categoría, foto, logo). Núcleo visual. |
| `VideoComposer.kt` | Pone la placa encima de un video (decodifica + OpenGL + recodifica a MP4, con audio). |
| `ImageIO.kt` | Cargar/guardar/compartir imágenes y videos (galería + FileProvider). |
| `TitularesScreen.kt` | Buscar noticias, sugerencias, y **redacción manual formato R24**. |
| `GeneradorScreen.kt` | Editor de la placa + vista previa en vivo + ventana emergente + descargar/compartir/video. |
| `BorradoresScreen.kt` | Lista de borradores guardados. |
| `MarcaScreen.kt` | Perfil del medio, fuentes RSS, **config de IA (Groq)** y copia de seguridad. |
| `Backup.kt` | Copia de seguridad JSON (borradores + fuentes + marca + config IA). |

Recursos en `app/src/main/res/`: `values/themes.xml`, `values/colors.xml`,
`values/strings.xml`, `xml/file_paths.xml`, íconos adaptativos del logo R24 en
`mipmap-*/ic_launcher_fg.png` y `mipmap-anydpi-v26/ic_launcher*.xml`.

## 5. Cómo se compila

### A) Automático (GitHub Actions) — el que usamos
Cada `push` a cualquier rama ejecuta `.github/workflows/build.yml`:
1. Checkout, JDK 17, Gradle 8.11.1.
2. `gradle :app:assembleRelease`.
3. Publica el APK en la rama `reportalo24-ci-output` y como *artifact* de la corrida.

### B) Local (Android Studio o consola)
Requisitos: JDK 17 + Android SDK 35.
```bash
./gradlew :app:assembleRelease
# APK en: app/build/outputs/apk/release/
```

## 6. Firma de la app (lo MÁS importante para custodia)

Android solo permite **actualizar** una app (sin desinstalar y sin perder datos)
si la nueva versión está firmada con **la misma llave**. Si cambias de llave,
el usuario debe desinstalar y pierde su configuración.

- **Estado actual:** las compilaciones de CI están firmadas con la llave **debug**
  (que cambia en cada corrida). Por eso, hasta ahora, cada actualización pedía
  desinstalar. Para resolverlo de raíz hay que usar una **llave fija (keystore)**.
- **Llave de firma generada para ti (guárdala en secreto):**
  - Archivo: `r24-release.jks` (se te entrega aparte por chat; **no** está en el repo público).
  - Tipo: PKCS12 · Algoritmo: RSA 2048 · Validez hasta: **2054-03-19**.
  - **Alias, contraseña y huella SHA-256:** se te entregan EN PRIVADO por el chat.
    No se guardan en este repositorio por seguridad. Consérvalos junto al archivo `.jks`.

### Cómo activar la firma fija (cuando puedas)
El `app/build.gradle.kts` ya soporta firmar con variables de entorno:
`R24_KEYSTORE` (ruta al .jks), `R24_KEYSTORE_PASSWORD`, `R24_KEY_ALIAS`, `R24_KEY_PASSWORD`.
Dos caminos:
1. **Repo privado:** haz el repositorio privado y guarda el `.jks` dentro (o como
   *GitHub Secret* en base64). Así el CI firma siempre igual.
2. **Firmar local:** compila con Android Studio usando el keystore de arriba.

> ⚠️ Si pierdes este keystore, **no podrás volver a actualizar** la app instalada:
> habría que publicarla como app nueva. Guárdalo en 2 o 3 lugares seguros.

## 7. La IA (Groq) — cómo funciona

- La app llama a `https://api.groq.com/openai/v1/chat/completions` (compatible con OpenAI).
- La **clave** (`gsk_...`) se obtiene gratis en `https://console.groq.com/keys` y se
  guarda **solo en el teléfono** (`ConfigStore`, SharedPreferences `r24_config`).
- Modelos: `llama-3.3-70b-versatile` (mejor) o `llama-3.1-8b-instant` (rápido).
- El *prompt editorial* está en `GroqRedactor.kt` (ver sección 8).
- Si la IA falla o no hay internet, la app cae a las **plantillas** (`Redactor.kt`).

## 8. Fórmula editorial R24 (el "carácter" de las noticias)

Basada en el flujo del medio (amarillista + crítico + humanizado, **dato sagrado**):

- **3 reglas:** (1) tono amarillista y crítico siempre, nunca neutral; (2) humanizado,
  sin voz de IA; (3) no inventar datos — lo no comprobable va atribuido o se omite.
- **Fórmula del titular (5 capas):** señalar al **responsable** → palabra **moral** →
  palabra **negativa** → **vacío** que da intriga → **ancla** (cifra/fecha) en el subtítulo.
- **Formato de la placa:**
  - **Franja:** etiqueta de urgencia corta (ÚLTIMA HORA, ALERTA, URGENTE…), distinta de la categoría y del título.
  - **Título:** sentencia corta con gancho, 3–6 palabras, que **juzga** y señala al responsable.
  - **Subtítulo:** "el dato que duele", con cifra o fecha; la palabra clave va en rojo (entre `**dobles asteriscos**`).
- **Categorías:** Nacional, Internacional, Política, Economía, Sucesos, Deporte, Farándula, Salud, Tecnología, Viral, Opinión.

## 9. Datos y copia de seguridad

- Todo se guarda **local** en SharedPreferences (JSON). Nada sale del teléfono salvo
  las peticiones a RSS y a Groq.
- **Copia de seguridad (Marca → Guardar copia JSON):** incluye borradores, fuentes,
  marca y **la configuración de IA (clave incluida)**. Restaurable con *Restaurar desde archivo*.
- Al **desinstalar** la app se borra todo: por eso, antes de reinstalar, haz una copia.

## 10. Funcionalidades (v1.6.0)

- **Titulares:** buscar noticias por RSS (6 medios VE) + sugerencias amarillistas + **redacción manual formato R24**.
- **Generar:** placa nativa (franja, título de 2 líneas con tamaño por línea, subtítulo con palabra en rojo, categoría, foto con zoom, logo). PNG con foto y PNG transparente. **Ventana en vivo** con pellizco para agrandar. Botones **Redactar con IA** y **Otro título**.
- **Video:** subir un video → bajarlo con la placa encima (MP4) → publicar.
- **Borradores:** guardar, reabrir, editar, eliminar.
- **Marca:** nombre del medio, @usuario, logo, franja por defecto, fuentes RSS, config IA y copia de seguridad.
- **Compartir desde otras apps** → arma la placa.
- **Formatos:** Story/Reel 1080×1920, Carrusel 1080×1080, Feed 1080×1350.

## 11. Historial de versiones

| Versión | Qué trajo |
|---|---|
| 1.0.0 | App base: titulares RSS, generador de placa, borradores, marca, backup. |
| 1.1.0 | Generador de **video** (placa sobre video). |
| 1.2.0 | Voz editorial R24 en la IA (Groq). |
| 1.3.0 | **Redacción manual** con formato R24 (sin IA). |
| 1.4.0 | Tamaño por línea del título, todas las categorías, ventana en vivo, video publicar. |
| 1.5.0 | Ícono R24 (logo); la config de IA se guarda sola. |
| 1.6.0 | Títulos cortos con gancho, botones Redactar con IA / Otro título, ventana en vivo usable + pellizco, franja distinta de categoría, copia que incluye la clave de IA. |

## 12. Limitaciones conocidas / pendientes

- **Firma:** aún con llave debug en CI (ver sección 6). Pendiente activar firma fija.
- **Video:** probado poco en dispositivos reales; puede necesitar ajuste de rotación en algunos teléfonos.
- **RSS:** depende de que cada medio tenga feed; algunos pueden fallar.
- **Roadmap sugerido:** pantalla "flujo rápido" (una noticia → placa + copy + nota web) como principal; **publicar a WordPress**; IA también para el video; verificación web con fuentes.

## 13. Cómo continuar con otra IA (prompt de traspaso)

> Copia y pega esto al empezar con otra IA, junto al código del repositorio:

«Tengo una app Android nativa llamada **Repórtalo 24** (Kotlin + Jetpack Compose,
package `com.reportalo24.app`, minSdk 26, targetSdk 35, AGP 8.7.3, Kotlin 2.1.0,
Gradle 8.11.1, JDK 17, Compose BOM 2024.12.01). El código está en GitHub
(`ANUNCIOSCA/Tirador2026`, rama `main`). Genera placas e imágenes/videos de noticias
para redes con un formato editorial amarillista (formato R24: franja de urgencia,
título corto de 3–6 palabras que juzga, subtítulo con el dato que duele y la palabra
clave en rojo). Usa RSS directo (sin servidor) y, opcional, la API de Groq para
redactar (clave guardada en el teléfono). Compila por GitHub Actions
(`.github/workflows/build.yml`) o con `./gradlew :app:assembleRelease`. Tengo el
keystore de firma (alias `r24`). Ayúdame a [lo que necesites] respetando esta
arquitectura y el formato R24 descrito en `DOCUMENTATION.md`.»

## 14. Licencia

Ver el archivo `LICENSE`. Por defecto: **Todos los derechos reservados** a nombre de
ANUNCIOSCA / Repórtalo 24 (software y marca propietarios). Puedes cambiarla a una
licencia abierta (p. ej. MIT) si decides liberar el código.

## 15. Checklist de custodia (qué guardar en lugar seguro)

- [ ] El **keystore** `r24-release.jks` + su contraseña y alias (entregados por chat, fuera del repo).
- [ ] Acceso a la cuenta de **GitHub** `ANUNCIOSCA` (dueña del repo).
- [ ] Tu **clave de Groq** (`gsk_...`) — o saber que se recrea gratis en console.groq.com.
- [ ] Una **copia de seguridad JSON** reciente de la app (borradores + config).
- [ ] Este `DOCUMENTATION.md` y el código del repositorio.
- [ ] El **logo** original (ícono) y la paleta: teal `#1A8B8B` / `#0F5A5A`, rojo `#D61F2C`.
