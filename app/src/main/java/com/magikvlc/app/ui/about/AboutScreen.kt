package com.magikvlc.app.ui.about

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.magikvlc.app.BuildConfig
import com.magikvlc.app.diagnostics.DiagnosticLog
import com.magikvlc.app.data.ThumbnailCache
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val logRevision by DiagnosticLog.revision.collectAsStateWithLifecycle()
    val diagnosticText = androidx.compose.runtime.remember(logRevision) {
        DiagnosticLog.text(limit = 80)
    }
    val versionName = try {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: BuildConfig.VERSION_NAME
    } catch (_: Exception) { BuildConfig.VERSION_NAME }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        TopAppBar(
            title = { Text("Acerca de") },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.Filled.ArrowBack, contentDescription = "Volver")
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.surface,
                titleContentColor = MaterialTheme.colorScheme.onSurface,
            ),
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // Hero card con gradiente
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = Color.Transparent),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            Brush.linearGradient(
                                colors = listOf(
                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.8f),
                                    MaterialTheme.colorScheme.tertiary.copy(alpha = 0.6f),
                                ),
                            ),
                        )
                        .padding(24.dp),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Filled.PlayArrow,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier
                                .size(56.dp)
                                .background(
                                    Color.Black.copy(alpha = 0.3f),
                                    RoundedCornerShape(16.dp),
                                )
                                .padding(8.dp),
                        )
                        Column {
                            Text(
                                text = "MagikVLC",
                                style = MaterialTheme.typography.headlineMedium,
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                            )
                            Text(
                                text = "v$versionName (${BuildConfig.GIT_SHA})",
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color.White.copy(alpha = 0.8f),
                            )
                        }
                    }
                }
            }

            // Info del dispositivo
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                ),
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Info del dispositivo",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    InfoRow("Versión", "v$versionName")
                    InfoRow("Build", BuildConfig.GIT_COUNT)
                    InfoRow("Android", "${Build.VERSION.RELEASE} (${Build.VERSION.SDK_INT})")
                    InfoRow("Dispositivo", "${Build.MANUFACTURER} ${Build.MODEL}")
                    InfoRow("libvlc", "3.6.2")
                }
            }

            // Caché de miniaturas
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                ),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Caché de miniaturas",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            text = "Acelera la biblioteca guardando vistas previas en el dispositivo.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    TextButton(onClick = {
                        scope.launch {
                            ThumbnailCache.clear(context)
                            Toast.makeText(context, "Caché borrada", Toast.LENGTH_SHORT).show()
                        }
                    }) { Text("Borrar") }
                }
            }

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                ),
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Diagnóstico",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = "Últimos eventos de navegación, muro y Biblioteca.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    SelectionContainer {
                        Text(
                            text = diagnosticText,
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(MaterialTheme.colorScheme.surface)
                                .padding(8.dp),
                            style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 80,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                    ) {
                        TextButton(onClick = {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE)
                                as? ClipboardManager
                            clipboard?.setPrimaryClip(
                                ClipData.newPlainText("MagikVLC diagnóstico", DiagnosticLog.text())
                            )
                            Toast.makeText(context, "Diagnóstico copiado", Toast.LENGTH_SHORT).show()
                        }) { Text("Copiar") }
                        TextButton(onClick = { DiagnosticLog.clear() }) { Text("Borrar") }
                    }
                }
            }

            // Historial de versiones
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                ),
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Historial de versiones",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    changelog.forEach { entry ->
                        Text(
                            text = entry.version,
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurface,
                            fontWeight = FontWeight.Bold,
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        entry.changes.forEach { change ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 2.dp),
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                Text(
                                    text = "•",
                                    color = MaterialTheme.colorScheme.primary,
                                )
                                Text(
                                    text = change,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                    }
                }
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

private data class ChangelogEntry(val version: String, val changes: List<String>)

