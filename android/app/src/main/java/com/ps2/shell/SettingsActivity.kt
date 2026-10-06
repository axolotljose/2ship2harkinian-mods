package com.ps2.shell

import android.os.Bundle
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class SettingsActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        val btnResetLayout = findViewById<Button>(R.id.btnResetLayout)
        val btnSaveSettings = findViewById<Button>(R.id.btnSaveSettings)

        btnResetLayout.setOnClickListener {
            Toast.makeText(this, "Touch layout reset to default", Toast.LENGTH_SHORT).show()
        }

        btnSaveSettings.setOnClickListener {
            Toast.makeText(this, "Settings saved", Toast.LENGTH_SHORT).show()
            finish()
        }
    }
}
