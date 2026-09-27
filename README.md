# JSUS Downloader (Android)

App Android para descargar **video o audio** de YouTube eligiendo calidad, formato y carpeta. Versión móvil de la extensión de Chrome JSUS Downloader.

## Descargar el APK
Ve a **[Releases](../../releases/latest)** y baja:
- `JsusDownloader-...-arm64.apk` → casi todos los celulares (recomendado)
- `JsusDownloader-...-armv7.apk` → celulares viejos de 32 bits

Cada cambio en `main` compila un APK nuevo automáticamente (pestaña **Actions**).

## Funciones
- Pegar link o **Compartir → JSUS Downloader** desde la app de YouTube
- Video: calidades reales (8K…144p) con tamaño, MP4 / MKV / WEBM, modo H.264 compatible
- Audio: MP3, M4A, OPUS, FLAC, WAV u original, bitrate 96–320 kbps
- Carpeta de destino a elección (o preguntar cada vez). Por defecto: `Descargas/JSUS Downloader`
- Subtítulos, portada, SponsorBlock, recorte por tiempo, playlists completas
- Cola con descargas simultáneas, progreso, cancelar, notificaciones
- Historial y actualización de yt-dlp desde la app

## Tecnología
Kotlin + Jetpack Compose · [youtubedl-android](https://github.com/yausername/youtubedl-android) (yt-dlp + Python + ffmpeg + QuickJS)

## Firma con tu propio keystore (opcional)
En *Settings → Secrets and variables → Actions* crea:
- `JSUS_KEYSTORE_BASE64` → el `.jks` en base64
- `JSUS_KEYSTORE_PASSWORD`, `JSUS_KEY_ALIAS`, `JSUS_KEY_PASSWORD`

Sin Secrets se firma con la clave de desarrollo de `keystore/` (sirve para instalar y actualizar).

> Descarga solo contenido propio o que tengas derecho a guardar.
