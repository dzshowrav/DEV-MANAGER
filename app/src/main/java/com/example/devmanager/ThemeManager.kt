package com.example.devmanager

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class ThemeColor(val displayName: String) {
    DEFAULT("System Dynamic"),
    BLUE("Ocean Blue"),
    RED("Crimson Red"),
    GREEN("Forest Green"),
    PURPLE("Deep Purple"),
    ORANGE("Sunset Orange")
}

object ThemeManager {
    private const val PREFS_NAME = "devmanager_settings"
    private const val KEY_THEME = "theme_color"
    
    private var prefs: SharedPreferences? = null
    private val _currentTheme = MutableStateFlow(ThemeColor.DEFAULT)
    val currentTheme: StateFlow<ThemeColor> = _currentTheme.asStateFlow()

    fun init(context: Context) {
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val savedTheme = prefs?.getString(KEY_THEME, ThemeColor.DEFAULT.name) ?: ThemeColor.DEFAULT.name
        _currentTheme.value = try { ThemeColor.valueOf(savedTheme) } catch(e: Exception) { ThemeColor.DEFAULT }
    }

    fun setTheme(theme: ThemeColor) {
        _currentTheme.value = theme
        prefs?.edit()?.putString(KEY_THEME, theme.name)?.apply()
    }
}