private val changelog = listOf(
    ChangelogEntry(
        version = "v1.5.0",
        changes = listOf(
            "Selección múltiple en playlist para mandar al muro",
            "Scroll fluido: pre-generador conservador y sin shimmers",
            "Overlay de tiles a 5 s y scrub en vivo al estilo VLC (sin burbuja)",
            "Seekbar fluida con ticker local y scrub de 60 s con margen anti-fin",
            "Sin descarte global de cuadros; HUD de fps renderizados en stats",
            "Overlays a 6 s y scrub sin tirones (throttle 300 ms)",
            "Fix: el HUD ya no intercepta frames (pantalla negra)",
            "Video único abre fullscreen y Atrás vuelve a Biblioteca",
            "Watchdog de arranque: sin celdas en blanco",
            "Overlay fijo mientras se arrastra la barra de tiempo",
            "Biblioteca instantánea: caché del último escaneo + refresco en fondo",
            "Listas y favoritos se re-enlazan solos si mueves videos de almacenamiento",
            "Añadir a lista en cuadrícula de dos columnas",
            "Barra de desplazamiento de biblioteca más ancha y fácil de arrastrar",
            "Controles de pantalla completa con los botones centrados",
            "Duplicados: hash seguro con caché, miniaturas y borrado por selección",
            "Comparador de duplicados para elegir la copia a conservar",
            "La X del muro reemplaza una celda sin reiniciar los demás videos",
            "Duplicados: detección por nombre y modo de comparación profunda",
            "Renombrar videos directamente desde Duplicados",
            "Mover videos entre carpetas y entre memoria interna y SD",
            "Barra de progreso y marca de visto al estilo VLC en la biblioteca",
            "Fix: volver a reproducir o adelantar un video terminado en el muro",
            "Salida más fluida de video único hacia Biblioteca",
            "Zoom, desplazamiento y rotación independientes por video",
            "Doble toque lateral: saltos de ±10 segundos corregidos",
            "Audio único o múltiple en el muro con selección por video",
            "Zoom vertical con desplazamiento individual y rotación en controles",
            "Controles del video simplificados y fullscreen fijo por tile",
            "Detector básico de duplicados mejorado para copias renombradas y muestras distribuidas",
            "Transición de reemplazo más fluida con muchos videos en el muro",
            "Aviso al seleccionar más videos que los 9 tiles disponibles",
        ),
    ),
    ChangelogEntry(
        version = "v1.4.0",
        changes = listOf(
            "Crear lista nueva desde multiselección, con buscador",
            "Pre-generación de miniaturas en segundo plano",
            "Duplicados probables por duración y resolución, con verificación",
        ),
    ),
    ChangelogEntry(
        version = "v1.3.0",
        changes = listOf(
            "Tema violeta en toda la app",
            "Filtros como diálogo centrado con cuadrícula",
            "Caché de miniaturas persistente en disco (150 MB)",
            "Vista previa al arrastrar la barra de tiempo (video enfocado)",
            "Selector de celdas compacto y modo Recortar sin deformar",
        ),
    ),
    ChangelogEntry(
        version = "v1.2.0",
        changes = listOf(
            "Modo fluido para 9 videos: arranque priorizado y menos carga en segundo plano",
            "Icono de memoria interna o tarjeta SD en videos, listas y duplicados",
            "Listas y detalle de lista como cuadrícula con miniaturas",
            "Biblioteca compacta con búsqueda y filtros colapsables",
        ),
    ),
    ChangelogEntry(
        version = "v1.1.0",
        changes = listOf(
            "Volumen por video hasta 200% (como VLC): gesto, slider y OSD",
            "Al terminar un video, Play reinicia desde el inicio",
            "Picture-in-Picture: muro o fullscreen en ventana flotante",
            "Controles PiP: play/pausa y ±10 s del video enfocado",
            "Auto-PiP al pulsar Home (toggle \"PiP auto\" en el muro)",
        ),
    ),
    ChangelogEntry(
        version = "v1.0.0",
        changes = listOf(
            "Muro de hasta 9 videos reproduciendo en paralelo con LibVLC",
            "Overlay de controles estilo VLC por tile",
            "Biblioteca con videos del dispositivo",
            "Búsqueda por nombre, favoritos y pestaña de duplicados",
            "Detección de duplicados robusta por contenido",
            "Ecualizador global, velocidad y escala por tile",
            "Modo sincronizado (Sync) y barra de scroll interactiva",
        ),
    ),
)
