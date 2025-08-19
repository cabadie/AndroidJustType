package com.justtype.nativeapp

import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.justtype.nativeapp.logic.JTUI
import java.util.Locale

class MainActivity : AppCompatActivity(), TextToSpeech.OnInitListener {
    private lateinit var jtui: JTUI
    private var tts: TextToSpeech? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        tts = TextToSpeech(this, this)

        val outputText: TextView = findViewById(R.id.outputText)
        val ambigText: TextView = findViewById(R.id.ambigText)
        val selectionList: TextView = findViewById(R.id.selectionList)
        val keyHistory: TextView = findViewById(R.id.keyHistory)
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
        )

        jtui = JTUI(
            say = { text -> speak(text) },
            onUiUpdate = { ui ->
                runOnUiThread {
                    outputText.text = ui.outputBuffer
                    ambigText.text = ui.ambigBuffer
                    selectionList.text = ui.selectionListBuffer
                    keyHistory.text = ui.keyHistoryBuffer
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
