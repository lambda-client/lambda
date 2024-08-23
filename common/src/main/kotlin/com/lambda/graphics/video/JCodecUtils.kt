package com.lambda.graphics.video

import org.jcodec.api.FrameGrab
import org.jcodec.common.io.NIOUtils
import org.jcodec.common.model.ColorSpace
import org.jcodec.common.model.Picture
import org.jcodec.scale.ColorUtil
import org.jcodec.scale.RgbToBgr
import java.awt.image.BufferedImage
import java.awt.image.DataBufferByte
import java.io.File

object JCodecUtils {
    fun demuxVideo(path: String): FrameGrab =
        FrameGrab.createFrameGrab(NIOUtils.readableChannel(File(path)))

    fun toBufferedImage(src: Picture): BufferedImage {
        val processedSrc = convertToBGR(src)
        val dst = BufferedImage(processedSrc.croppedWidth, processedSrc.croppedHeight, BufferedImage.TYPE_3BYTE_BGR)

        if (processedSrc.crop == null) {
            copyImageData(processedSrc, dst)
        } else {
            copyCroppedImageData(processedSrc, dst)
        }

        return dst
    }

    private fun convertToBGR(src: Picture): Picture {
        if (src.color == ColorSpace.BGR) return src

        val bgr = Picture.createCropped(src.width, src.height, ColorSpace.BGR, src.crop)

        if (src.color == ColorSpace.RGB) {
            RgbToBgr().transform(src, bgr)
        } else {
            val transform = ColorUtil.getTransform(src.color, ColorSpace.RGB)
            transform.transform(src, bgr)
            RgbToBgr().transform(bgr, bgr)
        }

        return bgr
    }

    private fun copyImageData(src: Picture, dst: BufferedImage) {
        val data = (dst.raster.dataBuffer as DataBufferByte).data
        val srcData = src.getPlaneData(0)

        for (i in data.indices) {
            data[i] = (srcData[i] + 128).toByte()
        }
    }

    private fun copyCroppedImageData(src: Picture, dst: BufferedImage) {
        val data = (dst.raster.dataBuffer as DataBufferByte).data
        val srcData = src.getPlaneData(0)
        val dstStride = dst.width * 3
        val srcStride = src.width * 3

        for (line in 0 until dst.height) {
            var dstOffset = line * dstStride
            var srcOffset = line * srcStride

            for (x in 0 until dstStride step 3) {
                data[dstOffset] = (srcData[srcOffset] + 128).toByte()
                data[dstOffset + 1] = (srcData[srcOffset + 1] + 128).toByte()
                data[dstOffset + 2] = (srcData[srcOffset + 2] + 128).toByte()
                dstOffset += 3
                srcOffset += 3
            }
        }
    }
}
