package com.ridenotify.app.wa_reader.policy

class MessageSanitizer {
    fun sanitize(value: String): String = buildString(value.length) {
        value.forEach { character ->
            when {
                character.isWhitespace() -> append(' ')
                character == ZERO_WIDTH_JOINER -> append(character)
                Character.getType(character) == Character.FORMAT.toInt() -> append(' ')
                character.isISOControl() -> append(' ')
                else -> append(character)
            }
        }
    }.replace(WHITESPACE, " ").trim()

    private companion object {
        val WHITESPACE = Regex(" +")
        const val ZERO_WIDTH_JOINER = '\u200D'
    }
}
