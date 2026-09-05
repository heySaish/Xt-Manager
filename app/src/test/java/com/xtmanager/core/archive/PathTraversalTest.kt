package com.xtmanager.core.archive

import org.junit.Assert.*
import org.junit.Test
import java.io.File

class PathTraversalTest {

    private fun sanitizePath(baseDir: File, entryPath: String): File? {
        if (entryPath.contains("\u0000")) return null
        if (entryPath.length >= 2 && entryPath[0].isLetter() && entryPath[1] == ':') return null
        
        val normalized = entryPath.replace('\\', '/')
        if (normalized.startsWith("/")) return null

        val parts = normalized.split('/').filter { it.isNotEmpty() }
        var depth = 0
        val cleanComponents = mutableListOf<String>()

        for (part in parts) {
            when (part) {
                ".." -> {
                    depth--
                    if (depth < 0) return null
                    if (cleanComponents.isNotEmpty()) cleanComponents.removeAt(cleanComponents.size - 1)
                }
                "." -> {}
                else -> {
                    depth++
                    cleanComponents.add(part)
                }
            }
        }

        if (cleanComponents.isEmpty()) return null

        var target = baseDir
        for (comp in cleanComponents) {
            target = File(target, comp)
        }
        return target
    }

    @Test
    fun testDotDotTraversalRejected() {
        val dest = File("/tmp/test_dest")
        assertNull(sanitizePath(dest, "../file.txt"))
    }

    @Test
    fun testDeepParentTraversalRejected() {
        val dest = File("/tmp/test_dest")
        assertNull(sanitizePath(dest, "../../etc/passwd"))
    }

    @Test
    fun testAbsolutePathRejected() {
        val dest = File("/tmp/test_dest")
        assertNull(sanitizePath(dest, "/etc/systemd/system"))
    }

    @Test
    fun testWindowsDrivePathRejected() {
        val dest = File("/tmp/test_dest")
        assertNull(sanitizePath(dest, "C:\\Windows\\System32"))
    }

    @Test
    fun testBackslashTraversalRejected() {
        val dest = File("/tmp/test_dest")
        assertNull(sanitizePath(dest, "..\\..\\sdcard\\data"))
    }

    @Test
    fun testNulByteRejected() {
        val dest = File("/tmp/test_dest")
        assertNull(sanitizePath(dest, "valid.txt\u0000.sh"))
    }

    @Test
    fun testSafeNestedPathAllowed() {
        val dest = File("/tmp/test_dest")
        val resolved = sanitizePath(dest, "sub/folder/data.bin")
        assertNotNull(resolved)
        assertEquals(File(dest, "sub/folder/data.bin").path, resolved?.path)
    }
}
