package com.secretaire

import android.app.Notification
import android.os.Build
import android.os.Parcelable

/** Construit la phrase à prononcer pour une notification. */
object NotificationText {

    private const val MAX_LENGTH = 400
    private val URL_REGEX = Regex("""https?://\S+|www\.\S+""")
    private val WHITESPACE_REGEX = Regex("""\s+""")

    fun build(appName: String, notification: Notification, mode: ReadingMode): String? =
        when (mode) {
            ReadingMode.MUTED -> null
            ReadingMode.APP_NAME -> appNameOnly(appName)
            ReadingMode.FULL -> full(appName, notification)
        }

    fun appNameOnly(appName: String): String = "Notification ${de(appName)}"

    private fun full(appName: String, notification: Notification): String {
        val extras = notification.extras
        val conversation = extras.getCharSequence(Notification.EXTRA_CONVERSATION_TITLE)
        val lastMessage = lastMessagingStyleMessage(notification)

        val title: CharSequence?
        val text: CharSequence?
        if (lastMessage != null) {
            // Conversation : "Groupe, Jean : message" ou "Jean : message".
            val sender = lastMessage.sender ?: extras.getCharSequence(Notification.EXTRA_TITLE)
            title = listOfNotNull(conversation, sender).filter { it.isNotBlank() }.joinToString(", ")
            text = lastMessage.text
        } else {
            title = extras.getCharSequence(Notification.EXTRA_TITLE_BIG)
                ?: extras.getCharSequence(Notification.EXTRA_TITLE)
            text = extras.getCharSequence(Notification.EXTRA_BIG_TEXT)
                ?: extras.getCharSequence(Notification.EXTRA_TEXT)
                ?: extras.getCharSequenceArray(Notification.EXTRA_TEXT_LINES)?.lastOrNull()
        }

        val cleanTitle = clean(title)
        val cleanText = clean(text)
        val body = when {
            cleanTitle.isNotEmpty() && cleanText.isNotEmpty() && cleanText != cleanTitle ->
                "$cleanTitle : $cleanText"
            cleanTitle.isNotEmpty() -> cleanTitle
            cleanText.isNotEmpty() -> cleanText
            else -> return appNameOnly(appName)
        }
        return "$appName. ${body.take(MAX_LENGTH)}"
    }

    private class Message(val sender: CharSequence?, val text: CharSequence?)

    private fun lastMessagingStyleMessage(notification: Notification): Message? {
        val messages: Array<Parcelable> =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                notification.extras.getParcelableArray(Notification.EXTRA_MESSAGES, Parcelable::class.java)
            } else {
                @Suppress("DEPRECATION")
                notification.extras.getParcelableArray(Notification.EXTRA_MESSAGES)
            } ?: return null
        val last = messages.lastOrNull() as? android.os.Bundle ?: return null
        // Clés internes de Notification.MessagingStyle.Message.
        val sender = last.getCharSequence("sender")
            ?: if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    last.getParcelable("sender_person", android.app.Person::class.java)?.name
                } else {
                    @Suppress("DEPRECATION")
                    (last.getParcelable("sender_person") as? android.app.Person)?.name
                }
            } else {
                null
            }
        return Message(sender, last.getCharSequence("text"))
    }

    private fun clean(text: CharSequence?): String =
        text?.toString()
            ?.replace(URL_REGEX, "lien")
            ?.replace(WHITESPACE_REGEX, " ")
            ?.trim()
            .orEmpty()

    /** "de WhatsApp", "d'Instagram". */
    private fun de(name: String): String {
        val first = name.firstOrNull()?.lowercaseChar() ?: return "de $name"
        return if (first in "aeiouyàâäéèêëîïôöùûü") "d'$name" else "de $name"
    }
}
