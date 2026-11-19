package com.pocketmultitool.app.hardware.tools

import android.annotation.SuppressLint
import android.content.Context
import android.hardware.camera2.CameraAccessException
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.os.Handler
import android.os.Looper
import kotlin.math.max

class TorchMorse(context: Context, private val onFlash: (Boolean) -> Unit) {
    private val cameraManager = context.applicationContext.getSystemService(Context.CAMERA_SERVICE) as? CameraManager
    private val handler = Handler(Looper.getMainLooper())
    private var thread: Thread? = null
    private var running = false
    private var torchId: String? = null
    private var currentOn = false

    init {
        torchId = findTorch()
    }

    val available: Boolean get() = torchId != null

    fun status(): String = torchId?.let { "ВСПЫШКА: камера $it" } ?: "ВСПЫШКА: отсутствует"

    fun play(text: String, dotMs: Int = 90) {
        stop()
        val id = torchId ?: return
        running = true
        thread = Thread {
            try {
                for (char in text.uppercase()) {
                    if (!running) break
                    if (char == ' ') {
                        Thread.sleep(dotMs * 4L)
                        continue
                    }
                    val code = MorseAlphabet[char] ?: continue
                    for (symbol in code) {
                        if (!running) break
                        setTorch(id, true, onFlash)
                        Thread.sleep((if (symbol == '.') dotMs else dotMs * 3).toLong())
                        setTorch(id, false, onFlash)
                        Thread.sleep(dotMs.toLong())
                    }
                    Thread.sleep(max(dotMs * 2, 100).toLong())
                }
            } catch (_: InterruptedException) {
            } finally {
                setTorch(id, false, onFlash)
                running = false
            }
        }.also { it.start() }
    }

    fun stop() {
        running = false
        thread?.interrupt()
        thread = null
        torchId?.let { setTorch(it, false, onFlash) }
    }

    @SuppressLint("MissingPermission")
    private fun findTorch(): String? = try {
        cameraManager?.cameraIdList?.firstOrNull { id ->
            cameraManager.getCameraCharacteristics(id).get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
        }
    } catch (_: Exception) {
        null
    }

    @SuppressLint("MissingPermission")
    private fun setTorch(id: String, enabled: Boolean, callback: (Boolean) -> Unit) {
        if (enabled == currentOn) return
        try {
            cameraManager?.setTorchMode(id, enabled)
            currentOn = enabled
            handler.post { callback(enabled) }
        } catch (e: CameraAccessException) {
            handler.post { callback(false) }
        } catch (_: SecurityException) {
            handler.post { callback(false) }
        }
    }
}
