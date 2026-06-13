package com.example.floatingwebview.settings

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.floatingwebview.R
import com.example.floatingwebview.databinding.ActivitySettingsBinding

class SettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySettingsBinding

    companion object {
        const val PREFS_NAME = "settings"
        const val KEY_HOMEPAGE = "homepage"
        const val KEY_BEHAVIOR = "behavior"
        const val DEFAULT_HOMEPAGE = "https://www.google.com"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // --- Toolbar ---
        val toolbar = binding.settingsToolbar
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "Settings"
        toolbar.setNavigationOnClickListener { finish() }

        // --- SharedPreferences ---
        val sharedPreferences = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)

        // --- Load & display saved homepage ---
        val savedHomepage = sharedPreferences.getString(KEY_HOMEPAGE, DEFAULT_HOMEPAGE) ?: DEFAULT_HOMEPAGE
        binding.homepageEditText.setText(savedHomepage)

        // --- Save homepage on button click ---
        binding.saveHomepageButton.setOnClickListener {
            val input = binding.homepageEditText.text.toString().trim()
            val homepageUrl = when {
                input.isEmpty() -> DEFAULT_HOMEPAGE
                input.startsWith("http://") || input.startsWith("https://") -> input
                else -> "https://$input"
            }
            sharedPreferences.edit().putString(KEY_HOMEPAGE, homepageUrl).apply()
            binding.homepageEditText.setText(homepageUrl)
            Toast.makeText(this, "Homepage saved: $homepageUrl", Toast.LENGTH_SHORT).show()
        }

        // --- Load & display browser behavior setting ---
        val savedBehavior = sharedPreferences.getString(KEY_BEHAVIOR, "floating")
        if (savedBehavior == "inapp") {
            binding.radioButtonInapp.isChecked = true
        } else {
            binding.radioButtonFloating.isChecked = true
        }

        // --- Save behavior on radio change ---
        binding.radioGroupBehavior.setOnCheckedChangeListener { _, checkedId ->
            val editor = sharedPreferences.edit()
            when (checkedId) {
                R.id.radioButtonFloating -> editor.putString(KEY_BEHAVIOR, "floating")
                R.id.radioButtonInapp -> editor.putString(KEY_BEHAVIOR, "inapp")
            }
            editor.apply()
        }
    }
}