package com.magikvlc.app.data

object DuplicateSelectionPolicy {
    fun replaceGroupSelection(
        currentUris: Set<String>,
        groupUris: Set<String>,
        nextSelectedUris: Set<String>,
    ): Set<String> = (currentUris - groupUris) + (nextSelectedUris intersect groupUris)
}
