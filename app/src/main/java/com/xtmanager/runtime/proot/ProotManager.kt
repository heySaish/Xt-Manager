package com.xtmanager.runtime.proot

import android.content.Context
import java.io.File

class ProotManager(private val context: Context) {
    val filesDir: File get() = context.filesDir
    
    val prootBinary: File
        get() = File(context.applicationInfo.nativeLibraryDir, "libproot.so")
        
    val alpineRootfs: File
        get() = File(filesDir, "alpine")

    val isProotInstalled: Boolean
        get() = prootBinary.exists()

    fun getBindPaths(): List<String> {
        return listOf(
            "/dev",
            "/proc",
            "/sys",
            "/storage",
            "/sdcard"
        ).filter { File(it).exists() }
    }

    fun getProotCommand(command: String): List<String> {
        val args = mutableListOf<String>()
        args.add(prootBinary.absolutePath)
        args.add("-r")
        args.add(alpineRootfs.absolutePath)
        
        // Bind directories
        getBindPaths().forEach { path ->
            args.add("-b")
            args.add("$path:$path")
        }
        
        // Set working directory inside proot if needed, default shell
        args.add("/bin/sh")
        args.add("-c")
        args.add(command)
        
        return args
    }
}
