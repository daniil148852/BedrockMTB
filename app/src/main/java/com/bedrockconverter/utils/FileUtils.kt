// app/src/main/java/com/bedrockconverter/utils/FileUtils.kt
package com.bedrockconverter.utils

import android.content.Context
import android.net.Uri
import android.os.Environment
import android.provider.OpenableColumns
import java.io.*
import java.security.MessageDigest
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * Utility class for file operations
 */
object FileUtils {

    private const val BUFFER_SIZE = 8192

    /**
     * Get file name from URI
     */
    fun getFileName(context: Context, uri: Uri): String {
        var name = "unknown"

        when (uri.scheme) {
            "content" -> {
                context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        if (nameIndex >= 0) {
                            name = cursor.getString(nameIndex) ?: "unknown"
                        }
                    }
                }
            }
            "file" -> {
                name = uri.lastPathSegment ?: "unknown"
            }
        }

        return name
    }

    /**
     * Get file size from URI
     */
    fun getFileSize(context: Context, uri: Uri): Long {
        var size = 0L

        when (uri.scheme) {
            "content" -> {
                context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                        if (sizeIndex >= 0) {
                            size = cursor.getLong(sizeIndex)
                        }
                    }
                }
            }
            "file" -> {
                uri.path?.let { path ->
                    size = File(path).length()
                }
            }
        }

        return size
    }

    /**
     * Get file extension
     */
    fun getFileExtension(fileName: String): String {
        return fileName.substringAfterLast('.', "").lowercase()
    }

    /**
     * Get file name without extension
     */
    fun getFileNameWithoutExtension(fileName: String): String {
        return fileName.substringBeforeLast('.')
    }

    /**
     * Copy URI content to a file
     */
    fun copyUriToFile(context: Context, uri: Uri, destFile: File): Boolean {
        return try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(destFile).use { output ->
                    input.copyTo(output, BUFFER_SIZE)
                }
            }
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    /**
     * Copy file to URI
     */
    fun copyFileToUri(context: Context, srcFile: File, uri: Uri): Boolean {
        return try {
            context.contentResolver.openOutputStream(uri)?.use { output ->
                FileInputStream(srcFile).use { input ->
                    input.copyTo(output, BUFFER_SIZE)
                }
            }
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    /**
     * Read bytes from URI
     */
    fun readBytesFromUri(context: Context, uri: Uri): ByteArray? {
        return try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                input.readBytes()
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * Read text from URI
     */
    fun readTextFromUri(context: Context, uri: Uri): String? {
        return try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                input.bufferedReader().readText()
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * Write bytes to file
     */
    fun writeBytesToFile(file: File, data: ByteArray): Boolean {
        return try {
            file.parentFile?.mkdirs()
            file.writeBytes(data)
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    /**
     * Write text to file
     */
    fun writeTextToFile(file: File, text: String): Boolean {
        return try {
            file.parentFile?.mkdirs()
            file.writeText(text)
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    /**
     * Create a temporary file
     */
    fun createTempFile(context: Context, prefix: String, suffix: String): File {
        val tempDir = File(context.cacheDir, "temp")
        tempDir.mkdirs()
        return File.createTempFile(prefix, suffix, tempDir)
    }

    /**
     * Create a temporary directory
     */
    fun createTempDirectory(context: Context, name: String): File {
        val tempDir = File(context.cacheDir, "temp/$name")
        tempDir.mkdirs()
        return tempDir
    }

    /**
     * Delete a file or directory recursively
     */
    fun deleteRecursively(file: File): Boolean {
        if (file.isDirectory) {
            file.listFiles()?.forEach { child ->
                deleteRecursively(child)
            }
        }
        return file.delete()
    }

    /**
     * Get directory size
     */
    fun getDirectorySize(directory: File): Long {
        var size = 0L
        if (directory.isDirectory) {
            directory.listFiles()?.forEach { file ->
                size += if (file.isDirectory) {
                    getDirectorySize(file)
                } else {
                    file.length()
                }
            }
        }
        return size
    }

    /**
     * Format file size for display
     */
    fun formatFileSize(bytes: Long): String {
        return when {
            bytes < 1024 -> "$bytes B"
            bytes < 1024 * 1024 -> "${bytes / 1024} KB"
            bytes < 1024 * 1024 * 1024 -> String.format("%.1f MB", bytes / (1024.0 * 1024.0))
            else -> String.format("%.2f GB", bytes / (1024.0 * 1024.0 * 1024.0))
        }
    }

    /**
     * Zip a directory
     */
    fun zipDirectory(sourceDir: File, outputZip: File): Boolean {
        return try {
            ZipOutputStream(BufferedOutputStream(FileOutputStream(outputZip))).use { zos ->
                zipDirectoryRecursive(sourceDir, sourceDir.name, zos)
            }
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    private fun zipDirectoryRecursive(file: File, baseName: String, zos: ZipOutputStream) {
        if (file.isDirectory) {
            val entryName = if (baseName.isEmpty()) "" else "$baseName/"
            if (entryName.isNotEmpty()) {
                zos.putNextEntry(ZipEntry(entryName))
                zos.closeEntry()
            }

            file.listFiles()?.forEach { child ->
                val childName = if (baseName.isEmpty()) child.name else "$baseName/${child.name}"
                zipDirectoryRecursive(child, childName, zos)
            }
        } else {
            zos.putNextEntry(ZipEntry(baseName))
            FileInputStream(file).use { fis ->
                fis.copyTo(zos, BUFFER_SIZE)
            }
            zos.closeEntry()
        }
    }

    /**
     * Unzip a file to directory
     */
    fun unzip(zipFile: File, destDir: File): Boolean {
        return try {
            destDir.mkdirs()

            ZipInputStream(BufferedInputStream(FileInputStream(zipFile))).use { zis ->
                var entry: ZipEntry? = zis.nextEntry
                while (entry != null) {
                    val destFile = File(destDir, entry.name)

                    // Security check: prevent zip slip vulnerability
                    if (!destFile.canonicalPath.startsWith(destDir.canonicalPath)) {
                        throw SecurityException("Zip entry outside of target directory")
                    }

                    if (entry.isDirectory) {
                        destFile.mkdirs()
                    } else {
                        destFile.parentFile?.mkdirs()
                        FileOutputStream(destFile).use { fos ->
                            zis.copyTo(fos, BUFFER_SIZE)
                        }
                    }

                    entry = zis.nextEntry
                }
            }
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    /**
     * Calculate MD5 hash of a file
     */
    fun calculateMd5(file: File): String? {
        return try {
            val md = MessageDigest.getInstance("MD5")
            FileInputStream(file).use { fis ->
                val buffer = ByteArray(BUFFER_SIZE)
                var read: Int
                while (fis.read(buffer).also { read = it } != -1) {
                    md.update(buffer, 0, read)
                }
            }
            md.digest().joinToString("") { "%02x".format(it) }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * Sanitize filename
     */
    fun sanitizeFileName(name: String): String {
        return name
            .replace(Regex("[^a-zA-Z0-9._-]"), "_")
            .replace(Regex("_+"), "_")
            .trim('_')
            .take(100)
            .ifEmpty { "file" }
    }

    /**
     * Check if external storage is available for write
     */
    fun isExternalStorageWritable(): Boolean {
        return Environment.getExternalStorageState() == Environment.MEDIA_MOUNTED
    }

    /**
     * Check if external storage is available for read
     */
    fun isExternalStorageReadable(): Boolean {
        return Environment.getExternalStorageState() in setOf(
            Environment.MEDIA_MOUNTED,
            Environment.MEDIA_MOUNTED_READ_ONLY
        )
    }

    /**
     * Get cache directory with fallback
     */
    fun getCacheDirectory(context: Context): File {
        return context.externalCacheDir ?: context.cacheDir
    }

    /**
     * Get files directory with fallback
     */
    fun getFilesDirectory(context: Context): File {
        return context.getExternalFilesDir(null) ?: context.filesDir
    }

    /**
     * Clear cache directory
     */
    fun clearCache(context: Context): Long {
        var clearedBytes = 0L

        val cacheDir = context.cacheDir
        clearedBytes += clearDirectory(cacheDir)

        context.externalCacheDir?.let { extCache ->
            clearedBytes += clearDirectory(extCache)
        }

        return clearedBytes
    }

    private fun clearDirectory(directory: File): Long {
        var clearedBytes = 0L

        directory.listFiles()?.forEach { file ->
            clearedBytes += if (file.isDirectory) {
                val size = getDirectorySize(file)
                deleteRecursively(file)
                size
            } else {
                val size = file.length()
                file.delete()
                size
            }
        }

        return clearedBytes
    }

    /**
     * List files in directory with filter
     */
    fun listFiles(directory: File, extension: String? = null): List<File> {
        return directory.listFiles()?.filter { file ->
            file.isFile && (extension == null || file.extension.equals(extension, ignoreCase = true))
        } ?: emptyList()
    }

    /**
     * Ensure directory exists
     */
    fun ensureDirectory(directory: File): Boolean {
        return if (directory.exists()) {
            directory.isDirectory
        } else {
            directory.mkdirs()
        }
    }
}