package com.audiomidi.ai.ui

/**
 * Wizard steps in their fixed order. The user navigates forward via
 * "Next", backward via "Back". Some steps can be skipped (e.g. QUALITY
 * step merges into MODEL_SELECT when no model has quality variants).
 *
 * The PROCESSING step is one-way forward only — once entered, going back
 * is replaced by the Cancel button (which jumps back to AUDIO_SELECT).
 */
enum class WizardStep {
    HOME,
    GENRE_SELECT,
    MODEL_SELECT,
    AUDIO_SELECT,
    CONFIRM,
    PROCESSING,
    COMPLETE;

    val next: WizardStep?
        get() = values().getOrNull(ordinal + 1)

    val prev: WizardStep?
        get() = values().getOrNull(ordinal - 1)

    /** 1-indexed position for the step indicator. */
    val position: Int get() = ordinal + 1

    val total: Int get() = values().size
}
