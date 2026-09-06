package com.xtmanager.core.settings

import android.content.Context
import android.content.SharedPreferences

class SettingsManager(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    var showHiddenFiles: Boolean
        get() = prefs.getBoolean(KEY_SHOW_HIDDEN_FILES, false)
        set(value) = prefs.edit().putBoolean(KEY_SHOW_HIDDEN_FILES, value).apply()

    var folderAnimationEnabled: Boolean
        get() = prefs.getBoolean(KEY_FOLDER_ANIMATION_ENABLED, true)
        set(value) = prefs.edit().putBoolean(KEY_FOLDER_ANIMATION_ENABLED, value).apply()

    var densityScale: Float
        get() = prefs.getFloat(KEY_DENSITY_SCALE, 1.0f)
        set(value) = prefs.edit().putFloat(KEY_DENSITY_SCALE, value).apply()

    var naturalSort: Boolean
        get() = prefs.getBoolean(KEY_NATURAL_SORT, true)
        set(value) = prefs.edit().putBoolean(KEY_NATURAL_SORT, value).apply()

    var bottomBarScale: Float
        get() = prefs.getFloat(KEY_BOTTOM_BAR_SCALE, 1.0f)
        set(value) = prefs.edit().putFloat(KEY_BOTTOM_BAR_SCALE, value).apply()

    var showThumbnails: Boolean
        get() = prefs.getBoolean(KEY_SHOW_THUMBNAILS, true)
        set(value) = prefs.edit().putBoolean(KEY_SHOW_THUMBNAILS, value).apply()

    var fileNameMaxLines: Int
        get() = prefs.getInt(KEY_FILE_NAME_MAX_LINES, 2)
        set(value) = prefs.edit().putInt(KEY_FILE_NAME_MAX_LINES, value).apply()

    companion object {
        private const val PREFS_NAME = "xt_manager_settings"
        private const val KEY_SHOW_HIDDEN_FILES = "show_hidden_files"
        private const val KEY_FOLDER_ANIMATION_ENABLED = "folder_animation_enabled"
        private const val KEY_DENSITY_SCALE = "density_scale"
        private const val KEY_NATURAL_SORT = "natural_sort"
        private const val KEY_BOTTOM_BAR_SCALE = "bottom_bar_scale"
        private const val KEY_SHOW_THUMBNAILS = "show_thumbnails"
        private const val KEY_FILE_NAME_MAX_LINES = "file_name_max_lines"
    }
}
