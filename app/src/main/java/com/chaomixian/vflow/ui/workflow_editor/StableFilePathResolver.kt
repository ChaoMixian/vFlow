package com.chaomixian.vflow.ui.workflow_editor

import android.content.Context
import android.net.Uri
import android.os.Environment
import android.provider.DocumentsContract
import java.io.File

/**
 * Resolves picker results to stable local filesystem paths for workflow parameters.
 * Only local files/directories are supported.
 */
object StableFilePathResolver {
    fun resolveFilePath(context: Context, uri: Uri): String? {
        resolveDirectPath(uri)?.let { return it }

        if (DocumentsContract.isDocumentUri(context, uri)) {
            resolveDocumentPath(context, uri)?.let { return it }
        }

        return null
    }

    fun resolveDirectoryPath(context: Context, uri: Uri): String? {
        resolveDirectPath(uri)?.let { return it }

        val treePath = try {
            DocumentsContract.getTreeDocumentId(uri)
        } catch (e: Exception) {
            null
        }?.let(::resolveExternalStorageDocumentId)
        if (treePath != null) return treePath

        return resolveFilePath(context, uri)
    }

    private fun resolveDirectPath(uri: Uri): String? {
        return when (uri.scheme) {
            null -> uri.path?.takeIf { it.startsWith("/") }
            "file" -> uri.path?.takeIf { it.startsWith("/") }
            else -> null
        }
    }

    private fun resolveDocumentPath(context: Context, uri: Uri): String? {
        val authority = uri.authority.orEmpty()
        val documentId = try {
            DocumentsContract.getDocumentId(uri)
        } catch (e: Exception) {
            return null
        }

        return when {
            authority == "com.android.externalstorage.documents" ->
                resolveExternalStorageDocumentId(documentId)
            else -> null
        }
    }

    private fun resolveExternalStorageDocumentId(documentId: String): String? {
        val parts = documentId.split(":", limit = 2)
        val volume = parts.getOrNull(0) ?: return null
        val relativePath = parts.getOrNull(1).orEmpty()
        val root = when (volume.lowercase()) {
            "primary" -> Environment.getExternalStorageDirectory().absolutePath
            "home" -> File(Environment.getExternalStorageDirectory(), "Documents").absolutePath
            else -> "/storage/$volume"
        }
        return if (relativePath.isBlank()) root else File(root, relativePath).absolutePath
    }
}
