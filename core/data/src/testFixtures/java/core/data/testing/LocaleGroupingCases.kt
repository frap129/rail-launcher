package core.data.testing

/** [names] grouped in [localeTag], expected to form the Initial groups labelled [expectedLabels] in order. */
data class LocaleGroupingCase(val localeTag: String, val names: List<String>, val expectedLabels: List<String>)

/**
 * The locales the Initial grouping covers, each with names exercising its letters: letters the
 * locale keeps apart, accents it folds into a base letter, and variants it merges into the
 * locale's own letter.
 */
val localeGroupingCases: List<LocaleGroupingCase> = listOf(
    LocaleGroupingCase(
        localeTag = "da",
        names = listOf("Zebra", "Æble", "Äble", "Øl", "Ål", "Beta", "1Password"),
        expectedLabels = listOf("#", "B", "Z", "Æ", "Ø", "Å")
    ),
    LocaleGroupingCase(
        localeTag = "nb",
        names = listOf("Zebra", "Ärlig", "Øl", "Ål", "Beta", "1Password"),
        expectedLabels = listOf("#", "B", "Z", "Æ", "Ø", "Å")
    ),
    LocaleGroupingCase(
        localeTag = "sv",
        names = listOf("Zebra", "Ål", "Äpple", "Öl", "Beta"),
        expectedLabels = listOf("B", "Z", "Å", "Ä", "Ö")
    ),
    LocaleGroupingCase(
        localeTag = "fi",
        names = listOf("Zebra", "Ål", "Äpple", "Öl", "Beta"),
        expectedLabels = listOf("B", "Z", "Å", "Ä", "Ö")
    ),
    LocaleGroupingCase(
        localeTag = "es",
        names = listOf("Zebra", "Ñandú", "Oso", "Nube"),
        expectedLabels = listOf("N", "Ñ", "O", "Z")
    ),
    LocaleGroupingCase(
        localeTag = "fr",
        names = listOf("École", "Zèbre", "Àbri", "Œuvre"),
        expectedLabels = listOf("A", "E", "Œ", "Z")
    ),
    LocaleGroupingCase(
        localeTag = "de",
        names = listOf("Über", "Äpfel", "Öl", "Zebra"),
        expectedLabels = listOf("A", "O", "U", "Z")
    )
)
