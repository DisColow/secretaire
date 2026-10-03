package com.secretaire

/** Ce qu'on lit à voix haute pour une notification. */
enum class ReadingMode(val label: String) {
    /** Uniquement le nom de l'appli. */
    APP_NAME("Nom de l'appli seulement"),

    /** Nom de l'appli, titre et texte de la notification. */
    FULL("Contenu complet"),

    /** Rien n'est lu. */
    MUTED("Muet");
}

/** Réglage d'une appli : soit le mode global, soit un mode spécifique. */
sealed interface AppSetting {
    data object UseDefault : AppSetting
    data class Override(val mode: ReadingMode) : AppSetting
}
