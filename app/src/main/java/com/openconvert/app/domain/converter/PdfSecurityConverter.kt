package com.openconvert.app.domain.converter

import android.content.Context
import android.net.Uri
import com.openconvert.app.domain.work.BoundedIo
import com.openconvert.app.domain.work.TempWorkspaceManager
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.encryption.AccessPermission
import com.tom_roush.pdfbox.pdmodel.encryption.StandardProtectionPolicy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException

/**
 * PDF 安全与权限管理转换器（计划书 §九）。
 * 支持设置打开密码/权限密码加密，以及已知密码的解密生成副本。
 */
class PdfSecurityConverter(
    private val context: Context,
    private val onProgress: suspend (Int) -> Unit = {},
) {
    private val resolver = context.contentResolver

    /**
     * 为 PDF 添加密码保护
     */
    suspend fun encrypt(
        inputUri: Uri,
        outputUri: Uri,
        userPassword: String,
        ownerPassword: String = userPassword,
        allowPrinting: Boolean = true,
        allowCopying: Boolean = true,
    ): Long = withContext(Dispatchers.IO) {
        if (userPassword.isBlank()) throw IllegalArgumentException("密码不能为空")

        val tempFile = TempWorkspaceManager(context).file(
            TempWorkspaceManager.NS_PDF,
            "pdf_encrypt_${System.currentTimeMillis()}.pdf",
        )
        try {
            onProgress(15)
            resolver.openInputStream(inputUri)?.use { input ->
                PDDocument.load(input).use { document ->
                    onProgress(40)
                    val accessPermission = AccessPermission().apply {
                        setCanPrint(allowPrinting)
                        setCanExtractContent(allowCopying)
                    }
                    val policy = StandardProtectionPolicy(ownerPassword, userPassword, accessPermission).apply {
                        encryptionKeyLength = 128
                    }
                    document.protect(policy)
                    onProgress(75)

                    tempFile.outputStream().use { out ->
                        document.save(out)
                    }
                }
            } ?: throw FileNotFoundException("无法读取源 PDF 文件")

            onProgress(90)
            resolver.openOutputStream(outputUri, "wt")?.use { out ->
                tempFile.inputStream().use { input ->
                    BoundedIo.copy(input, out)
                }
                out.flush()
            } ?: throw FileNotFoundException("无法写入目标 PDF 文件")

            onProgress(100)
            tempFile.length()
        } finally {
            tempFile.delete()
        }
    }

    /**
     * 验证并移除已知密码，导出无加密副本
     */
    suspend fun decrypt(
        inputUri: Uri,
        outputUri: Uri,
        password: String,
    ): Long = withContext(Dispatchers.IO) {
        val tempFile = TempWorkspaceManager(context).file(
            TempWorkspaceManager.NS_PDF,
            "pdf_decrypt_${System.currentTimeMillis()}.pdf",
        )
        try {
            onProgress(15)
            resolver.openInputStream(inputUri)?.use { input ->
                PDDocument.load(input, password).use { document ->
                    if (document.isEncrypted) {
                        document.isAllSecurityToBeRemoved = true
                    }
                    onProgress(70)

                    tempFile.outputStream().use { out ->
                        document.save(out)
                    }
                }
            } ?: throw FileNotFoundException("无法读取源 PDF 文件")

            onProgress(90)
            resolver.openOutputStream(outputUri, "wt")?.use { out ->
                tempFile.inputStream().use { input ->
                    BoundedIo.copy(input, out)
                }
                out.flush()
            } ?: throw FileNotFoundException("无法写入目标 PDF 文件")

            onProgress(100)
            tempFile.length()
        } finally {
            tempFile.delete()
        }
    }
}
