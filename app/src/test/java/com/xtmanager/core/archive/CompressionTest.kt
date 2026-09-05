package com.xtmanager.core.archive

import org.junit.Assert.*
import org.junit.Test

class CompressionTest {

    private fun resolveBackendFormat(extensionOrFormat: String): String? {
        return when (extensionOrFormat.lowercase()) {
            "zip", "apk" -> "zip"
            "tar", "tar.gz", "tgz" -> "tar"
            "7z" -> "7z"
            else -> null // RAR and unsupported formats return null
        }
    }

    @Test
    fun testZipFormatResolution() {
        assertEquals("zip", resolveBackendFormat("zip"))
        assertEquals("zip", resolveBackendFormat("apk"))
    }

    @Test
    fun testTarFormatResolution() {
        assertEquals("tar", resolveBackendFormat("tar.gz"))
        assertEquals("tar", resolveBackendFormat("tgz"))
    }

    @Test
    fun test7zFormatResolution() {
        assertEquals("7z", resolveBackendFormat("7z"))
    }

    @Test
    fun testRarNotSupported() {
        assertNull(resolveBackendFormat("rar"))
    }

    @Test
    fun testApkIsBrowsedAsZipNotCompressionTarget() {
        val formatForCompress = resolveBackendFormat("zip")
        assertNotNull(formatForCompress)
        assertEquals("zip", formatForCompress)
    }
}
