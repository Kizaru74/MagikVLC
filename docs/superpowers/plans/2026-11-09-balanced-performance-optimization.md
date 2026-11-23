# Balanced Performance Optimization Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Mantener la navegación de Biblioteca inmediata y reducir la competencia de miniaturas, escaneo de duplicados y recomposición con bibliotecas grandes.

**Architecture:** `ThumbnailWarmer` conservará una cola prioritaria reanudable de hasta 150 elementos, pausada fuera de Videos/Favoritos y con progreso limitado. `LibraryScreen` coordinará el ciclo de vida de esa cola y evitará recalcular listas derivadas innecesariamente. El muro y la caché existente conservarán sus interfaces públicas salvo ajustes de prioridad y cancelación.

**Tech Stack:** Kotlin, Kotlin Coroutines, Jetpack Compose, MediaStore, `LruCache`, `ThumbnailDiskCache`.

**Spec:** Diseño aprobado en la conversación: optimización equilibrada de miniaturas, Biblioteca, duplicados, recomposiciones, muro y memoria sin cambiar la reproducción.

## Global Constraints

- La navegación entre pestañas debe ser inmediata y no esperar al escaneo ni a las miniaturas.
- La precarga debe priorizar elementos visibles/cercanos y limitarse inicialmente a 150 videos.
- Miniaturas se pausan en Listas, Duplicados y Acerca de; se reanudan en Videos y Favoritos.
- El muro mantiene reproducción individual/múltiple y un máximo de 9 tiles.
- No añadir dependencias ni cambiar ExoPlayer.
- Las miniaturas fuera de caché deben seguir cargándose bajo demanda.

## Review Focus

- Cambiar de pestaña mientras una miniatura está en decodificación: el trabajo debe pausarse/cancelarse sin actualizar una pantalla abandonada.
- Volver a Videos después de Listas/Duplicados: la cola debe reanudar progreso sin repetir innecesariamente elementos ya preparados.
- Biblioteca con menos de 150 videos: el total mostrado debe coincidir con la cantidad real y no quedar una barra infinita.
- Reordenar o refrescar mientras la cola está activa: no deben existir dos warmers simultáneos ni progreso inconsistente.
- Cancelar manualmente la precarga: debe detenerla completamente y permitir iniciarla de nuevo desde la UI.

---

### Task 1: Cola prioritaria y reanudable de miniaturas

**Files:**
- Modify: `app/src/main/java/com/magikvlc/app/data/ThumbnailWarmer.kt`
- Test: tests unitarios existentes de datos, si la configuración permite aislar el objeto; en caso contrario verificar mediante compilación y pruebas instrumentales disponibles.

**Interfaces:**
- Consumes: `VideoItem`, `ThumbnailCache.warm`, callback de scroll existente.
- Produces: `start(context, videos, isScrolling)`, `pause()`, `cancel()` compatibles con los consumidores actuales; `progress` con total limitado al máximo prioritario.

- [ ] Definir una cola objetivo limitada a `MAX_PRIORITY_ITEMS = 150` y un conjunto de claves completadas para que pausar/reanudar no reinicie el trabajo ya realizado.
- [ ] Hacer que `pause()` detenga el Job sin borrar la cola ni el progreso, mientras `cancel()` borre ambos estados y el botón «Parar» conserve su semántica actual.
- [ ] Evitar trabajos duplicados cuando `start()` se invoque varias veces; actualizar la cola si cambia la lista sin lanzar una segunda precarga simultánea.
- [ ] Mantener la espera durante scroll y emitir progreso con throttling; limpiar el progreso únicamente al completar toda la cola prioritaria.
- [ ] Ejecutar las pruebas disponibles y comprobar que la compilación no cambia las interfaces consumidoras.

### Task 2: Coordinación por pestaña y trabajo de Biblioteca

**Files:**
- Modify: `app/src/main/java/com/magikvlc/app/ui/library/LibraryScreen.kt`

**Interfaces:**
- Consumes: `ThumbnailWarmer.start/pause/cancel`, `tab`, `granted`, `videos`, `gridState`.
- Produces: ciclo de vida explícito: pausa fuera de Videos/Favoritos y reanudación dentro de las pestañas con grilla.

