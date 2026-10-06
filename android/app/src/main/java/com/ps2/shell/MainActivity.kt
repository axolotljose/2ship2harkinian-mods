package com.ps2.shell

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.widget.Button
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity(), SurfaceHolder.Callback {

    private lateinit var surfaceView: SurfaceView
    private lateinit var touchOverlay: TouchOverlayView
    private val controllerMapper = ControllerInputMapper()

    private var renderThread: Thread? = null
    @Volatile
    private var isRendering = false

    private var currentProfile: GameProfile? = null

    private val folderPickerLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        if (uri != null) {
            SafManager.persistUriPermission(this, uri)
            val resolvedPath = SafManager.getPathFromUri(this, uri)
            Toast.makeText(this, "Selected: $resolvedPath", Toast.LENGTH_SHORT).show()

            val profileId = uri.toString().hashCode().toString()
            var profile = GameProfileManager.getProfile(this, profileId)
            if (profile == null) {
                profile = GameProfile(
                    gameId = profileId,
                    title = uri.lastPathSegment ?: "Game",
                    folderUri = uri.toString(),
                    layoutJson = TouchLayoutSerializer.serialize(touchOverlay)
                )
            } else {
                profile.folderUri = uri.toString()
            }

            GameProfileManager.saveProfile(this, profile)
            currentProfile = profile
            NativeEngine.init(resolvedPath)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        surfaceView = findViewById(R.id.gameSurfaceView)
        touchOverlay = findViewById(R.id.touchOverlayView)

        surfaceView.holder.addCallback(this)

        // Connect TouchOverlay input to NativeEngine
        touchOverlay.inputListener = object : TouchOverlayView.InputListener {
            override fun onInputChanged(
                buttons: Int,
                lstickX: Float, lstickY: Float,
                rstickX: Float, rstickY: Float,
                l2Pressure: Int, r2Pressure: Int
            ) {
                // Merge touch overlay buttons with physical controller inputs
                val combinedButtons = buttons or controllerMapper.buttonsBitmask
                val finalLstickX = if (lstickX != 0.0f) lstickX else controllerMapper.lstickX
                val finalLstickY = if (lstickY != 0.0f) lstickY else controllerMapper.lstickY
                val finalRstickX = if (rstickX != 0.0f) rstickX else controllerMapper.rstickX
                val finalRstickY = if (rstickY != 0.0f) rstickY else controllerMapper.rstickY
                val finalL2 = maxOf(l2Pressure, controllerMapper.l2Pressure)
                val finalR2 = maxOf(r2Pressure, controllerMapper.r2Pressure)

                NativeEngine.sendInput(
                    combinedButtons,
                    finalLstickX, finalLstickY,
                    finalRstickX, finalRstickY,
                    finalL2, finalR2
                )
            }
        }

        findViewById<Button>(R.id.btnSelectFolder).setOnClickListener {
            folderPickerLauncher.launch(null)
        }

        findViewById<Button>(R.id.btnEditTouch).setOnClickListener {
            touchOverlay.isEditMode = !touchOverlay.isEditMode
            val btn = it as Button
            if (touchOverlay.isEditMode) {
                btn.text = "Save Layout"
            } else {
                btn.text = "Edit Layout"
                // Save updated touch layout to active profile
                currentProfile?.let { prof ->
                    prof.layoutJson = TouchLayoutSerializer.serialize(touchOverlay)
                    GameProfileManager.saveProfile(this, prof)
                }
            }
        }

        findViewById<Button>(R.id.btnSettings).setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }

        // Initialize default runtime with empty path until folder chosen
        NativeEngine.init("")
    }

    override fun surfaceCreated(holder: SurfaceHolder) {
        val width = surfaceView.width
        val height = surfaceView.height
        val backend = currentProfile?.graphicsBackend ?: 0
        NativeEngine.onSurfaceCreated(holder.surface, width, height, backend)

        isRendering = true
        renderThread = Thread {
            while (isRendering) {
                NativeEngine.runFrame()
                try {
                    Thread.sleep(16)
                } catch (_: InterruptedException) {}
            }
        }.apply { start() }
    }

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
        NativeEngine.onSurfaceChanged(width, height)
    }

    override fun surfaceDestroyed(holder: SurfaceHolder) {
        isRendering = false
        renderThread?.join()
        renderThread = null
        NativeEngine.onSurfaceDestroyed()
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (controllerMapper.handleKeyEvent(event)) {
            touchOverlay.inputListener?.onInputChanged(
                0, 0f, 0f, 0f, 0f, 0, 0
            )
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean {
        if (controllerMapper.handleKeyEvent(event)) {
            touchOverlay.inputListener?.onInputChanged(
                0, 0f, 0f, 0f, 0f, 0, 0
            )
            return true
        }
        return super.onKeyUp(keyCode, event)
    }

    override fun onGenericMotionEvent(event: MotionEvent): Boolean {
        if (controllerMapper.handleGenericMotionEvent(event)) {
            touchOverlay.inputListener?.onInputChanged(
                0, 0f, 0f, 0f, 0f, 0, 0
            )
            return true
        }
        return super.onGenericMotionEvent(event)
    }

    override fun onDestroy() {
        super.onDestroy()
        NativeEngine.shutdown()
    }
}
