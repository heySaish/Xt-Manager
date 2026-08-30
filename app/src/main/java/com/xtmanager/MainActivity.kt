package com.xtmanager

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import com.xtmanager.archive.AlpineArchiveManager
import com.xtmanager.core.filesystem.LocalFileSystem
import com.xtmanager.core.operations.OperationManager
import com.xtmanager.runtime.proot.AlpineManager
import com.xtmanager.runtime.proot.ProotExecutor
import com.xtmanager.runtime.proot.ProotManager
import com.xtmanager.runtime.tools.AlpineToolManager
import com.xtmanager.ui.FileManagerScreen
import com.xtmanager.viewmodel.FileManagerViewModel

class MainActivity : ComponentActivity() {

    private lateinit var viewModel: FileManagerViewModel

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val granted = permissions.entries.all { it.value }
        if (granted) {
            viewModel.refreshPane(com.xtmanager.core.model.PaneType.LEFT)
            viewModel.refreshPane(com.xtmanager.core.model.PaneType.RIGHT)
        } else {
            Toast.makeText(this, "Storage permission is required to list files", Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Initialize dependencies
        val fileSystem = LocalFileSystem()
        val prootManager = ProotManager(applicationContext)
        val prootExecutor = ProotExecutor(prootManager)
        val toolManager = AlpineToolManager(prootExecutor)
        val archiveManager = AlpineArchiveManager(toolManager)
        val operationManager = OperationManager(fileSystem, archiveManager)
        val alpineManager = AlpineManager(applicationContext, prootManager)

        viewModel = FileManagerViewModel(
            fileSystem = fileSystem,
            operationManager = operationManager,
            alpineManager = alpineManager
        )

        // Request storage permissions
        checkAndRequestPermissions()

        setContent {
            val context = androidx.compose.ui.platform.LocalContext.current
            val darkTheme = androidx.compose.foundation.isSystemInDarkTheme()
            val useDynamicColors = android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S
            
            val colorScheme = when {
                useDynamicColors -> {
                    if (darkTheme) androidx.compose.material3.dynamicDarkColorScheme(context)
                    else androidx.compose.material3.dynamicLightColorScheme(context)
                }
                darkTheme -> androidx.compose.material3.darkColorScheme()
                else -> androidx.compose.material3.lightColorScheme()
            }

            MaterialTheme(colorScheme = colorScheme) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    FileManagerScreen(
                        viewModel = viewModel,
                        prootManager = prootManager
                    )
                }
            }
        }
    }

    private fun checkAndRequestPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // Android 11+ (MANAGE_EXTERNAL_STORAGE)
            if (!Environment.isExternalStorageManager()) {
                try {
                    val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                        data = Uri.parse("package:$packageName")
                    }
                    startActivity(intent)
                } catch (e: Exception) {
                    val intent = Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
                    startActivity(intent)
                }
            }
        } else {
            // Android 10 and below (Normal permissions)
            val writePermission = ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
            val readPermission = ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE)

            if (writePermission != PackageManager.PERMISSION_GRANTED || readPermission != PackageManager.PERMISSION_GRANTED) {
                requestPermissionLauncher.launch(
                    arrayOf(
                        Manifest.permission.WRITE_EXTERNAL_STORAGE,
                        Manifest.permission.READ_EXTERNAL_STORAGE
                    )
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Refresh when returning to the app (e.g. after granting permission in settings)
        if (::viewModel.isInitialized) {
            viewModel.refreshPane(com.xtmanager.core.model.PaneType.LEFT)
            viewModel.refreshPane(com.xtmanager.core.model.PaneType.RIGHT)
        }
    }
}
