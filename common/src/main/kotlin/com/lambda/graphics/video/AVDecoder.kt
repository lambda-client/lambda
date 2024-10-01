package com.lambda.graphics.video

import org.bytedeco.ffmpeg.avcodec.AVCodecContext
import org.bytedeco.ffmpeg.avcodec.AVPacket
import org.bytedeco.ffmpeg.avformat.AVFormatContext
import org.bytedeco.ffmpeg.global.avcodec.*
import org.bytedeco.ffmpeg.global.avformat.*
import org.bytedeco.ffmpeg.global.avutil.*
import org.bytedeco.ffmpeg.global.swscale.*
import org.bytedeco.ffmpeg.swscale.SwsContext
import org.bytedeco.javacpp.BytePointer
import org.bytedeco.javacpp.DoublePointer
import org.bytedeco.javacpp.PointerPointer
import java.nio.ByteBuffer

class AVDecoder(
    path: String,
) {
    private val formatContext: AVFormatContext = AVFormatContext(null)
    private var videoCodecContext: AVCodecContext? = null
    private var audioCodecContext: AVCodecContext? = null
    private var videoStreamIndex: Int = -1
    private var audioStreamIndex: Int = -1
    private var swsContext: SwsContext? = null

    private var error = -1
    private val errorBuffer = ByteArray(1024)

    private var videoPTS = 0L

    fun videoInfo(): VideoInfo {
        val fps = av_q2d(formatContext.streams(videoStreamIndex).r_frame_rate())
        val timeBase = av_q2d(formatContext.streams(videoStreamIndex).time_base())

        return VideoInfo(
            width = videoCodecContext?.width() ?: 0,
            height = videoCodecContext?.height() ?: 0,
            frameRate = fps,
            frameDuration = { videoPTS * timeBase },
        )
    }

    fun audioInfo(): AudioInfo {
        return AudioInfo(0)
    }

    fun audioFrameIterator(): Iterator<ByteBuffer>? {
        audioCodecContext?.let { codec ->
            val audioFrame = av_frame_alloc()
            val pkt = AVPacket()

            // Number of bytes per sample or zero if unknown for the given sample format
            // https://ffmpeg.org/doxygen/3.3/group__lavu__sampfmts.html#ga0c3c218e1dd570ad4917c69a35a6c77d
            val bufferOutputSize =
                av_get_bytes_per_sample(codec.sample_fmt())

            return iterator {
                while (av_read_frame(formatContext, pkt) >= 0) {
                    if (pkt.stream_index() == audioStreamIndex) {
                        avcodec_send_packet(codec, pkt)
                        error = avcodec_receive_frame(codec, audioFrame)
                    }

                    if (error >= 0) {
                        val decodedAudio =
                            ByteBuffer.allocate(bufferOutputSize * codec.ch_layout().nb_channels())

                        for (ch in 0 until codec.ch_layout().nb_channels()) {
                            decodedAudio.put(
                                audioFrame.data(ch).position(bufferOutputSize.toLong())
                                    .position(bufferOutputSize.toLong())
                                    .asByteBuffer()
                            )
                        }

                        yield(decodedAudio)
                    }

                    // Unreferences the buffer referenced by the packet and
                    // reset the remaining packet fields to their default
                    // values
                    // https://ffmpeg.org/doxygen/trunk/group__lavc__packet.html#ga63d5a489b419bd5d45cfd09091cbcbc2
                    av_packet_unref(pkt)
                }

                // Clean up to prevent memory leak
                av_frame_free(audioFrame)
            }
        }

        return null
    }

    fun videoFrameIterator(
        pixelFormat: Int = AV_PIX_FMT_RGB24,
    ): Iterator<ByteBuffer>? {
        videoCodecContext?.let { codec ->
            val pkt = AVPacket()

            val videoFrame = av_frame_alloc()
            val pFrameRGB = av_frame_alloc()

            // Returns the size in bytes of the amount of data required
            // to store an image
            // https://ffmpeg.org/doxygen/trunk/group__lavu__picture.html#ga24a67963c3ae0054a2a4bab35930e694
            val bufferOutputSize =
                av_image_get_buffer_size(
                    pixelFormat,
                    codec.width(),
                    codec.height(),
                    1,
                ).toLong()

            val buffer = BytePointer(av_malloc(bufferOutputSize))

            // Map deprecated pixel formats to their newest compatible
            // versions
            val sourcePixFmt = correctForDeprecatedPixelFormat(codec.pix_fmt())

            // Allocate and return an SwsContext
            // https://ffmpeg.org/doxygen/trunk/group__libsws.html#gaf360d1a9e0e60f906f74d7d44f9abfdd
            if (swsContext == null) {
                swsContext = sws_getContext(
                    codec.width(),
                    codec.height(),
                    sourcePixFmt,
                    codec.width(),
                    codec.height(),
                    pixelFormat,
                    SWS_BILINEAR,
                    null, null, null as DoublePointer?
                )
            }

            // Setup the data pointers and linesizes based on the image
            // https://ffmpeg.org/doxygen/trunk/group__lavu__picture.html#ga5b6ead346a70342ae8a303c16d2b3629
            av_image_fill_arrays(
                pFrameRGB.data(),       // Destination
                pFrameRGB.linesize(),   // Destination Linesize
                buffer,                     // Source buffer
                pixelFormat,                // Pixel Format
                codec.width(),              // Width
                codec.height(),             // Height
                1,                          // Alignment
            )

            return iterator {
                while (av_read_frame(formatContext, pkt) >= 0) {
                    if (pkt.stream_index() == videoStreamIndex) {
                        avcodec_send_packet(codec, pkt)
                        error = avcodec_receive_frame(codec, videoFrame)
                    }

                    videoPTS = videoFrame.pts()

                    if (error >= 0) {
                        // Scale the image slice into the destination image
                        // https://www.ffmpeg.org/doxygen/2.7/group__libsws.html#gae531c9754c9205d90ad6800015046d74
                        sws_scale(
                            swsContext,               // Scaling context
                            videoFrame.data(),        // Source slice
                            videoFrame.linesize(),    // Source stride
                            0,                        // Position in the source image of the slice to process
                            videoFrame.height(),
                            pFrameRGB.data(),
                            pFrameRGB.linesize(),  // Destination stride
                        )

                        // We need to set the capacity in order to use
                        // `asByteBuffer()`
                        val convertedFrame =
                            pFrameRGB.data(0).capacity(bufferOutputSize).asByteBuffer()

                        yield(convertedFrame)
                    }

                    // Unreferences the buffer referenced by the packet and
                    // reset the remaining packet fields to their default
                    // values
                    // https://ffmpeg.org/doxygen/trunk/group__lavc__packet.html#ga63d5a489b419bd5d45cfd09091cbcbc2
                    av_packet_unref(pkt)
                }

                // Clean up to prevent memory leak
                av_frame_free(videoFrame)
                av_frame_free(pFrameRGB)
            }
        }

        return null
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

    fun close() {
        videoCodecContext?.close()
        audioCodecContext?.close()
        formatContext.close()
    }

    init {
        error = avformat_open_input(formatContext, path, null, null)
        if (error < 0) {
            av_strerror(error, errorBuffer, 1024)
            throw IllegalStateException("Failed to open video file at $path: ${errorBuffer.decodeToString()}")
        }

        error = avformat_find_stream_info(formatContext, null as PointerPointer<*>?)
        if (error < 0) {
            av_strerror(error, errorBuffer, 1024)
            throw IllegalStateException("Failed to find stream info for video file at $path: ${errorBuffer.decodeToString()}")
        }

        av_dump_format(formatContext, 0, path, 0)

        // Setup audio and video codecs
        for (i in 0 until formatContext.nb_streams()) {
            val stream = formatContext.streams(i)

            when (stream.codecpar().codec_type()) {
                AVMEDIA_TYPE_VIDEO -> {
                    if (videoStreamIndex >= 0) continue

                    // Allocate video codec context and set codec parameters
                    videoCodecContext = avcodec_alloc_context3(null)
                    avcodec_parameters_to_context(videoCodecContext, stream.codecpar())

                    val codec = avcodec_find_decoder(videoCodecContext?.codec_id() ?: 0)
                    avcodec_open2(videoCodecContext, codec, null as PointerPointer<*>?)

                    videoStreamIndex = i
                }

                AVMEDIA_TYPE_AUDIO -> {
                    if (audioStreamIndex >= 0) continue

                    // Allocate audio codec context and set codec parameters
                    audioCodecContext = avcodec_alloc_context3(null)
                    avcodec_parameters_to_context(audioCodecContext, stream.codecpar())

                    val codec = avcodec_find_decoder(audioCodecContext?.codec_id() ?: 0)
                    avcodec_open2(audioCodecContext, codec, null as PointerPointer<*>?)

                    audioStreamIndex = i
                }
            }
        }
    }
}
