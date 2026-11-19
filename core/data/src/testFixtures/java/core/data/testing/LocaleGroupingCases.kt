package core.data.testing

/** [names] grouped in [localeTag], expected to form the Initial groups labelled [expectedLabels] in order. */
data class LocaleGroupingCase(val localeTag: String, val names: List<String>, val expectedLabels: List<String>)

/**
 * The locales the Initial grouping covers, each with names exercising its letters: letters the
 * locale keeps apart, accents it folds into a base letter, variants it merges into the locale's
 * own letter, names in another script transliterated to Latin, and names in a script the locale
 * writes, which keep their native letters.
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
    ),
    LocaleGroupingCase(
        localeTag = "en",
        names = listOf("Яндекс", "Ελλάδα", "東京", "Привет", "重庆", "欧元"),
        expectedLabels = listOf("A", "C", "D", "E", "O", "P")
    ),
    LocaleGroupingCase(
        localeTag = "en",
        names = listOf("🎉Party", "🎮 Games", "𠮷"),
        expectedLabels = listOf("🎉", "🎮", "𠮷")
    ),
    LocaleGroupingCase(
        localeTag = "ja",
        names = listOf("あおい", "アイス", "かさ", "カメラ", "さくら", "ゆめ", "東京", "한국", "Zebra", "1Password"),
        expectedLabels = listOf("#", "あ", "か", "さ", "や", "H", "Z")
    ),
    LocaleGroupingCase(
        localeTag = "ko",
        names = listOf("가나", "각", "까치", "나무", "한국", "韓國", "東京", "Яндекс", "Zebra", "1Password"),
        expectedLabels = listOf("#", "ㄱ", "ㄴ", "ㄷ", "ㅎ", "A", "Z")
    ),
    LocaleGroupingCase(
        localeTag = "zh",
        names = listOf("北京", "上海", "东京", "あお", "한국", "Zebra", "1Password"),
        expectedLabels = listOf("#", "A", "B", "D", "H", "S", "Z")
    ),
    LocaleGroupingCase(
        localeTag = "ru",
        names = listOf("Яндекс", "Альфа", "Браво", "Гугл", "Юла", "Экран", "Ёлка", "Европа", "Zebra"),
        expectedLabels = listOf("А", "Б", "Г", "Е", "Э", "Ю", "Я", "Z")
    )
)
