package com.magikvlc.app.player

import android.media.MediaCodecList
import android.media.MediaFormat
import android.os.Build

object HwDecodePolicy {

    /**
     * Conservative estimate of how many concurrent hardware video decodes the
     * device supports for a given MIME type. Derived from the count of
     * available hardware decoders (API 23+); falls back to a default elsewhere.
     */
    fun maxSupportedInstances(mime: String = MediaFormat.MIMETYPE_VIDEO_AVC): Int {
        if (Build.VERSION.SDK_INT < 23) return 2
        return try {
            val count = MediaCodecList(MediaCodecList.REGULAR_CODECS).codecInfos.count {
                !it.isEncoder &&
                    it.supportedTypes.contains(mime) &&
                    it.name.startsWith("OMX.") &&
                    !it.name.contains("google", ignoreCase = true)
            }
            if (count == 0) 2 else count.coerceIn(2, 4)
        } catch (_: Throwable) {
            2
        }
    }
}