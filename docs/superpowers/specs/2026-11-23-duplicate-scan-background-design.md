# Diseño: escaneo de duplicados fiable en segundo plano

## Intención

Evitar el falso mensaje «No se encontraron duplicados» causado por escanear una lista vacía mientras la Biblioteca todavía carga. El análisis debe usar un snapshot real de MediaStore y continuar si el usuario cambia de pestaña o minimiza MagikVLC.

## Evidencia y estado actual

- La captura del usuario muestra `Biblioteca · 1369 videos`, pero el reporte dice `Escaneados 0 vídeos`.
- `LibraryScreen` inicializa `videos` vacío y su `LaunchedEffect(tab, dupReport == null, dupScanCancelled)` puede llamar `scanDuplicates()` antes de terminar la carga.
- El efecto no usa el tamaño/generación de `videos` como condición para repetir el análisis cuando llegan los datos.
- `scanDuplicates()` vive en el estado de Compose. Al dejar el destino o destruirse la composición, su corrutina puede cancelarse.
- `VideosDataSource.findDuplicates()` ya ofrece fases y progreso, pero el progreso/resultados solo se mantienen en la pantalla y las estadísticas finales no llegan al diagnóstico visible de Acerca.
- La app tiene `READ_MEDIA_VIDEO`, pero no WorkManager ni permisos/notificación de trabajo en primer plano.

## Objetivos

1. No iniciar el escaneo mientras la biblioteca está cargando.
2. Nunca presentar como resultado válido un reporte con cero videos si el MediaStore contiene videos.
3. Continuar al cambiar de pestaña, navegar a otra pantalla o minimizar la app.
4. Mostrar una notificación de progreso con fase y avance, y ofrecer cancelación explícita.
5. Al volver a Duplicados, mostrar el progreso/resultado del mismo trabajo sin reiniciarlo.
6. Mantener el SHA-256 para exactos, los probables actuales y los modos básico/profundo.
7. Añadir diagnóstico acotado de inicio, snapshot, resultado, cancelación y error; no registrar cada archivo individual.

## No objetivos

- Cambiar la definición de duplicado exacto o los umbrales de probable.
- Ejecutar escaneos periódicos automáticos sin que el usuario abra Duplicados.
- Mantener el trabajo tras «Forzar detención» del sistema; Android bloquea el reinicio de trabajos hasta abrir la app de nuevo.
- Mostrar miniaturas, listas completas de nombres o información sensible en la notificación.

## Enfoques considerados

### A. `WorkManager` con worker de larga duración en foreground — recomendado

Encola un trabajo único de Duplicados y lo eleva a foreground con notificación mientras calcula. `WorkManager` mantiene el trabajo fuera del ciclo de vida de Compose, puede seguir con la app minimizada y administra recuperación/reintento si el proceso se interrumpe. La pantalla observa `WorkInfo` y lee el reporte persistido.

**Ventajas:** sobrevive cambios de pestaña y minimización; progreso durable; cancelación soportada; evita duplicar análisis concurrentes.

**Costes:** nueva dependencia, configuración de permisos/canal de notificación y persistencia del reporte. Android puede limitar trabajos largos; forzar detención detiene el trabajo.

### B. `CoroutineScope` de `Application`

Mover el job fuera de la pantalla y compartir su `StateFlow`.

**Ventajas:** menos dependencias y simple para cambios de pestaña.

**Costes:** el sistema puede matar el proceso al minimizar; no garantiza una notificación ni reanudar después. No cubre el requisito del usuario.

### C. Servicio foreground propio

Crear y gestionar manualmente servicio, ciclo de vida y notificación.

**Ventajas:** control completo de notificación y ejecución inmediata.

**Costes:** más código de ciclo de vida y manejo de Android moderno; hay que reconstruir persistencia, observación y reintentos que WorkManager ya proporciona.

**Elección propuesta:** A, porque cumple la continuidad y la notificación solicitadas con menos gestión manual que un servicio propio.

## Flujo propuesto

