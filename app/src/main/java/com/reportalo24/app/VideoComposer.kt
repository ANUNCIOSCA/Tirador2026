package com.reportalo24.app

import android.content.Context
import android.graphics.Bitmap
import android.graphics.SurfaceTexture
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import android.net.Uri
import android.opengl.EGL14
import android.opengl.EGLConfig
import android.opengl.EGLContext
import android.opengl.EGLDisplay
import android.opengl.EGLExt
import android.opengl.EGLSurface
import android.opengl.GLES11Ext
import android.opengl.GLES20
import android.opengl.GLUtils
import android.opengl.Matrix
import android.view.Surface
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer

/**
 * Pone la placa (overlay transparente) encima de un video y recodifica a MP4.
 * Decodifica con MediaCodec, dibuja con OpenGL (video + overlay) y vuelve a codificar;
 * el audio se copia tal cual. Devuelve true si salió bien.
 */
object VideoComposer {

    private const val TIMEOUT_US = 10000L
    private const val MIME_OUT = "video/avc"

    suspend fun procesar(
        context: Context,
        inputUri: Uri,
        salida: File,
        overlayProvider: (w: Int, h: Int) -> Bitmap,
        onProgress: (Float) -> Unit,
    ): Boolean = withContext(Dispatchers.Default) {
        var extractor: MediaExtractor? = null
        var decoder: MediaCodec? = null
        var encoder: MediaCodec? = null
        var muxer: MediaMuxer? = null
        var inputSurface: InputSurface? = null
        var outputSurface: OutputSurface? = null
        try {
            extractor = MediaExtractor()
            context.contentResolver.openFileDescriptor(inputUri, "r")!!.use { pfd ->
                extractor!!.setDataSource(pfd.fileDescriptor)
            }
            val videoTrack = selectTrack(extractor!!, "video/")
            require(videoTrack >= 0) { "El archivo no tiene pista de video" }
            extractor!!.selectTrack(videoTrack)
            val inFormat = extractor!!.getTrackFormat(videoTrack)

            val rot = if (inFormat.containsKey(MediaFormat.KEY_ROTATION)) inFormat.getInteger(MediaFormat.KEY_ROTATION) else 0
            val w = inFormat.getInteger(MediaFormat.KEY_WIDTH)
            val h = inFormat.getInteger(MediaFormat.KEY_HEIGHT)
            var dispW = if (rot == 90 || rot == 270) h else w
            var dispH = if (rot == 90 || rot == 270) w else h
            // Escala para que el lado mayor no pase de 1920 (encode viable)
            val maxDim = maxOf(dispW, dispH)
            if (maxDim > 1920) {
                val f = 1920f / maxDim
                dispW = (dispW * f).toInt(); dispH = (dispH * f).toInt()
            }
            dispW = dispW / 2 * 2; dispH = dispH / 2 * 2
            val durationUs = if (inFormat.containsKey(MediaFormat.KEY_DURATION)) inFormat.getLong(MediaFormat.KEY_DURATION) else 0L

            // Overlay a la medida del video (orientación de visualización)
            val overlay = overlayProvider(dispW, dispH)

            // Encoder
            val outFormat = MediaFormat.createVideoFormat(MIME_OUT, dispW, dispH).apply {
                setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
                setInteger(MediaFormat.KEY_BIT_RATE, (dispW.toLong() * dispH * 5).toInt().coerceAtLeast(3_000_000))
                setInteger(MediaFormat.KEY_FRAME_RATE, 30)
                setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)
            }
            encoder = MediaCodec.createEncoderByType(MIME_OUT)
            encoder!!.configure(outFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            inputSurface = InputSurface(encoder!!.createInputSurface())
            inputSurface!!.makeCurrent()
            encoder!!.start()

            // Render + SurfaceTexture para la salida del decoder
            outputSurface = OutputSurface(overlay, rot)

            // Decoder
            val videoMime = inFormat.getString(MediaFormat.KEY_MIME)!!
            decoder = MediaCodec.createDecoderByType(videoMime)
            decoder!!.configure(inFormat, outputSurface!!.surface, null, 0)
            decoder!!.start()

            muxer = MediaMuxer(salida.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)

            val ok = doTranscode(extractor!!, decoder!!, encoder!!, inputSurface!!, outputSurface!!, muxer!!, durationUs, onProgress)
            if (!ok) return@withContext false

            // Audio (copia directa)
            copyAudio(context, inputUri, muxer!!)

            muxer!!.stop()
            overlay.recycle()
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        } finally {
            runCatching { decoder?.stop() }; runCatching { decoder?.release() }
            runCatching { encoder?.stop() }; runCatching { encoder?.release() }
            runCatching { muxer?.release() }
            runCatching { inputSurface?.release() }
            runCatching { outputSurface?.release() }
            runCatching { extractor?.release() }
        }
    }

