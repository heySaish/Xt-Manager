package com.xtmanager.archive

import java.util.regex.Pattern

interface ArchiveOutputParser {
    fun parseLine(line: String): ProgressEvent?
}

/**
 * Parser for 7z CLI output with -bsp1 flag
 * Example lines:
 * "  5% 12/100"
 * " 45% + folder/file.txt"
 * "100%"
 */
class SevenZipOutputParser : ArchiveOutputParser {
    private val percentPattern = Pattern.compile("(\\d+)%\\b")
    private val filePattern = Pattern.compile("(?:\\+|Extracting|Compressing)\\s+(.+)")

    override fun parseLine(line: String): ProgressEvent? {
        val trimmed = line.trim()
        if (trimmed.isEmpty()) return null

        var percent: Float? = null
        val percentMatcher = percentPattern.matcher(trimmed)
        if (percentMatcher.find()) {
            val p = percentMatcher.group(1)?.toIntOrNull()
            if (p != null) {
                percent = p / 100f
            }
        }

        var currentFile = ""
        val fileMatcher = filePattern.matcher(trimmed)
        if (fileMatcher.find()) {
            currentFile = fileMatcher.group(1)?.trim() ?: ""
        }

        return if (percent != null) {
            ProgressEvent.Progressing(
                percentage = percent.coerceIn(0f, 1f),
                currentItem = currentFile
            )
        } else if (currentFile.isNotEmpty()) {
            ProgressEvent.Progressing(
                percentage = 0.5f,
                currentItem = currentFile
            )
        } else null
    }
}

/**
 * Parser for Pipe Viewer (pv) stderr progress output
 * Example line:
 * "12.5MiB 0:00:04 [ 45%] [==>   ]"
 */
class PvOutputParser : ArchiveOutputParser {
    private val pvPattern = Pattern.compile("(\\d+)%\\s*\\]")

    override fun parseLine(line: String): ProgressEvent? {
        val matcher = pvPattern.matcher(line)
        if (matcher.find()) {
            val p = matcher.group(1)?.toIntOrNull()
            if (p != null) {
                return ProgressEvent.Progressing(
                    percentage = (p / 100f).coerceIn(0f, 1f),
                    currentItem = "Processing stream..."
                )
            }
        }
        return null
    }
}

/**
 * Parser for Unzip CLI output
 * Example line:
 * "  inflating: folder/subfolder/file.txt"
 * " extracting: folder/file.apk"
 */
class UnzipOutputParser(private val totalItems: Int = 0) : ArchiveOutputParser {
    private var processedCount = 0

    override fun parseLine(line: String): ProgressEvent? {
        val trimmed = line.trim()
        if (trimmed.startsWith("inflating:") || trimmed.startsWith("extracting:")) {
            processedCount++
            val itemName = trimmed.substringAfter(":").trim()
            val ratio = if (totalItems > 0) (processedCount.toFloat() / totalItems).coerceIn(0f, 1f) else 0.5f
            return ProgressEvent.Progressing(
                percentage = ratio,
                currentItem = itemName
            )
        }
        return null
    }
}
