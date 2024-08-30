package com.a101apps.endgame

import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.navigation.findNavController
import androidx.navigation.ui.setupWithNavController
import com.google.android.material.bottomnavigation.BottomNavigationView

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Handle night mode for navigation and status bar colors
        handleNightMode()

        val navView: BottomNavigationView = findViewById(R.id.nav_view)
        val navController = findNavController(R.id.nav_host_fragment)

        // Setup BottomNavigationView with NavController
        navView.setupWithNavController(navController)

        navController.addOnDestinationChangedListener { _, destination, _ ->
            when (destination.id) {
                R.id.project1Fragment,  // Hide on project1Fragment
                R.id.settingsFragment  // Hide on settingsFragment
                -> navView.visibility = BottomNavigationView.GONE
                else -> navView.visibility = BottomNavigationView.VISIBLE
            }
        }

    }

    // Function to handle night mode
    private fun handleNightMode() {
        val nightModeFlags = resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
        val darkColor = ContextCompat.getColor(this, R.color.dark)
        val lightColor = ContextCompat.getColor(this, R.color.light)
        window.navigationBarColor = if (nightModeFlags == Configuration.UI_MODE_NIGHT_YES) darkColor else lightColor
        window.statusBarColor = if (nightModeFlags == Configuration.UI_MODE_NIGHT_YES) darkColor else lightColor

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val visibilityFlags = if (nightModeFlags == Configuration.UI_MODE_NIGHT_YES) {
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE
            } else {
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE or View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
            }
            window.decorView.systemUiVisibility = visibilityFlags
        }
    }

}