    private fun doTranscode(
        extractor: MediaExtractor,
        decoder: MediaCodec,
        encoder: MediaCodec,
        inputSurface: InputSurface,
        outputSurface: OutputSurface,
        muxer: MediaMuxer,
        durationUs: Long,
        onProgress: (Float) -> Unit,
    ): Boolean {
        val bufferInfo = MediaCodec.BufferInfo()
        var muxerVideoTrack = -1
        var muxerStarted = false
        var inputDone = false
        var decoderDone = false

        while (true) {
            // 1. Alimentar el decoder
            if (!inputDone) {
                val inIndex = decoder.dequeueInputBuffer(TIMEOUT_US)
                if (inIndex >= 0) {
                    val buf = decoder.getInputBuffer(inIndex)!!
                    val size = extractor.readSampleData(buf, 0)
                    if (size < 0) {
                        decoder.queueInputBuffer(inIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                        inputDone = true
                    } else {
                        decoder.queueInputBuffer(inIndex, 0, size, extractor.sampleTime, 0)
                        extractor.advance()
                    }
                }
            }

            // 2. Drenar encoder → muxer
            var encoderBusy = true
            while (encoderBusy) {
                val outIndex = encoder.dequeueOutputBuffer(bufferInfo, 0)
                when {
                    outIndex == MediaCodec.INFO_TRY_AGAIN_LATER -> encoderBusy = false
                    outIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        muxerVideoTrack = muxer.addTrack(encoder.outputFormat)
                        muxer.start()
                        muxerStarted = true
                    }
                    outIndex >= 0 -> {
                        val data = encoder.getOutputBuffer(outIndex)!!
                        if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0) bufferInfo.size = 0
                        if (bufferInfo.size > 0 && muxerStarted) {
                            data.position(bufferInfo.offset)
                            data.limit(bufferInfo.offset + bufferInfo.size)
                            muxer.writeSampleData(muxerVideoTrack, data, bufferInfo)
                            if (durationUs > 0) onProgress((bufferInfo.presentationTimeUs.toFloat() / durationUs).coerceIn(0f, 1f))
                        }
                        encoder.releaseOutputBuffer(outIndex, false)
                        if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) return true
                    }
                }
            }

            // 3. Drenar decoder → GL → encoder
            if (!decoderDone) {
                val outIndex = decoder.dequeueOutputBuffer(bufferInfo, TIMEOUT_US)
                if (outIndex >= 0) {
                    val render = bufferInfo.size != 0
                    val eos = bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0
                    decoder.releaseOutputBuffer(outIndex, render)
                    if (render) {
                        outputSurface.awaitNewImage()
                        outputSurface.drawImage()
                        inputSurface.setPresentationTime(bufferInfo.presentationTimeUs * 1000)
                        inputSurface.swapBuffers()
                    }
                    if (eos) {
                        encoder.signalEndOfInputStream()
                        decoderDone = true
                    }
                }
            }
        }
    }

    private fun copyAudio(context: Context, inputUri: Uri, muxer: MediaMuxer) {
        val extractor = MediaExtractor()
        try {
            context.contentResolver.openFileDescriptor(inputUri, "r")!!.use { pfd ->
                extractor.setDataSource(pfd.fileDescriptor)
            }
            val audioTrack = selectTrack(extractor, "audio/")
            if (audioTrack < 0) return
            extractor.selectTrack(audioTrack)
            val format = extractor.getTrackFormat(audioTrack)
            val muxerAudio = muxer.addTrack(format)
            val maxSize = if (format.containsKey(MediaFormat.KEY_MAX_INPUT_SIZE)) format.getInteger(MediaFormat.KEY_MAX_INPUT_SIZE) else 256 * 1024
            val buffer = ByteBuffer.allocate(maxSize)
            val info = MediaCodec.BufferInfo()
            while (true) {
                val size = extractor.readSampleData(buffer, 0)
                if (size < 0) break
                info.offset = 0
                info.size = size
                info.presentationTimeUs = extractor.sampleTime
                info.flags = if (extractor.sampleFlags and MediaExtractor.SAMPLE_FLAG_SYNC != 0) MediaCodec.BUFFER_FLAG_KEY_FRAME else 0
                muxer.writeSampleData(muxerAudio, buffer, info)
                extractor.advance()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            runCatching { extractor.release() }
        }
    }

    private fun selectTrack(extractor: MediaExtractor, prefix: String): Int {
        for (i in 0 until extractor.trackCount) {
            val mime = extractor.getTrackFormat(i).getString(MediaFormat.KEY_MIME) ?: continue
            if (mime.startsWith(prefix)) return i
        }
        return -1
    }
}

