package com.lambda.graphics.video

import com.lambda.Lambda.LOG
import org.bytedeco.ffmpeg.avcodec.AVCodecContext
import org.bytedeco.ffmpeg.avformat.AVFormatContext

import org.bytedeco.javacpp.*
import org.bytedeco.ffmpeg.avcodec.AVPacket
import org.bytedeco.ffmpeg.avutil.AVFrame
import org.bytedeco.ffmpeg.global.avcodec.av_packet_unref
import org.bytedeco.ffmpeg.global.avcodec.avcodec_alloc_context3
import org.bytedeco.ffmpeg.global.avcodec.avcodec_find_decoder
import org.bytedeco.ffmpeg.global.avcodec.avcodec_open2
import org.bytedeco.ffmpeg.global.avcodec.avcodec_parameters_to_context
import org.bytedeco.ffmpeg.global.avcodec.avcodec_receive_frame
import org.bytedeco.ffmpeg.global.avcodec.avcodec_send_packet
import org.bytedeco.ffmpeg.global.avformat.*
import org.bytedeco.ffmpeg.global.avutil.*
import org.bytedeco.ffmpeg.global.swscale
import java.io.FileOutputStream
import java.nio.ByteBuffer

/**
 * Utility object for handling audio-visual (AV) operations using the FFmpeg library.
 * Provides functions to create codec contexts, format contexts, and iterating through video frames.
 *
 * ## Resources for further understanding:
 * - [FFmpeg Documentation](https://ffmpeg.org/documentation.html)
 * - [JAVACV Documentation](https://bytedeco.org/)
 * - [Understanding Video Decoding in FFmpeg](https://ffmpeg.org/doxygen/trunk/group__lavc__decoding.html)
 * - [FFmpeg's AVFormatContext and AVCodecContext](https://ffmpeg.org/doxygen/trunk/structAVFormatContext.html)
 * - [Swscale Library for Image Scaling](https://ffmpeg.org/doxygen/trunk/group__libsws.html)
 */
object AVUtils {

    /**
     * Creates an `AVCodecContext` and an `AVFormatContext` for the video file at the specified path.
     *
     * @param path The file path to the video file.
     * @return A Triple containing an `AVCodecContext`, an `AVFormatContext` and the stream codec index, or `null` if the context creation fails.
     *
     * ### Detailed Explanation:
     * This function opens the video file using FFmpeg's `avformat_open_input()` function to initialize the `AVFormatContext`.
     * After opening the file, `avformat_find_stream_info()` extracts information about the streams (audio, video, etc.).
     * The function searches for a video stream (type `AVMEDIA_TYPE_VIDEO`), and if found, initializes a codec context
     * with `avcodec_alloc_context3()`. The stream's codec parameters are then copied to the codec context using
     * `avcodec_parameters_to_context()`.
     * If any errors occur during these steps, the error message is logged, and the function returns `null`.
     */
    fun createContext(
        path: String
    ): Triple<
            AVCodecContext,
            AVFormatContext,
            Int>? {
        val errStr = ByteArray(1024)
        var err = -1
        var streamIndex = -1
        val formatContext = AVFormatContext(null)

        err = avformat_open_input(formatContext, path, null, null)
        if (err < 0) {
            av_strerror(err, errStr, 1024)
            LOG.error("Failed to open video file at $path: ${errStr.decodeToString()}")
            return null
        }

        err = avformat_find_stream_info(formatContext, null as PointerPointer<*>?)
        if (err < 0) {
            av_strerror(err, errStr, 1024)
            LOG.error("Failed to find stream info for video file at $path: ${errStr.decodeToString()}")
            return null
        }

        av_dump_format(formatContext, 0, path, 0)

        for (i in 0 until formatContext.nb_streams()) {
            if (formatContext.streams(i).codecpar().codec_type() == AVMEDIA_TYPE_VIDEO) {
                streamIndex = i
                break
            }
        }

        if (streamIndex == -1) {
            LOG.error("Video stream not found in file at $path")
            return null
        }

        // Allocate codec context and set codec parameters
        val codecContext = avcodec_alloc_context3(null)
        avcodec_parameters_to_context(codecContext, formatContext.streams(streamIndex).codecpar())

        return Triple(codecContext, formatContext, streamIndex)
    }

