package com.chaomixian.vflow.core.workflow.module.data

import android.content.Context
import com.chaomixian.vflow.R
import com.chaomixian.vflow.core.execution.ExecutionContext
import com.chaomixian.vflow.core.execution.VariableResolver
import com.chaomixian.vflow.core.module.ExecutionResult
import com.chaomixian.vflow.core.module.InputDefinition
import com.chaomixian.vflow.core.module.InputStyle
import com.chaomixian.vflow.core.module.InputVisibility
import com.chaomixian.vflow.core.module.OutputDefinition
import com.chaomixian.vflow.core.module.ParameterType
import com.chaomixian.vflow.core.types.VTypeRegistry
import com.chaomixian.vflow.core.types.basic.VString
import com.chaomixian.vflow.core.workflow.model.ActionStep
import org.bouncycastle.jce.provider.BouncyCastleProvider
import java.nio.charset.Charset
import java.security.MessageDigest
import java.security.Security
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

internal object CryptoModuleSupport {
    // Android 自带同名 "BC" Provider，但不包含 SM4/SM3；直接持有完整 BouncyCastle 实例使用。
    private val bundledBcProvider = BouncyCastleProvider()

    const val OP_ENCODE = "encode"
    const val OP_DECODE = "decode"
    const val OP_ENCRYPT = "encrypt"
    const val OP_DECRYPT = "decrypt"

    const val MODE_ECB = "ECB"
    const val MODE_CBC = "CBC"

    const val PADDING_PKCS5 = "PKCS5Padding"
    const val PADDING_PKCS7 = "PKCS7Padding"
    const val PADDING_NO = "NoPadding"

    const val ENCODING_BASE64 = "base64"
    const val ENCODING_HEX = "hex"
    const val ENCODING_UTF8 = "utf8"
    const val ENCODING_LATIN1 = "latin1"

    val operationLegacyMap = mapOf(
        "编码" to OP_ENCODE,
        "解码" to OP_DECODE,
        "加密" to OP_ENCRYPT,
        "解密" to OP_DECRYPT
    )

    val cipherModeLegacyMap = mapOf(
        "ecb" to MODE_ECB,
        "cbc" to MODE_CBC
    )

    val paddingLegacyMap = mapOf(
        "pkcs5" to PADDING_PKCS5,
        "no_padding" to PADDING_NO,
        "nopadding" to PADDING_NO
    )

    val binaryEncodingLegacyMap = mapOf(
        "Base64" to ENCODING_BASE64,
        "HEX" to ENCODING_HEX,
        "Hex" to ENCODING_HEX
    )

    val textEncodingLegacyMap = mapOf(
        "UTF-8" to ENCODING_UTF8,
        "utf-8" to ENCODING_UTF8,
        "Latin1" to ENCODING_LATIN1,
        "latin-1" to ENCODING_LATIN1,
        "ISO-8859-1" to ENCODING_LATIN1,
        "Base64" to ENCODING_BASE64,
        "HEX" to ENCODING_HEX,
        "Hex" to ENCODING_HEX
    )

    private val utf8: Charset = Charsets.UTF_8
    private val latin1: Charset = Charsets.ISO_8859_1
    private val uriUnreserved = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-._~"