// ===========================================================================
// EGL: superficie de entrada del encoder
// ===========================================================================
private class InputSurface(private val surface: Surface) {
    private var eglDisplay: EGLDisplay = EGL14.EGL_NO_DISPLAY
    private var eglContext: EGLContext = EGL14.EGL_NO_CONTEXT
    private var eglSurface: EGLSurface = EGL14.EGL_NO_SURFACE

    init {
        eglDisplay = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
        val version = IntArray(2)
        EGL14.eglInitialize(eglDisplay, version, 0, version, 1)
        val attribList = intArrayOf(
            EGL14.EGL_RED_SIZE, 8, EGL14.EGL_GREEN_SIZE, 8, EGL14.EGL_BLUE_SIZE, 8,
            EGL14.EGL_ALPHA_SIZE, 8, EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT,
            0x3142, 1, EGL14.EGL_NONE,
        )
        val configs = arrayOfNulls<EGLConfig>(1)
        val numConfigs = IntArray(1)
        EGL14.eglChooseConfig(eglDisplay, attribList, 0, configs, 0, 1, numConfigs, 0)
        val ctxAttrib = intArrayOf(EGL14.EGL_CONTEXT_CLIENT_VERSION, 2, EGL14.EGL_NONE)
        eglContext = EGL14.eglCreateContext(eglDisplay, configs[0], EGL14.EGL_NO_CONTEXT, ctxAttrib, 0)
        val surfAttrib = intArrayOf(EGL14.EGL_NONE)
        eglSurface = EGL14.eglCreateWindowSurface(eglDisplay, configs[0], surface, surfAttrib, 0)
    }

    fun makeCurrent() { EGL14.eglMakeCurrent(eglDisplay, eglSurface, eglSurface, eglContext) }
    fun swapBuffers(): Boolean = EGL14.eglSwapBuffers(eglDisplay, eglSurface)
    fun setPresentationTime(nsecs: Long) { EGLExt.eglPresentationTimeANDROID(eglDisplay, eglSurface, nsecs) }

    fun release() {
        if (eglDisplay != EGL14.EGL_NO_DISPLAY) {
            EGL14.eglMakeCurrent(eglDisplay, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT)
            EGL14.eglDestroySurface(eglDisplay, eglSurface)
            EGL14.eglDestroyContext(eglDisplay, eglContext)
            EGL14.eglReleaseThread()
            EGL14.eglTerminate(eglDisplay)
        }
        surface.release()
        eglDisplay = EGL14.EGL_NO_DISPLAY
    }
}

// ===========================================================================
// Salida del decoder (SurfaceTexture) + dibujo de video OES + overlay
// ===========================================================================
private class OutputSurface(overlay: Bitmap, rotation: Int) : SurfaceTexture.OnFrameAvailableListener {
    val surface: Surface
    private val surfaceTexture: SurfaceTexture
    private val lock = Object()
    private var frameAvailable = false
    private val renderer = TextureRender(overlay, rotation)

    init {
        renderer.surfaceCreated()
        surfaceTexture = SurfaceTexture(renderer.textureId)
        surfaceTexture.setOnFrameAvailableListener(this)
        surface = Surface(surfaceTexture)
    }

    fun awaitNewImage() {
        synchronized(lock) {
            val start = System.currentTimeMillis()
            while (!frameAvailable) {
                lock.wait(2000)
                if (!frameAvailable && System.currentTimeMillis() - start > 2000) break
            }
            frameAvailable = false
        }
        surfaceTexture.updateTexImage()
    }

    fun drawImage() = renderer.drawFrame(surfaceTexture)

    override fun onFrameAvailable(st: SurfaceTexture?) {
        synchronized(lock) { frameAvailable = true; lock.notifyAll() }
    }

    fun release() {
        surface.release()
        surfaceTexture.release()
        renderer.release()
    }
}

// ===========================================================================
// Programa GL: dibuja la textura externa (video) rotada + el overlay encima
// ===========================================================================
private class TextureRender(private val overlay: Bitmap, private val rotation: Int) {
    var textureId = 0; private set
    private var overlayTexId = 0
    private var videoProgram = 0
    private var overlayProgram = 0
    private val stMatrix = FloatArray(16)
    private val rotMatrix = FloatArray(16)
    private val finalMatrix = FloatArray(16)
    private val mvp = FloatArray(16)

    private val quad: FloatBuffer = floatBuffer(floatArrayOf(-1f, -1f, 1f, -1f, -1f, 1f, 1f, 1f))
    private val texVideo: FloatBuffer = floatBuffer(floatArrayOf(0f, 0f, 1f, 0f, 0f, 1f, 1f, 1f))
    private val texOverlay: FloatBuffer = floatBuffer(floatArrayOf(0f, 1f, 1f, 1f, 0f, 0f, 1f, 0f))