1. La carga de Biblioteca termina. Si el usuario está en Duplicados y hay videos, el análisis empieza automáticamente, conservando el comportamiento actual; si la biblioteca está realmente vacía, se muestra un estado distinto y no se crea trabajo.
2. Al empezar, se encola un trabajo único con el modo básico/profundo. El worker obtiene un snapshot fresco de MediaStore; no se pasan miles de URIs en `WorkManager Data`.
3. El worker ejecuta `findDuplicates()` sobre ese snapshot, publica fases/progreso con actualizaciones limitadas y actualiza la notificación.
4. El worker guarda el reporte final en almacenamiento privado de la app mediante DTO serializable. No guardar la lista completa en `WorkManager Data`, que tiene límite pequeño.
5. La UI observa estado/progreso del trabajo. Cambiar de pestaña o minimizar no cancela ni reinicia el worker. Al volver, consulta resultado persistido.
6. Cambiar el modo profundo durante un trabajo activo reemplaza/cancela ese trabajo y arranca otro con la nueva opción. «Cancelar» detiene explícitamente el trabajo. «Volver a buscar» inicia uno nuevo después de terminar.
7. Si se deniega `POST_NOTIFICATIONS` en Android 13+, explicar la limitación y conservar la notificación obligatoria de foreground según las reglas del sistema; no ocultar el estado del trabajo al volver a la app.

## Datos y estado

- `WorkManager Data` solo lleva opciones pequeñas (modo, identificador/versionado del reporte), nunca la biblioteca completa.
- Un `DuplicateScanRepository`/equivalente ofrece el trabajo activo y el reporte desde almacenamiento privado.
- El reporte persiste URI, metadatos necesarios, exact groups, probable groups, progreso final, unreadable y error.
- El reporte se invalida si se solicita un nuevo escaneo o cambian los videos mediante operación local; el nuevo worker vuelve a escanear MediaStore.
- El resultado vacío solo es válido si el snapshot fue cargado y `scannedCount` coincide con los elementos elegibles, no por pasar una lista vacía accidental.

## Notificación y permisos

- Canal específico de análisis de biblioteca.
- Notificación ongoing: «Buscando duplicados», fase y progreso resumido; acción «Cancelar».
- Solicitar permiso de notificaciones en Android 13+ en contexto del usuario antes de iniciar el trabajo foreground. Si se deniega, continuar el escaneo y avisar que Android puede ocultar la notificación del panel; el estado seguirá visible dentro de la app y en las superficies de trabajo foreground que permita el sistema.
- Declarar permisos y tipo de foreground service requeridos por la versión Android/WorkManager elegida; no solicitar permisos de video adicionales si `READ_MEDIA_VIDEO` ya fue concedido.

## Verificación

- Prueba de política: no escanear mientras `loading=true` ni con snapshot aún no cargado; iniciar con snapshot no vacío; biblioteca realmente vacía no crea trabajo.
- Pruebas del estado de trabajo: cambio de pestaña no cancela, minimización no cancela, cancelación explícita cancela, cambiar modo reemplaza trabajo activo.
- Prueba de persistencia/serialización del reporte exacto y probable.
- Suite `testDebugUnitTest`; prueba de compilación `assembleDebug`.
- Verificación manual en dispositivo: abrir Duplicados antes de que acabe la carga, confirmar que el contador nunca reporta cero falsamente; cambiar de pestaña y minimizar, observar notificación/progreso; volver y verificar resultado, cancelar y volver a buscar.

## Llamadas de implementación

- Usar `WorkManager` foreground en lugar de un scope de pantalla o servicio manual.
- El trabajo se inicia automáticamente al entrar a Duplicados, pero solo después de que la Biblioteca esté cargada y el snapshot no esté vacío.
- La denegación del permiso de notificaciones no bloquea el escaneo; se conserva el aviso de limitación de visibilidad.
- Progreso resumido en notificación y detalle en UI; actualizar en fases y con throttling, no por cada archivo.
- No borrar ni sustituir el algoritmo SHA-256 actual.

## Fuera de alcance / pregunta pendiente

- El gesto de zoom para videos verticales es un trabajo independiente. El usuario eligió la interacción A: pellizco focal, pan de un dedo cuando zoom > 1×, seek horizontal a 1× y doble toque lateral ±10 s. Se diseñará después de cerrar este cambio de Duplicados.
