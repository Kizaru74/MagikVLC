# MagikVLC Performance, Duplicates, and Material 3 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Mejorar la fluidez de Biblioteca, hacer más confiable la detección de duplicados probables y consolidar el tema Material 3 sin romper reproducción, muro, favoritos ni playlists.

**Architecture:** Mantener MediaStore y Compose existentes. El relink de playlists se ejecutará fuera del hilo principal y solo después de un escaneo real. La detección exacta seguirá usando hashes; los probables usarán una puntuación pura y explicable. El tema seguirá usando `MaterialTheme`, ampliando colores, tipografía, formas y soporte claro/oscuro/dinámico.

**Tech Stack:** Kotlin, Jetpack Compose Material 3, MediaStore, coroutines, Kotlin unit tests.

**Spec:** Lista conversacional de mejoras pendientes del 2026-11-11.

## Global Constraints

- Mantener máximo 9 tiles, reemplazo por celda, Favoritos, Playlists, zoom/rotación y posición.
- No ejecutar trabajo CPU pesado de playlists en el hilo principal.
- No degradar la confirmación SHA-256 de duplicados exactos.
- El refresco manual debe seguir detectando videos nuevos y eliminados.
- Mantener compatibilidad con `minSdk = 21`.

## Review Focus

- Abrir Biblioteca desde muro con 0 players: no ejecutar suspensión ni relink pesado en UI.
- Abrir Biblioteca desde caché con 1,254 videos: mostrar contenido antes de precargar miniaturas.
- Playlists vacías o sin coincidencias: no bloquear ni alterar entradas.
- Videos re-encodeados con nombres distintos: clasificarlos como probables, no exactos.
- Android claro/oscuro y Android 12+ dinámico: conservar contraste y roles MD3.

### Task 1: Relink asíncrono y condicionado

**Files:**
- Modify: `app/src/main/java/com/magikvlc/app/ui/library/LibraryScreen.kt`
- Modify: `app/src/main/java/com/magikvlc/app/data/LibraryRepository.kt`
- Test: `app/src/test/java/com/magikvlc/app/ui/library/LibraryLoadPolicyTest.kt`

- [x] Registrar generación de escaneo y ejecutar `relinkAllPlaylists` solo cuando cambie esa generación.
- [x] Ejecutar el relink fuera del hilo UI mediante `withContext(Dispatchers.Default)`.
- [x] Añadir evento `PLAYLIST` con duración y cantidad corregida.
- [x] Ejecutar pruebas dirigidas y suite completa.

### Task 2: Heurística de duplicados probables

**Files:**
- Create: `app/src/main/java/com/magikvlc/app/data/DuplicateHeuristics.kt`
- Modify: `app/src/main/java/com/magikvlc/app/data/VideosDataSource.kt`
- Test: `app/src/test/java/com/magikvlc/app/data/DuplicateHeuristicsTest.kt`

- [x] Crear comparación pura mediante `DuplicateHeuristics.Candidate` y adaptador de `VideoItem`.
- [x] Puntuar nombre normalizado, duración relativa, resolución, relación de aspecto y tamaño relativo.
- [x] Devolver razón y confianza; no llamar a I/O desde la heurística.
- [x] Usar la puntuación para agrupar `probableGroups` sin cambiar grupos exactos SHA-256.
- [x] Cubrir coincidencia fuerte y falsos positivos por duración/nombre.

### Task 3: Consolidación Material 3

**Files:**
- Modify: `app/src/main/java/com/magikvlc/app/ui/theme/Theme.kt`
- Test/verification: build and manual light/dark review.

- [x] Añadir esquema claro de marca y seleccionar claro/oscuro según sistema.
- [x] Activar colores dinámicos Android 12+ de forma opcional mediante el parámetro `dynamicColor`.
- [x] Definir `Typography` y `Shapes` centralizados.
- [x] Mantener los componentes existentes conectados a roles semánticos de `MaterialTheme`.
- [ ] Verificar manualmente navegación bar/rail, insets, contraste y targets táctiles en dispositivo.

### Task 4: Verificación final

- [x] Ejecutar `testDebugUnitTest`.
- [x] Ejecutar `clean assembleDebug` y validar APK nuevo.
- [ ] Probar manualmente Biblioteca/Muro, duplicados y tema en dispositivo.
