package io.github.w568w.voicetrigger.util

object Chinese {
    private const val CHINESE_PUNCTUATION = "，。；：！“”‘’《》？【】（）——、"

    fun cleanUpChineseText(text: String): String {
        return text.replace(Regex("[$CHINESE_PUNCTUATION]"), "").trim()
    }
}