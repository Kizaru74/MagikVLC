package com.magikvlc.app.diagnostics

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Registro diagnóstico local y acotado para investigar bloqueos de navegación,
 * reproducción y carga de Biblioteca sin depender de logcat.
 */
object DiagnosticLog {
    internal const val MAX_ENTRIES = 200

    private val lock = Any()
    private val entries = ArrayDeque<String>()
    private val _revision = MutableStateFlow(0L)
    val revision: StateFlow<Long> = _revision.asStateFlow()

    fun log(component: String, message: String) {
        val time = SimpleDateFormat("HH:mm:ss.SSS", Locale.US).format(Date())
        val line = "[$time] [${Thread.currentThread().name}] [$component] $message"
        synchronized(lock) {
            entries.addLast(line)
            while (entries.size > MAX_ENTRIES) entries.removeFirst()
        }
        _revision.update { it + 1L }
    }

    fun snapshot(limit: Int = MAX_ENTRIES): List<String> = synchronized(lock) {
        entries.toList().takeLast(limit.coerceIn(1, MAX_ENTRIES))
    }

    fun text(limit: Int = MAX_ENTRIES): String =
        snapshot(limit).joinToString(separator = "\n")
            .ifBlank { "Sin eventos registrados." }

    fun clear() {
        synchronized(lock) { entries.clear() }
        _revision.update { it + 1L }
    }
}
