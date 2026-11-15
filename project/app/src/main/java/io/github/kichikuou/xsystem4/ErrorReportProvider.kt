package io.github.kichikuou.xsystem4

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import java.io.File
import java.io.FileNotFoundException

class ErrorReportProvider : ContentProvider() {
    companion object {
        const val SHARED_DIR_NAME = "shared_reports"

        fun authorityFor(packageName: String): String = "$packageName.errorreport"
    }

    override fun onCreate(): Boolean = true

    private fun resolveReportFile(uri: Uri): File {
        val ctx = context ?: throw FileNotFoundException("Provider context unavailable")
        val baseDir = File(ctx.cacheDir, SHARED_DIR_NAME).canonicalFile
        val fileName = uri.lastPathSegment?.trim().orEmpty()
        if (fileName.isEmpty() ||
            fileName.contains('/') ||
            fileName.contains('\\') ||
            fileName.contains("..") ||
            !fileName.endsWith(".txt", ignoreCase = true)
        ) {
            throw FileNotFoundException("Invalid report URI")
        }
        val target = File(baseDir, fileName).canonicalFile
        if (target.parentFile != baseDir || !target.isFile) {
            throw FileNotFoundException("Report file not found")
        }
        return target
    }

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?
    ): Cursor {
        val file = resolveReportFile(uri)
        val requested = projection ?: arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE)
        val cols = ArrayList<String>(requested.size)
        val vals = ArrayList<Any?>(requested.size)
        for (col in requested) {
            when (col) {
                OpenableColumns.DISPLAY_NAME -> {
                    cols.add(OpenableColumns.DISPLAY_NAME)
                    vals.add(file.name)
                }
                OpenableColumns.SIZE -> {
                    cols.add(OpenableColumns.SIZE)
                    vals.add(file.length())
                }
            }
        }
        val cursor = MatrixCursor(cols.toTypedArray(), 1)
        cursor.addRow(vals.toTypedArray())
        return cursor
    }

    override fun getType(uri: Uri): String = "text/plain"

    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor {
        if (mode != "r") {
            throw SecurityException("Read-only error report provider")
        }
        val file = resolveReportFile(uri)
        return ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
    }

    override fun insert(uri: Uri, values: ContentValues?): Uri? =
        throw UnsupportedOperationException()

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int =
        throw UnsupportedOperationException()

    override fun update(
        uri: Uri,
        values: ContentValues?,
        selection: String?,
        selectionArgs: Array<out String>?
    ): Int = throw UnsupportedOperationException()
}
