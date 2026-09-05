package com.xtmanager.core.archive

import org.junit.Assert.*
import org.junit.Test
import java.io.File

class AtomicExtractionTest {

    private fun generateUniquePath(baseFile: File): File {
        val parent = baseFile.parentFile ?: File(".")
        val name = baseFile.nameWithoutExtension
        val ext = if (baseFile.extension.isNotEmpty()) ".${baseFile.extension}" else ""

        var counter = 1
        while (true) {
            val candidate = File(parent, "$name ($counter)$ext")
            if (!candidate.exists()) {
                return candidate
            }
            counter++
        }
    }

    @Test
    fun testRenameNewPolicyGeneration() {
        val tempDir = File(System.getProperty("java.io.tmpdir"), "xt_atomic_test_${System.currentTimeMillis()}")
        tempDir.mkdirs()
        try {
            val existingFile = File(tempDir, "document.pdf")
            existingFile.createNewFile()

            val uniqueTarget = generateUniquePath(existingFile)
            assertEquals("document (1).pdf", uniqueTarget.name)
            assertFalse(uniqueTarget.exists())
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun testStagingSubfolderNaming() {
        val tempDir = File(System.getProperty("java.io.tmpdir"), "xt_staging_test")
        tempDir.mkdirs()
        try {
            val stagingFolder = File(tempDir, ".xt-tmp-staging-123")
            stagingFolder.mkdirs()
            assertTrue(stagingFolder.name.startsWith(".xt-tmp-"))
            assertTrue(stagingFolder.exists())

            // Test cleanup
            stagingFolder.deleteRecursively()
            assertFalse(stagingFolder.exists())
        } finally {
            tempDir.deleteRecursively()
        }
    }
}
