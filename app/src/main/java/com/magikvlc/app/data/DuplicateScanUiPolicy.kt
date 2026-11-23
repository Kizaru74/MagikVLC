package com.magikvlc.app.data

object DuplicateScanUiPolicy {
    fun shouldAutoStart(
        onDuplicatesTab: Boolean,
        libraryReady: Boolean,
        hasVideoPermission: Boolean,
        videoCount: Int,
        workInfoLoaded: Boolean,
        hasWorkRecord: Boolean,
        userCancelled: Boolean = false,
    ): Boolean = onDuplicatesTab && libraryReady && hasVideoPermission && videoCount > 0 &&
        workInfoLoaded && !hasWorkRecord && !userCancelled

    fun reportIdToLoad(state: String, outputReportId: String?): String? =
        if (state == "SUCCEEDED" || state == "FAILED") outputReportId?.takeIf { it.isNotBlank() } else null

    fun noGroupsMessage(error: String?): String =
        if (error == null) "No se encontraron duplicados" else "No se pudo completar el análisis"
}
