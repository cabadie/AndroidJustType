package com.justtype.nativeapp

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.view.View
import android.widget.Button
import android.widget.ImageButton
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.PopupMenu
import com.justtype.nativeapp.logic.JTUI
import java.util.Locale

class MainActivity : AppCompatActivity(), TextToSpeech.OnInitListener {
    private lateinit var jtui: JTUI
    private var tts: TextToSpeech? = null
    private lateinit var ambigTextView: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        tts = TextToSpeech(this, this)

        val outputText: TextView = findViewById(R.id.outputText)
        ambigTextView = findViewById(R.id.ambigText)
        val selectionList: TextView = findViewById(R.id.selectionList)
        val centerLabel: TextView = findViewById(R.id.centerLabel)

        val buttons = listOf(
            findViewById<Button>(R.id.btn0),
            findViewById<Button>(R.id.btn1),
            findViewById<Button>(R.id.btn2),
            findViewById<Button>(R.id.btn3),
            findViewById<Button>(R.id.btn4),
            findViewById<Button>(R.id.btn5),
            findViewById<Button>(R.id.btn6),
            findViewById<Button>(R.id.btn7)
        ).onEach { btn ->
            btn.typeface = android.graphics.Typeface.MONOSPACE
            btn.textAlignment = android.view.View.TEXT_ALIGNMENT_CENTER
        }

        jtui = JTUI(
            say = { text -> speak(text) },
            onUiUpdate = { ui ->
                runOnUiThread {
                    outputText.text = ui.outputBuffer
                    ambigTextView.text = ui.ambigBuffer
                    selectionList.text = ui.selectionListBuffer
                    centerLabel.text = ui.centerSpace
                    buttons.forEachIndexed { index, button ->
                        button.text = ui.keyLabels.getOrNull(index) ?: ""
                    }
                }
            },
            assets = assets
        )

        jtui.init()

        buttons.forEachIndexed { index, button ->
            button.setOnClickListener { jtui.buttonPressed(index) }
        }
        
        // Setup hamburger menu button
        val menuButton: ImageButton = findViewById(R.id.menuButton)
        menuButton.setOnClickListener { view ->
            val popup = PopupMenu(this, view)
            popup.menuInflater.inflate(R.menu.main_menu, popup.menu)
            popup.setOnMenuItemClickListener { item ->
                when (item.itemId) {
                    R.id.action_settings -> {
                        val intent = Intent(this, SettingsActivity::class.java)
                        startActivity(intent)
                        true
                    }
                    else -> false
                }
            }
            popup.show()
        }
        
        // Load and apply preference
        loadAndApplyPreferences()
    }
    
    override fun onResume() {
        super.onResume()
        // Reload preferences when returning from settings
        loadAndApplyPreferences()
    }
    
    private fun loadAndApplyPreferences() {
        val prefs = getSharedPreferences(SettingsActivity.PREFS_NAME, Context.MODE_PRIVATE)
        val showWordFrequencies = prefs.getBoolean(SettingsActivity.KEY_SHOW_WORD_FREQUENCIES, false)
        jtui.showWordFrequencies = showWordFrequencies
        
        val showButtonsPressed = prefs.getBoolean(SettingsActivity.KEY_SHOW_BUTTONS_PRESSED, false)
        ambigTextView.visibility = if (showButtonsPressed) View.VISIBLE else View.GONE

        // Apply layout mode
        val layoutPref = prefs.getString(SettingsActivity.KEY_LAYOUT_MODE, SettingsActivity.MODE_ALPHA)
        val mode = if (layoutPref == SettingsActivity.MODE_ALPHA)
            com.justtype.nativeapp.logic.LayoutMode.Alphabetical
        else
            com.justtype.nativeapp.logic.LayoutMode.Optimized
        jtui.layoutMode = mode
    }

    private fun speak(text: String) {
        val e = tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "jtui")
    }

    override fun onDestroy() {
        tts?.shutdown()
        super.onDestroy()
    }

    override fun onInit(status: Int) {
        tts?.language = Locale.getDefault()
    }
}