- [ ] Añadir un efecto de pestaña que invoque `ThumbnailWarmer.pause()` al entrar en Listas o Duplicados, y también cuando la pantalla deje de estar activa mediante la ruta existente de navegación.
- [ ] Mantener el arranque de la precarga ligado a la grilla visible para conservar la detección de scroll, pero hacerlo reanudable y limitado por Task 1.
- [ ] Asegurar que cambiar entre Videos y Favoritos no cree Jobs duplicados y que volver desde Duplicados no reactive una cola antigua incorrectamente.
- [ ] Aislar la barra de progreso para que observe solo `ThumbnailWarmer.progress` y muestre «prioritarias» cuando el total esté limitado.
- [ ] Mantener la carga cacheada inmediata y confirmar que una actualización fresca no impida cambiar de pestaña.

### Task 3: Reducir recomputaciones de la grilla

**Files:**
- Modify: `app/src/main/java/com/magikvlc/app/ui/library/LibraryScreen.kt`

**Interfaces:**
- Consumes: `videos`, `favorites`, `tab`, `query` y los estados derivados actuales.
- Produces: lista `shown` memoizada con las mismas reglas de favoritos, búsqueda y orden.

- [ ] Memoizar el cálculo de coincidencias de favoritos y la lista filtrada con claves explícitas (`videos`, favoritos, pestaña y consulta), sin alterar resultados.
- [ ] Evitar que el progreso de miniaturas, scrollbars o estados de selección fuerce filtros de 1300 elementos cuando sus entradas no cambiaron.
- [ ] Revisar el efecto de reparación de listas para que no se dispare por cambios irrelevantes del progreso o de la grilla.
- [ ] Ejecutar pruebas unitarias y compilación de Kotlin tras el cambio.

### Task 4: Competencia entre duplicados y miniaturas

**Files:**
- Modify: `app/src/main/java/com/magikvlc/app/ui/library/LibraryScreen.kt`
- Modify: `app/src/main/java/com/magikvlc/app/data/VideosDataSource.kt` solo si la verificación muestra que hacen falta puntos de cancelación adicionales.

**Interfaces:**
- Consumes: cancelación de `LaunchedEffect(tab, dupReport == null, dupScanCancelled)` y `findDuplicates` cancelable.
- Produces: no ejecutar precarga durante el escaneo de duplicados y cancelar el escaneo al abandonar la pestaña.

- [ ] Pausar la cola de miniaturas antes de iniciar el escaneo de duplicados.
- [ ] Confirmar que salir de Duplicados cancela la coroutine de `scanDuplicates` y no deja `dupScanning` bloqueado.
- [ ] Mantener `ensureActive()` en las fases de hash y añadir solo puntos de cooperación necesarios, sin cambiar la semántica de detección.
- [ ] Verificar que volver a Videos reanuda la precarga sin iniciar otro escaneo de duplicados.

### Task 5: Verificación de memoria, concurrencia y APK

**Files:**
- Inspect/Modify only if evidence requires: `ThumbnailCache.kt`, `ThumbnailDiskCache.kt`, `MosaicViewModel.kt`.

**Interfaces:**
- Consumes: límites actuales de `LruCache`, semáforos de cargas visibles/background y liberación diferida del muro.
- Produces: ajustes mínimos basados en evidencia, sin degradar thumbnails visibles ni reproducción.

- [ ] Revisar que la caché en memoria siga limitada a un máximo razonable y que `RGB_565` no genere copias retenidas innecesariamente.
- [ ] Confirmar que las cargas visibles tienen prioridad sobre warm y que no se superan los permisos de IO existentes.
- [ ] Confirmar que el cierre/reemplazo de tiles mantiene callbacks desconectados antes de liberar players.
- [ ] Ejecutar `testDebugUnitTest` y `assembleDebug`.
- [ ] Confirmar existencia, tamaño y fecha posterior al inicio del APK generado.
- [ ] Registrar como pendiente cualquier validación que solo pueda hacerse en dispositivo: cambio de pestañas con 1300 videos, scroll rápido y 9 players.

## Self-review

- La cola limitada cubre la fuente del progreso `360/1300` y conserva carga bajo demanda.
- La coordinación por pestaña cubre Listas, Duplicados, Acerca de mediante pausa de la precarga y cancelación natural de efectos.
- La memoización reduce trabajo de Compose sin cambiar el modelo de datos.
- La detección de duplicados no se altera; solo se reduce competencia y se conserva cancelación.
- La memoria y el muro se revisan sin introducir cambios especulativos.
