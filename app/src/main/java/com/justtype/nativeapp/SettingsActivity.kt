package com.justtype.nativeapp

import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import com.justtype.nativeapp.R

class SettingsActivity : AppCompatActivity() {
    
    companion object {
        const val PREFS_NAME = "JustTypePrefs"
        const val KEY_SHOW_WORD_FREQUENCIES = "show_word_frequencies"
        const val KEY_SHOW_BUTTONS_PRESSED = "show_buttons_pressed"
        const val KEY_LAYOUT_MODE = "layout_mode"
        const val MODE_ALPHA = "alpha"
        const val MODE_OPT = "opt"
    }
    
    private lateinit var sharedPreferences: SharedPreferences
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)
        
        sharedPreferences = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        
        val backButton: ImageButton = findViewById(R.id.backButton)
        val wordFrequenciesSwitch: SwitchCompat = findViewById(R.id.showWordFrequenciesSwitch)
        val wordFrequenciesLayout: LinearLayout = findViewById(R.id.wordFrequenciesLayout)
        val showButtonsPressedSwitch: SwitchCompat = findViewById(R.id.showButtonsPressedSwitch)
        val showButtonsPressedLayout: LinearLayout = findViewById(R.id.showButtonsPressedLayout)
        val layoutRadioGroup: RadioGroup = findViewById(R.id.layoutRadioGroup)
        val layoutAlphaRadio: RadioButton = findViewById(R.id.layoutAlphaRadio)
        val layoutOptRadio: RadioButton = findViewById(R.id.layoutOptRadio)
        
        // Load saved preferences (default is false - debug info hidden)
        val showWordFrequencies = sharedPreferences.getBoolean(KEY_SHOW_WORD_FREQUENCIES, false)
        wordFrequenciesSwitch.isChecked = showWordFrequencies
        
        val showButtonsPressed = sharedPreferences.getBoolean(KEY_SHOW_BUTTONS_PRESSED, false)
        showButtonsPressedSwitch.isChecked = showButtonsPressed
        val layoutSaved = sharedPreferences.getString(KEY_LAYOUT_MODE, MODE_ALPHA)
        layoutAlphaRadio.isChecked = layoutSaved == MODE_ALPHA
        layoutOptRadio.isChecked = layoutSaved == MODE_OPT
        
        // Handle back button click
        backButton.setOnClickListener {
            finish()
        }
        
        // Handle word frequencies switch toggle
        wordFrequenciesSwitch.setOnCheckedChangeListener { _, isChecked ->
            sharedPreferences.edit()
                .putBoolean(KEY_SHOW_WORD_FREQUENCIES, isChecked)
                .apply()
        }
        
        // Handle word frequencies layout click to toggle switch
        wordFrequenciesLayout.setOnClickListener {
            wordFrequenciesSwitch.isChecked = !wordFrequenciesSwitch.isChecked
        }
        
        // Handle show buttons pressed switch toggle
        showButtonsPressedSwitch.setOnCheckedChangeListener { _, isChecked ->
            sharedPreferences.edit()
                .putBoolean(KEY_SHOW_BUTTONS_PRESSED, isChecked)
                .apply()
        }
        
        // Handle show buttons pressed layout click to toggle switch
        showButtonsPressedLayout.setOnClickListener {
            showButtonsPressedSwitch.isChecked = !showButtonsPressedSwitch.isChecked
        }

        // Handle layout selection
        layoutRadioGroup.setOnCheckedChangeListener { _, checkedId ->
            val value = if (checkedId == R.id.layoutAlphaRadio) MODE_ALPHA else MODE_OPT
            sharedPreferences.edit().putString(KEY_LAYOUT_MODE, value).apply()
        }
    }
}