    init {
        if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
            Security.addProvider(bundledBcProvider)
        }
    }

    fun buildCipherInputs(
        defaultOperation: String = OP_ENCRYPT,
        includeMode: Boolean = true,
        includePadding: Boolean = true,
        includeIv: Boolean = true,
        includeTextEncoding: Boolean = true
    ): List<InputDefinition> {
        val inputs = mutableListOf(
            InputDefinition(
                id = "operation",
                nameStringRes = R.string.param_vflow_data_crypto_operation_name,
                name = "操作",
                staticType = ParameterType.ENUM,
                defaultValue = defaultOperation,
                options = listOf(OP_ENCRYPT, OP_DECRYPT),
                optionsStringRes = listOf(
                    R.string.option_vflow_data_crypto_encrypt,
                    R.string.option_vflow_data_crypto_decrypt
                ),
                legacyValueMap = operationLegacyMap,
                inputStyle = InputStyle.CHIP_GROUP
            ),
            InputDefinition(
                id = "source_text",
                nameStringRes = R.string.param_vflow_data_crypto_source_text_name,
                name = "内容",
                staticType = ParameterType.STRING,
                defaultValue = "",
                acceptsMagicVariable = true,
                supportsRichText = true
            ),
            InputDefinition(
                id = "text_encoding",
                nameStringRes = R.string.param_vflow_data_crypto_text_encoding_name,
                name = "文本编码",
                staticType = ParameterType.ENUM,
                defaultValue = ENCODING_UTF8,
                options = listOf(ENCODING_UTF8, ENCODING_HEX, ENCODING_BASE64, ENCODING_LATIN1),
                optionsStringRes = listOf(
                    R.string.option_vflow_data_crypto_text_encoding_utf8,
                    R.string.option_vflow_data_crypto_text_encoding_hex,
                    R.string.option_vflow_data_crypto_text_encoding_base64,
                    R.string.option_vflow_data_crypto_text_encoding_latin1
                ),
                legacyValueMap = textEncodingLegacyMap,
                inputStyle = InputStyle.CHIP_GROUP,
                visibility = if (includeTextEncoding) null else InputVisibility.whenEquals("__never__", "__never__")
            ),
            InputDefinition(
                id = "key",
                nameStringRes = R.string.param_vflow_data_crypto_key_name,
                name = "密钥",
                staticType = ParameterType.STRING,
                defaultValue = "",
                acceptsMagicVariable = true,
                supportsRichText = true
            ),
            InputDefinition(
                id = "key_encoding",
                nameStringRes = R.string.param_vflow_data_crypto_key_encoding_name,
                name = "密钥编码",
                staticType = ParameterType.ENUM,
                defaultValue = ENCODING_HEX,
                options = listOf(ENCODING_UTF8, ENCODING_HEX, ENCODING_BASE64, ENCODING_LATIN1),
                optionsStringRes = listOf(
                    R.string.option_vflow_data_crypto_text_encoding_utf8,
                    R.string.option_vflow_data_crypto_text_encoding_hex,
                    R.string.option_vflow_data_crypto_text_encoding_base64,
                    R.string.option_vflow_data_crypto_text_encoding_latin1
                ),
                legacyValueMap = textEncodingLegacyMap,
                inputStyle = InputStyle.CHIP_GROUP
            )
        )

        if (includeMode) {
            inputs += InputDefinition(
                id = "cipher_mode",
                nameStringRes = R.string.param_vflow_data_crypto_cipher_mode_name,
                name = "模式",
                staticType = ParameterType.ENUM,
                defaultValue = MODE_ECB,
                options = listOf(MODE_ECB, MODE_CBC),
                optionsStringRes = listOf(
                    R.string.option_vflow_data_crypto_mode_ecb,
                    R.string.option_vflow_data_crypto_mode_cbc
                ),
                legacyValueMap = cipherModeLegacyMap,
                inputStyle = InputStyle.CHIP_GROUP
            )
        }

        if (includePadding) {
            inputs += InputDefinition(
                id = "padding",
                nameStringRes = R.string.param_vflow_data_crypto_padding_name,
                name = "填充",
                staticType = ParameterType.ENUM,
                defaultValue = PADDING_PKCS5,
                options = listOf(PADDING_PKCS5, PADDING_NO),
                optionsStringRes = listOf(
                    R.string.option_vflow_data_crypto_padding_pkcs5,
                    R.string.option_vflow_data_crypto_padding_no
                ),
                legacyValueMap = paddingLegacyMap,
                inputStyle = InputStyle.CHIP_GROUP
            )
        }

        if (includeIv) {
            inputs += InputDefinition(
                id = "iv",
                nameStringRes = R.string.param_vflow_data_crypto_iv_name,
                name = "IV",
                staticType = ParameterType.STRING,
                defaultValue = "",
                acceptsMagicVariable = true,
                supportsRichText = true,
                visibility = InputVisibility.whenEquals("cipher_mode", MODE_CBC)
            )
            inputs += InputDefinition(
                id = "iv_encoding",
                nameStringRes = R.string.param_vflow_data_crypto_iv_encoding_name,
                name = "IV 编码",
                staticType = ParameterType.ENUM,
                defaultValue = ENCODING_HEX,
                options = listOf(ENCODING_UTF8, ENCODING_HEX, ENCODING_BASE64, ENCODING_LATIN1),
                optionsStringRes = listOf(
                    R.string.option_vflow_data_crypto_text_encoding_utf8,
                    R.string.option_vflow_data_crypto_text_encoding_hex,
                    R.string.option_vflow_data_crypto_text_encoding_base64,
                    R.string.option_vflow_data_crypto_text_encoding_latin1
                ),
                legacyValueMap = textEncodingLegacyMap,
                inputStyle = InputStyle.CHIP_GROUP,
                visibility = InputVisibility.whenEquals("cipher_mode", MODE_CBC)
            )
        }

        inputs += InputDefinition(
            id = "ciphertext_encoding",
            nameStringRes = R.string.param_vflow_data_crypto_ciphertext_encoding_name,
            name = "密文编码",
            staticType = ParameterType.ENUM,
            defaultValue = ENCODING_BASE64,
            options = listOf(ENCODING_BASE64, ENCODING_HEX),
            optionsStringRes = listOf(
                R.string.option_vflow_data_crypto_binary_encoding_base64,
                R.string.option_vflow_data_crypto_binary_encoding_hex
            ),
            legacyValueMap = binaryEncodingLegacyMap,
            inputStyle = InputStyle.CHIP_GROUP
        )
        return inputs
    }

    fun resultOutput(name: String = "结果文本", nameStringRes: Int = R.string.output_vflow_data_crypto_result_text_name): List<OutputDefinition> = listOf(
        OutputDefinition(
            id = "result_text",
            name = name,
            typeName = VTypeRegistry.STRING.id,
            nameStringRes = nameStringRes
        )
    )

    fun resolveText(context: ExecutionContext, key: String, defaultValue: String = ""): String {
        return VariableResolver.resolve(context.getVariableAsString(key, defaultValue), context)
    }

    fun digest(
        context: Context,
        algorithm: String,
        sourceBytes: ByteArray,
        outputEncoding: String
    ): ExecutionResult {
        return try {
            val digest = MessageDigest.getInstance(algorithm, bundledBcProvider)
                .digest(sourceBytes)
            ExecutionResult.Success(mapOf("result_text" to VString(encodeBinary(digest, outputEncoding))))
        } catch (_: Exception) {
            try {
                val digest = MessageDigest.getInstance(algorithm).digest(sourceBytes)
                ExecutionResult.Success(mapOf("result_text" to VString(encodeBinary(digest, outputEncoding))))
            } catch (e: Exception) {
                ExecutionResult.Failure(
                    context.getString(R.string.error_vflow_data_crypto_execution_error),
                    e.localizedMessage ?: context.getString(R.string.error_vflow_data_hash_failed)
                )
            }
        }
    }

    fun urlEncode(context: Context, sourceText: String, charsetName: String): ExecutionResult {
        return try {
            val charset = Charset.forName(charsetName)
            val result = encodeUrlComponent(sourceText.toByteArray(charset))
            ExecutionResult.Success(mapOf("result_text" to VString(result)))
        } catch (e: Exception) {
            ExecutionResult.Failure(
                context.getString(R.string.error_vflow_data_crypto_execution_error),
                e.localizedMessage ?: context.getString(R.string.error_vflow_data_url_encode_failed)
            )
        }
    }

    fun urlDecode(context: Context, sourceText: String, charsetName: String): ExecutionResult {
        return try {
            val charset = Charset.forName(charsetName)
            val result = String(decodeUrlComponent(sourceText), charset)
            ExecutionResult.Success(mapOf("result_text" to VString(result)))
        } catch (e: Exception) {
            ExecutionResult.Failure(
                context.getString(R.string.error_vflow_data_crypto_execution_error),
                e.localizedMessage ?: context.getString(R.string.error_vflow_data_url_decode_failed)
            )
        }
    }

    fun executeCipher(
        context: Context,
        algorithm: String,
        provider: String? = null,
        keyText: String,
        keyEncoding: String,
        sourceText: String,
        textEncoding: String,
        operation: String,
        ciphertextEncoding: String,
        expectedKeyLengths: Set<Int>,
        blockSize: Int? = null,
        mode: String = MODE_ECB,
        padding: String = PADDING_PKCS5,
        ivText: String = "",
        ivEncoding: String = ENCODING_HEX
    ): ExecutionResult {
        val keyBytes = decodeByEncoding(keyText, keyEncoding)
        if (keyBytes.size !in expectedKeyLengths) {
            val supported = expectedKeyLengths.sorted().joinToString(" / ")
            return ExecutionResult.Failure(
                context.getString(R.string.error_vflow_data_crypto_param_error),
                context.getString(R.string.error_vflow_data_crypto_invalid_key_length, supported)
            )
        }

        val normalizedMode = mode.uppercase()
        // BouncyCastle 的 SM4 只提供 PKCS7Padding，UI 中的 PKCS5Padding 需要映射。
        val effectivePadding = if (algorithm.equals("SM4", ignoreCase = true) && padding == PADDING_PKCS5) {
            PADDING_PKCS7
        } else {
            padding
        }
        val transformation = "$algorithm/$normalizedMode/$effectivePadding"

        val ivBytes = if (normalizedMode == MODE_CBC) {
            val resolved = decodeByEncoding(ivText, ivEncoding)
            if (blockSize != null && resolved.size != blockSize) {
                return ExecutionResult.Failure(
                    context.getString(R.string.error_vflow_data_crypto_param_error),
                    context.getString(R.string.error_vflow_data_crypto_invalid_iv_length, blockSize)
                )
            }
            resolved
        } else {
            ByteArray(0)
        }

        return try {
            val cipher = when {
                provider == BouncyCastleProvider.PROVIDER_NAME -> Cipher.getInstance(transformation, bundledBcProvider)
                provider != null -> Cipher.getInstance(transformation, provider)
                else -> Cipher.getInstance(transformation)
            }
            val secretKey = SecretKeySpec(keyBytes, algorithm)
            val cipherMode = if (operation == OP_ENCRYPT) Cipher.ENCRYPT_MODE else Cipher.DECRYPT_MODE

            if (normalizedMode == MODE_CBC) {
                cipher.init(cipherMode, secretKey, IvParameterSpec(ivBytes))
            } else {
                cipher.init(cipherMode, secretKey)
            }

            val inputBytes = if (operation == OP_ENCRYPT) {
                decodeByEncoding(sourceText, textEncoding)
            } else {
                decodeBinary(sourceText, ciphertextEncoding)
            }

            if (effectivePadding == PADDING_NO && blockSize != null && inputBytes.size % blockSize != 0) {
                val label = if (operation == OP_ENCRYPT) {
                    context.getString(R.string.label_vflow_data_crypto_plain_text)
                } else {
                    context.getString(R.string.label_vflow_data_crypto_cipher_text)
                }
                return ExecutionResult.Failure(
                    context.getString(R.string.error_vflow_data_crypto_param_error),
                    context.getString(R.string.error_vflow_data_crypto_invalid_block_size, label, blockSize)
                )
            }

            val resultBytes = cipher.doFinal(inputBytes)
            val resultText = if (operation == OP_ENCRYPT) {
                encodeBinary(resultBytes, ciphertextEncoding)
            } else {
                encodeByEncoding(resultBytes, textEncoding)
            }
            ExecutionResult.Success(mapOf("result_text" to VString(resultText)))
        } catch (e: Exception) {
            ExecutionResult.Failure(
                context.getString(R.string.error_vflow_data_crypto_execution_error),
                e.localizedMessage ?: context.getString(R.string.error_vflow_data_crypto_failed)
            )
        }
    }

    fun executeRc4(
        context: Context,
        keyText: String,
        keyEncoding: String,
        sourceText: String,
        textEncoding: String,
        operation: String,
        ciphertextEncoding: String
    ): ExecutionResult {
        val keyBytes = decodeByEncoding(keyText, keyEncoding)
        if (keyBytes.isEmpty()) {
            return ExecutionResult.Failure(
                context.getString(R.string.error_vflow_data_crypto_param_error),
                context.getString(R.string.error_vflow_data_crypto_key_required)
            )
        }

        return try {
            val cipher = Cipher.getInstance("RC4")
            val secretKey = SecretKeySpec(keyBytes, "RC4")
            val cipherMode = if (operation == OP_ENCRYPT) Cipher.ENCRYPT_MODE else Cipher.DECRYPT_MODE
            cipher.init(cipherMode, secretKey)

            val inputBytes = if (operation == OP_ENCRYPT) {
                decodeByEncoding(sourceText, textEncoding)
            } else {
                decodeBinary(sourceText, ciphertextEncoding)
            }
            val resultBytes = cipher.doFinal(inputBytes)
            val resultText = if (operation == OP_ENCRYPT) {
                encodeBinary(resultBytes, ciphertextEncoding)
            } else {
                encodeByEncoding(resultBytes, textEncoding)
            }
            ExecutionResult.Success(mapOf("result_text" to VString(resultText)))
        } catch (e: Exception) {
            ExecutionResult.Failure(
                context.getString(R.string.error_vflow_data_crypto_execution_error),
                e.localizedMessage ?: context.getString(R.string.error_vflow_data_rc4_failed)
            )
        }
    }

    fun encodeBinary(bytes: ByteArray, encoding: String): String {
        return when (encoding.lowercase()) {
            ENCODING_HEX -> bytes.joinToString("") { "%02x".format(it) }
            else -> Base64.getEncoder().encodeToString(bytes)
        }
    }

    fun decodeBinary(text: String, encoding: String): ByteArray {
        return when (encoding.lowercase()) {
            ENCODING_HEX -> decodeHex(text)
            else -> Base64.getDecoder().decode(text)
        }
    }

    fun decodeByEncoding(text: String, encoding: String): ByteArray {
        return when ((textEncodingLegacyMap[encoding] ?: encoding).lowercase()) {
            ENCODING_UTF8 -> text.toByteArray(utf8)
            ENCODING_LATIN1 -> text.toByteArray(latin1)
            ENCODING_HEX -> decodeHex(text)
            ENCODING_BASE64 -> Base64.getDecoder().decode(text)
            else -> text.toByteArray(utf8)
        }
    }

    fun encodeByEncoding(bytes: ByteArray, encoding: String): String {
        return when ((textEncodingLegacyMap[encoding] ?: encoding).lowercase()) {
            ENCODING_UTF8 -> String(bytes, utf8)
            ENCODING_LATIN1 -> String(bytes, latin1)
            ENCODING_HEX -> bytes.joinToString("") { "%02x".format(it) }
            ENCODING_BASE64 -> Base64.getEncoder().encodeToString(bytes)
            else -> String(bytes, utf8)
        }
    }

    fun getOperation(step: ActionStep?, defaultValue: String): String {
        val raw = step?.parameters?.get("operation")?.toString() ?: defaultValue
        return operationLegacyMap[raw] ?: raw
    }

    private fun decodeHex(text: String): ByteArray {
        val normalized = text.trim().removePrefix("0x").replace(" ", "")
        require(normalized.length % 2 == 0) { "HEX 文本长度必须为偶数" }
        return ByteArray(normalized.length / 2) { index ->
            normalized.substring(index * 2, index * 2 + 2).toInt(16).toByte()
        }
    }

    private fun encodeUrlComponent(bytes: ByteArray): String {
        val builder = StringBuilder(bytes.size * 3)
        bytes.forEach { byte ->
            val unsigned = byte.toInt() and 0xff
            val ch = unsigned.toChar()
            if (uriUnreserved.indexOf(ch) >= 0) {
                builder.append(ch)
            } else {
                builder.append('%')
                builder.append("%02X".format(unsigned))
            }
        }
        return builder.toString()
    }

    private fun decodeUrlComponent(text: String): ByteArray {
        val out = ArrayList<Byte>(text.length)
        var index = 0
        while (index < text.length) {
            val ch = text[index]
            if (ch == '%') {
                require(index + 2 < text.length) { "Invalid percent-encoding" }
                val value = text.substring(index + 1, index + 3).toInt(16)
                out += value.toByte()
                index += 3
            } else {
                out += ch.code.toByte()
                index += 1
            }
        }
        return out.toByteArray()
    }
}
