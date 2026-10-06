package com.ps2.shell

import android.view.KeyEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class InputAndProfileTest {

    @Test
    fun testControllerInputMapperKeyMapping() {
        val mapper = ControllerInputMapper()

        mapper.handleKeyCode(KeyEvent.KEYCODE_BUTTON_A, KeyEvent.ACTION_DOWN)
        assertEquals(NativeConstants.PS2_BUTTON_CROSS, mapper.buttonsBitmask and NativeConstants.PS2_BUTTON_CROSS)

        mapper.handleKeyCode(KeyEvent.KEYCODE_BUTTON_B, KeyEvent.ACTION_DOWN)
        assertEquals(NativeConstants.PS2_BUTTON_CIRCLE, mapper.buttonsBitmask and NativeConstants.PS2_BUTTON_CIRCLE)

        mapper.handleKeyCode(KeyEvent.KEYCODE_BUTTON_A, KeyEvent.ACTION_UP)
        assertEquals(0, mapper.buttonsBitmask and NativeConstants.PS2_BUTTON_CROSS)
    }

    @Test
    fun testTouchLayoutSerializer() {
        val jsonStr = """
        {
          "globalOpacity": 0.75,
          "hapticsEnabled": true,
          "elements": [
            {
              "id": "cross",
              "xNorm": 0.8,
              "yNorm": 0.4,
              "scale": 1.2,
              "opacity": 0.9
            },
            {
              "id": "lstick",
              "xNorm": 0.2,
              "yNorm": 0.7,
              "scale": 1.0,
              "opacity": 0.8
            }
          ]
        }
        """.trimIndent()

        val parsed = TouchLayoutSerializer.deserialize(jsonStr)

        assertEquals(0.75f, parsed.globalOpacity, 0.01f)
        assertTrue(parsed.hapticsEnabled)
        assertEquals(2, parsed.elements.size)

        val crossElem = parsed.elements.find { it.id == "cross" }
        assertNotNull(crossElem)
        assertEquals(0.8f, crossElem!!.xNorm, 0.01f)
        assertEquals(1.2f, crossElem.scale, 0.01f)
    }

    @Test
    fun testGameProfileModel() {
        val profile = GameProfile(
            gameId = "game_123",
            title = "Grand Theft Auto",
            folderUri = "content://com.android.externalstorage.documents/tree/primary%3AGames%2FGTA",
            layoutJson = "{}",
            graphicsBackend = 0
        )

        assertEquals("game_123", profile.gameId)
        assertEquals("Grand Theft Auto", profile.title)
        assertEquals(0, profile.graphicsBackend)
    }
}
