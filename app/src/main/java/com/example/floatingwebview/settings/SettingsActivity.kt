package com.example.floatingwebview.settings

import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.example.floatingwebview.R
import com.example.floatingwebview.databinding.ActivitySettingsBinding

class SettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySettingsBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val toolbar = binding.settingsToolbar
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "Settings"
        toolbar.setNavigationOnClickListener { finish() }

        // Using SharedPreferences for storing and retrieving user settings
        val sharedPreferences = getSharedPreferences("settings", MODE_PRIVATE)

        val savedBehavior = sharedPreferences.getString("behavior", "floating")
        if(savedBehavior == "inapp") {
            binding.radioButtonInapp.isChecked = true
        } else {
            binding.radioButtonFloating.isChecked = true
        }

        binding.radioGroupBehavior.setOnCheckedChangeListener { group, checkedId ->
            val editor = sharedPreferences.edit()
            when(checkedId) {
                R.id.radioButtonFloating -> editor.putString("behavior", "floating")
                R.id.radioButtonInapp -> editor.putString("behavior", "inapp")
            }
            editor.apply()
        }
    }
}