    private val vsVideo = "uniform mat4 uMVP;uniform mat4 uST;attribute vec4 aPos;attribute vec4 aTex;varying vec2 vTex;void main(){gl_Position=uMVP*aPos;vTex=(uST*aTex).xy;}"
    private val fsVideo = "#extension GL_OES_EGL_image_external:require\nprecision mediump float;varying vec2 vTex;uniform samplerExternalOES sTex;void main(){gl_FragColor=texture2D(sTex,vTex);}"
    private val vsOverlay = "attribute vec4 aPos;attribute vec2 aTex;varying vec2 vTex;void main(){gl_Position=aPos;vTex=aTex;}"
    private val fsOverlay = "precision mediump float;varying vec2 vTex;uniform sampler2D uTex;void main(){gl_FragColor=texture2D(uTex,vTex);}"

    fun surfaceCreated() {
        videoProgram = buildProgram(vsVideo, fsVideo)
        overlayProgram = buildProgram(vsOverlay, fsOverlay)
        val tex = IntArray(1)
        GLES20.glGenTextures(1, tex, 0)
        textureId = tex[0]
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, textureId)
        GLES20.glTexParameterf(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR.toFloat())
        GLES20.glTexParameterf(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR.toFloat())
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)

        val otex = IntArray(1)
        GLES20.glGenTextures(1, otex, 0)
        overlayTexId = otex[0]
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, overlayTexId)
        GLES20.glTexParameterf(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR.toFloat())
        GLES20.glTexParameterf(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR.toFloat())
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
        GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, overlay, 0)

        Matrix.setIdentityM(mvp, 0)
    }

    fun drawFrame(st: SurfaceTexture) {
        st.getTransformMatrix(stMatrix)
        Matrix.setIdentityM(rotMatrix, 0)
        if (rotation != 0) {
            Matrix.translateM(rotMatrix, 0, 0.5f, 0.5f, 0f)
            Matrix.rotateM(rotMatrix, 0, rotation.toFloat(), 0f, 0f, 1f)
            Matrix.translateM(rotMatrix, 0, -0.5f, -0.5f, 0f)
        }
        Matrix.multiplyMM(finalMatrix, 0, stMatrix, 0, rotMatrix, 0)

        GLES20.glClearColor(0f, 0f, 0f, 1f)
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)

        // Video (textura externa)
        GLES20.glUseProgram(videoProgram)
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, textureId)
        bindAttrib(videoProgram, "aPos", quad, 2)
        bindAttrib(videoProgram, "aTex", texVideo, 2)
        GLES20.glUniformMatrix4fv(GLES20.glGetUniformLocation(videoProgram, "uMVP"), 1, false, mvp, 0)
        GLES20.glUniformMatrix4fv(GLES20.glGetUniformLocation(videoProgram, "uST"), 1, false, finalMatrix, 0)
        GLES20.glUniform1i(GLES20.glGetUniformLocation(videoProgram, "sTex"), 0)
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)

        // Overlay (textura 2D con transparencia)
        GLES20.glEnable(GLES20.GL_BLEND)
        GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA)
        GLES20.glUseProgram(overlayProgram)
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, overlayTexId)
        bindAttrib(overlayProgram, "aPos", quad, 2)
        bindAttrib(overlayProgram, "aTex", texOverlay, 2)
        GLES20.glUniform1i(GLES20.glGetUniformLocation(overlayProgram, "uTex"), 0)
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
        GLES20.glDisable(GLES20.GL_BLEND)

        GLES20.glFinish()
    }

    private fun bindAttrib(program: Int, name: String, buf: FloatBuffer, size: Int) {
        val loc = GLES20.glGetAttribLocation(program, name)
        buf.position(0)
        GLES20.glVertexAttribPointer(loc, size, GLES20.GL_FLOAT, false, 0, buf)
        GLES20.glEnableVertexAttribArray(loc)
    }

    fun release() {
        GLES20.glDeleteProgram(videoProgram)
        GLES20.glDeleteProgram(overlayProgram)
    }

    private fun floatBuffer(data: FloatArray): FloatBuffer =
        ByteBuffer.allocateDirect(data.size * 4).order(ByteOrder.nativeOrder()).asFloatBuffer().apply { put(data); position(0) }

    private fun buildProgram(vs: String, fs: String): Int {
        val v = compile(GLES20.GL_VERTEX_SHADER, vs)
        val f = compile(GLES20.GL_FRAGMENT_SHADER, fs)
        val p = GLES20.glCreateProgram()
        GLES20.glAttachShader(p, v); GLES20.glAttachShader(p, f); GLES20.glLinkProgram(p)
        return p
    }

    private fun compile(type: Int, src: String): Int {
        val s = GLES20.glCreateShader(type)
        GLES20.glShaderSource(s, src); GLES20.glCompileShader(s)
        return s
    }
}
