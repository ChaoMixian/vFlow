package com.chaomixian.vflow.core.utils

import java.util.regex.Pattern

/**
 * 从文本中提取验证码的工具类。
 * 参考了 otphelper 项目，整合了多语言关键词和更健壮的正则表达式。
 */
object CodeExtractor {

    // 多语言关键词列表，用于识别验证码相关的文本
    private val sensitivePhrases = listOf(
        "code", "One[-\\s]Time[-\\s]Password", "کد", "رمز", "\\bOTP\\W", "\\b2FA\\W",
        "Einmalkennwort", "contraseña", "c[oó]digo", "clave", "\\bel siguiente PIN\\W",
        "验证码", "校验码", "識別碼", "認證", "驗證", "动态码", "動態碼", "код", "סיסמ",
        "\\bהקוד\\W", "\\bקוד\\W", "\\bKodu\\W", "\\bKodunuz\\W", "인증번호", "PIN"
    )

    // 应该被忽略的词组，避免误判
    private val ignoredPhrases = listOf(
        "RatingCode", "vscode", "versionCode", "unicode",
        "discount code", "fancode", "encode", "decode", "barcode", "codex"
    )

    // 通用匹配器：匹配 "关键词" 在 "验证码" 之前的大多数情况
    // (?i) 表示不区分大小写
    // (?:...) 表示非捕获分组
    // [^\\d\\w]* 匹配任意数量的非数字、非字母字符
    // ([a-zA-Z0-9]{4,8}) 捕获4到8位的字母或数字作为验证码
    private val generalCodeMatcher: Pattern =
        Pattern.compile(
            "(?i)(?:${sensitivePhrases.joinToString("|")})[^\\d\\w]*([a-zA-Z0-9]{4,8})",
            Pattern.MULTILINE
        )

    // 特殊匹配器：匹配 "验证码" 在 "关键词" 之前，例如 "123456 is your verification code"
    private val specialCodeMatcher: Pattern =
        Pattern.compile(
            "([a-zA-Z0-9]{4,8})[^\\d\\w]* (?:is your|is the|is|就是|是您的|为您的) (?:${sensitivePhrases.joinToString("|")})",
            Pattern.MULTILINE or Pattern.CASE_INSENSITIVE
        )

    private val ignoredPhrasesRegex: Pattern =
        Pattern.compile("\\b(${ignoredPhrases.joinToString("|")})\\b", Pattern.CASE_INSENSITIVE)

    /**
     * 从给定的字符串中提取验证码。
     *
     * @param text 输入的短信或通知内容。
     * @return 提取到的验证码字符串，如果未找到则返回 null。
     */
    fun getCode(text: String): String? {
        // 1. 检查是否包含应被忽略的短语，如果包含则直接返回 null
        if (ignoredPhrasesRegex.matcher(text).find()) {
            return null
        }

        // 2. 优先使用通用匹配器 (关键词在前)
        var matcher = generalCodeMatcher.matcher(text)
        if (matcher.find()) {
            val result = matcher.group(1)
            if (!result.isNullOrEmpty()) {
                return result
            }
        }

        // 3. 如果通用匹配器失败，尝试使用特殊匹配器 (验证码在前)
        matcher = specialCodeMatcher.matcher(text)
        if (matcher.find()) {
            val result = matcher.group(1)
            if (!result.isNullOrEmpty()) {
                return result
            }
        }

        // 4. 如果都未找到，返回 null
        return null
    }
}