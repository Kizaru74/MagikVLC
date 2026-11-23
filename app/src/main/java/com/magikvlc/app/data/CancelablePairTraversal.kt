package com.magikvlc.app.data

internal inline fun forEachUnorderedPair(
    itemCount: Int,
    checkActive: () -> Unit,
    onPair: (left: Int, right: Int) -> Unit,
) {
    for (left in 0 until itemCount) {
        checkActive()
        for (right in left + 1 until itemCount) {
            checkActive()
            onPair(left, right)
        }
    }
}