    /**
     * Returns an iterator over the decoded video frames for the given codec and format context pair.
     *
     * @param ctx A Pair of `AVCodecContext` and `AVFormatContext` created from `createContext()`.
     * @return An `Iterator` of `AVFrame` representing the video frames, or `null` if initialization fails.
     *
     * ### Detailed Explanation:
     * This function sets up a decoding pipeline using FFmpeg's `avcodec_find_decoder()` and `avcodec_open2()`
     * to find and initialize the video codec. It also allocates memory for `AVFrame` structures used for decoding.
     * To convert the decoded frames into RGB format, the Swscale library is used (`sws_getContext()`), which scales
     * and formats the raw frames into RGB using `sws_scale()`. The frames are yielded using a Kotlin iterator.
     *
     * The iterator reads video packets (`av_read_frame()`) and decodes them into frames using `avcodec_send_packet()`
     * and `avcodec_receive_frame()`.
     */
    fun frameIterator(
        ctx: Triple<AVCodecContext, AVFormatContext, Int>
    ): Iterator<ByteBuffer>? {
        var err = -1
        val pkt = AVPacket()

        val codecContext = ctx.first
        val formatContext = ctx.second
        val streamIndex = ctx.third

        // Find and initialize the decoder for the video stream
        val codec = avcodec_find_decoder(codecContext.codec_id())
        if (codec == null) {
            LOG.error("Unsupported codec for video file")
            return null
        }

        err = avcodec_open2(codecContext, codec, null as PointerPointer<*>?)
        if (err < 0) {
            LOG.error("Failed to open codec for video decoding")
            return null
        }

        val frame = av_frame_alloc() ?: return null

        val pFrameRGB = av_frame_alloc() ?: run {
            LOG.error("Failed to allocate AV frame for RGB conversion")
            return null
        }

        val numBytes = av_image_get_buffer_size(AV_PIX_FMT_RGB24, codecContext.width(), codecContext.height(), 1)
        val buffer = BytePointer(av_malloc(numBytes.toLong()))

        val sourcePixFmt = correctForDeprecatedPixelFormat(codecContext.pix_fmt())

        // Initialize Swscale context for converting YUV to RGB
        // This is required to pass the image data to OpenGL.
        val swsCtx = swscale.sws_getContext(
            codecContext.width(),
            codecContext.height(),
            sourcePixFmt,
            codecContext.width(),
            codecContext.height(),
            AV_PIX_FMT_RGB24,
            swscale.SWS_BILINEAR,
            null, null, null as DoublePointer?
        )

        if (swsCtx == null) {
            LOG.error("Failed to initialize Swscale context")
            return null
        }

        av_image_fill_arrays(
            pFrameRGB.data(),
            pFrameRGB.linesize(),
            buffer,
            AV_PIX_FMT_RGB24,
            codecContext.width(),
            codecContext.height(),
            1,
        )

        return iterator {
            while (av_read_frame(formatContext, pkt) >= 0) {
                if (pkt.stream_index() == streamIndex) {
                    avcodec_send_packet(codecContext, pkt)
                    err = avcodec_receive_frame(codecContext, frame)
                }

                if (err >= 0) {
                    swscale.sws_scale(
                        swsCtx,
                        frame.data(),
                        frame.linesize(),
                        0,
                        frame.height(),
                        pFrameRGB.data(),
                        pFrameRGB.linesize(),
                    )

                    yield(pFrameRGB.data().asByteBuffer())
                }

                av_packet_unref(pkt)
            }

            // Clean up to prevent memory leak
            av_frame_free(frame)
        }
    }

    private fun correctForDeprecatedPixelFormat(pixFmt: Int): Int {
        // Fix swscaler deprecated pixel format warning
        // (YUVJ has been deprecated, change pixel format to regular YUV)
        return when (pixFmt) {
            AV_PIX_FMT_YUVJ420P -> AV_PIX_FMT_YUV420P
            AV_PIX_FMT_YUVJ422P -> AV_PIX_FMT_YUV422P
            AV_PIX_FMT_YUVJ444P -> AV_PIX_FMT_YUV444P
            AV_PIX_FMT_YUVJ440P -> AV_PIX_FMT_YUV440P
            else -> pixFmt
        }
    }

    fun saveFrame(pFrame: AVFrame, width: Int, height: Int, dest: String) {
        // Open file
        val pFile = FileOutputStream("$dest/output.ppm")

        // Write header
        pFile.write("P6\n$width $height\n255\n".toByteArray())

        // Write pixel data
        val data = pFrame.data(0)
        val bytes = ByteArray(width * 3)
        val lineSize = pFrame.linesize(0)
        for (y in 0L until height) {
            data.position(y * lineSize).get(bytes)
            pFile.write(bytes)
        }

        // Close file
        pFile.close()
    }
}
