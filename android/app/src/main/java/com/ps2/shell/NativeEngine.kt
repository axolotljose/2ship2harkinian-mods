package com.ps2.shell

import android.view.Surface

object NativeEngine {
    init {
        System.loadLibrary("ps2runtime")
    }

    external fun init(dataPath: String?): Int
    external fun shutdown()
    external fun runFrame()
    external fun sendInput(
        buttons: Int,
        lstickX: Float, lstickY: Float,
        rstickX: Float, rstickY: Float,
        l2Pressure: Int, r2Pressure: Int
    )
    external fun onSurfaceCreated(surface: Surface?, width: Int, height: Int, backend: Int): Int
    external fun onSurfaceChanged(width: Int, height: Int)
    external fun onSurfaceDestroyed()
    external fun fillAudioBuffer(buffer: ShortArray, numSamples: Int): Int
    external fun readFile(relativePath: String): ByteArray?
}
