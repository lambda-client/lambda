package com.lambda.graphics.video

import org.bytedeco.ffmpeg.global.avutil.*
import java.nio.ByteOrder

enum class SampleFormats(
    val sample: Int,
    val formatBe: String,
    val formatLe: String,
) {
    U8(AV_SAMPLE_FMT_U8, "u8", "u8"),
    S16(AV_SAMPLE_FMT_S16, "s16be", "s16le"),
    S32(AV_SAMPLE_FMT_S32, "s32be", "s32le"),
    F32(AV_SAMPLE_FMT_FLT, "f32be", "f32le"),
    F64(AV_SAMPLE_FMT_DBL, "f64be", "f64le");

    fun AV_NE(): String {
        return if (ByteOrder.nativeOrder() == ByteOrder.LITTLE_ENDIAN) formatLe
        else formatBe
    }

    companion object {
        fun getSampleFromFormat(format: Int): SampleFormats {
            return SampleFormats.entries.find { it.sample == format }
                ?: throw IllegalArgumentException("Unknown format: $format")
        }
    }
}
