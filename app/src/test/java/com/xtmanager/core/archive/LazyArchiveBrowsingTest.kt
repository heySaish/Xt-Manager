package com.xtmanager.core.archive

import com.xtmanager.core.model.FileEntry
import com.xtmanager.core.model.FileType
import org.junit.Assert.*
import org.junit.Test

class LazyArchiveBrowsingTest {

    private fun filterImmediateChildren(
        entries: List<FileEntry>,
        virtualPrefix: String
    ): List<FileEntry> {
        val cleanPrefix = virtualPrefix.trim('/').let { if (it.isEmpty()) "" else "$it/" }
        val map = mutableMapOf<String, FileEntry>()

        for (entry in entries) {
            val relPath = entry.path.trimStart('/')
            if (cleanPrefix.isNotEmpty() && !relPath.startsWith(cleanPrefix)) {
                continue
            }

            val subPath = if (cleanPrefix.isEmpty()) relPath else relPath.substring(cleanPrefix.length)
            if (subPath.isEmpty()) continue

            val parts = subPath.split('/').filter { it.isNotEmpty() }
            if (parts.isEmpty()) continue

            val childName = parts[0]
            val isDir = parts.size > 1 || entry.isDirectory

            map.getOrPut(childName) {
                FileEntry(
                    name = childName,
                    path = if (cleanPrefix.isEmpty()) childName else "$cleanPrefix$childName",
                    isDirectory = isDir,
                    size = if (isDir) 0L else entry.size,
                    lastModified = entry.lastModified,
                    type = if (isDir) FileType.DIRECTORY else FileType.FILE
                )
            }
        }

        return map.values.sortedWith(compareBy<FileEntry> { !it.isDirectory }.thenBy { it.name })
    }

    @Test
    fun testImmediateChildrenFiltering() {
        val rawEntries = listOf(
            FileEntry("a.txt", "src/a.txt", false, 100, 0, FileType.FILE),
            FileEntry("b.txt", "src/main/b.txt", false, 200, 0, FileType.FILE),
            FileEntry("c.txt", "src/main/deep/c.txt", false, 300, 0, FileType.FILE)
        )

        val children = filterImmediateChildren(rawEntries, "src")
        assertEquals(2, children.size)
        assertTrue(children.any { it.name == "main" && it.isDirectory })
        assertTrue(children.any { it.name == "a.txt" && !it.isDirectory })
    }

    @Test
    fun test10kEntriesFilteringPerformance() {
        val list = mutableListOf<FileEntry>()
        for (i in 0 until 10_000) {
            list.add(
                FileEntry(
                    "file_$i.txt",
                    "root/dir_${i % 10}/file_$i.txt",
                    false,
                    1024,
                    0,
                    FileType.FILE
                )
            )
        }

        val start = System.currentTimeMillis()
        val result = filterImmediateChildren(list, "root")
        val duration = System.currentTimeMillis() - start

        assertEquals(10, result.size)
        assertTrue(duration < 100) // Under 100ms
    }
}
