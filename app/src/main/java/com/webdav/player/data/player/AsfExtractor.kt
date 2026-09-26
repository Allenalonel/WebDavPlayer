package com.webdav.player.data.player

import androidx.media3.common.util.UnstableApi

@UnstableApi
class AsfExtractor : NativeAsfExtractor() {
    companion object {
        val ASF_HEADER_GUID = NativeAsfExtractor.ASF_HEADER_GUID
        val STREAM_PROPERTIES_GUID = NativeAsfExtractor.STREAM_PROPERTIES_GUID
        val FILE_PROPERTIES_GUID = NativeAsfExtractor.FILE_PROPERTIES_GUID
        val DATA_OBJECT_GUID = NativeAsfExtractor.DATA_OBJECT_GUID
        val AUDIO_MEDIA_TYPE_GUID = NativeAsfExtractor.AUDIO_MEDIA_TYPE_GUID
    }

    class AsfSeekMap(
        durationUs: Long,
        dataStartOffset: Long,
        packetSize: Int,
        totalDataPackets: Long
    ) : NativeAsfExtractor.AsfSeekMap(durationUs, dataStartOffset, packetSize, totalDataPackets)
}
