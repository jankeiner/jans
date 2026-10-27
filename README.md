# Jan’s — reproductor Android

Esta versión añade escaneo multimedia nativo, lectura de etiquetas, vinculación de letras LRC, carátulas, edición de metadatos y contador acumulado de reproducción. El proyecto se compila como APK de depuración desde GitHub Actions.

## Cambios incluidos

- **Escanear teléfono (lupa):** plugin Android nativo consulta `MediaStore.Audio` y solicita permiso de audio. Solo enumera elementos del catálogo multimedia de audio; no añade vídeos ni documentos.
- **Escanear carpeta:** alternativa que permite seleccionar una carpeta con `webkitdirectory`. Jan’s filtra audio y vincula archivos `.lrc` si comparten nombre base y carpeta relativa.
- **Etiquetas de canciones:** `jsmediatags` lee título, artista, álbum, letras incrustadas y carátula de archivos seleccionados compatibles. El escáner nativo obtiene título/artista/álbum/duración/tamaño desde MediaStore y la carátula incrustada cuando se abre una pista.
- **Letras sincronizadas:** se interpretan marcas de tiempo LRC (`[mm:ss.xx]`) y se resalta la línea correspondiente durante la reproducción. También se intenta usar letra incrustada cuando el formato la expone.
- **Editor con lápiz:** cambia título, artista, álbum, año y carátula en la biblioteca de Jan’s. Los cambios se guardan localmente como metadatos de la app. Para MP3 se puede exportar una copia con etiquetas ID3 actualizadas (incluida la carátula si se selecciona una imagen compatible); el original no se sobrescribe.
- **Contador correcto:** el tiempo acumulado solo avanza mientras un archivo está reproduciéndose, no por tener Jan’s abierta.
- **GitHub Actions:** workflow instala dependencias, empaqueta el plugin Android, compila `assembleDebug` y publica el APK como artefacto `jans-debug-apk`.

## Requisitos y limitaciones importantes

1. El escaneo completo del teléfono usa el catálogo multimedia de Android y requiere permiso `READ_MEDIA_AUDIO` en Android 13+ o `READ_EXTERNAL_STORAGE` en versiones anteriores. Android puede excluir archivos que no estén indexados por MediaStore; usa Escanear carpeta como alternativa.
2. Para archivos nativos, Jan’s copia la pista seleccionada a la caché privada para reproducirla desde WebView. Esto puede ocupar espacio temporal; los archivos de caché pueden borrarse por Android.
3. La asociación automática de LRC requiere que Android indexe el archivo `.lrc` y que tenga el mismo nombre base que la canción en la misma carpeta. En modo carpeta, se vincula por nombre y ruta relativa. Las variantes con nombres diferentes necesitarán vinculación manual en una mejora futura.
4. Las letras incrustadas dependen de si el formato y el proveedor de metadatos las exponen. No se descargan letras de internet ni se inventan.
5. El lápiz edita los metadatos que muestra Jan’s. Para no sobrescribir ni dañar los archivos originales, la exportación con etiquetas ID3 se ofrece como copia MP3. No se escribe directamente sobre el archivo original ni se reescriben etiquetas de WAV/FLAC/M4A.
6. La lectura de carátulas y etiquetas depende del formato y de los metadatos realmente presentes. Si un archivo no contiene artista, álbum o carátula, Jan’s muestra valores genéricos en lugar de inferirlos del nombre de archivo.
7. La cuenta de tiempo mide segundos reales de reproducción de la biblioteca local. El audio de reproductores externos/embebidos de Spotify y YouTube no se contabiliza como pista local.

## Compilar con GitHub Actions

1. Sube el contenido de esta carpeta a un repositorio GitHub (incluida la carpeta `.github/workflows`).
2. En la pestaña **Actions**, ejecuta **Build Jan's Android APK** con **Run workflow**, o haz push a `main`.
3. Al terminar, abre la ejecución y descarga el artefacto **`jans-debug-apk`**. Dentro estará `app-debug.apk`.
4. Instala el APK de depuración en Android y concede el permiso de audio cuando pulses la lupa.

## Compilación local

Requiere Node.js 22, JDK 21 y Android SDK. Ejecuta:

```bash
npm install
npm run build
npx cap add android
# Copiar android-plugin/JansMediaPlugin.java a android/app/src/main/java/com/jans/music/
# Registrar JansMediaPlugin en MainActivity y añadir permisos como hace el workflow
npx cap sync android
cd android && ./gradlew assembleDebug
```

El entorno de generación no dispone de Android SDK, por lo que el APK debe compilarse desde GitHub Actions o Android Studio. La compilación en GitHub también es la verificación final de que el plugin nativo se integra con la versión concreta de Capacitor.
