package core.data.launchables

import android.icu.lang.UScript
import android.icu.text.Collator
import android.icu.text.Transliterator
import android.icu.util.ULocale
import core.data.launchables.model.Launchable
import core.data.launchables.model.LaunchableGroup
import core.data.profiles.PrivateSpaceStatus
import core.data.profiles.ProfileInfo
import java.text.Normalizer
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

private const val DIGIT_GROUP_LABEL: String = "#"

/** Transliterates any script to Latin, so the key keeps the first letter of the result. */
private const val TO_LATIN_TRANSLITERATOR_ID: String = "Any-Latin"

/** The letters a Group key may keep: A-Z plus the letters the supported alphabets add beyond them. */
private val GROUP_LETTERS: List<Char> = ('A'..'Z') + listOf('Æ', 'Ø', 'Å', 'Ä', 'Ö', 'Ñ', 'Œ')

/** Each locale's [InitialGroupCollation], so an unchanged locale reuses its collators and keys. */
private val collationsByLocale: ConcurrentHashMap<Locale, InitialGroupCollation> = ConcurrentHashMap()

/** The scripts [this] locale writes, whose names keep their native letters instead of a transliteration. */
private fun Locale.writtenScripts(): Set<Int> = UScript.getCode(ULocale.forLocale(this))?.toSet().orEmpty()

/** [this] without its combining marks, so the locale can fold it into the letter it decomposes to. */
private fun String.baseLetter(): String {
    val decomposition: String = Normalizer.normalize(this, Normalizer.Form.NFD)
    val withoutMarks: String = decomposition.filterNot { character -> Character.getType(character) == Character.NON_SPACING_MARK.toInt() }
    return if (withoutMarks.isEmpty()) this else Normalizer.normalize(withoutMarks, Normalizer.Form.NFC)
}

/**
 * Orders the Rail: the Star, then the Initial groups in [locale]'s order, then [builtinGroup],
 * then the Private Space container. A Launchable is keyed by its Group key (see `GLOSSARY.md`).
 * Private Apps and their Shortcuts belong to the Private Space group only, so they never form or
 * join an Initial group. Folders group with the other Launchables by their resolved names. Always
 * holds the Star and the Builtin group so Home stays reachable without apps.
 *
 * [privateSpaceStatus] supplies the Private Space's Profile and whether its container is listed,
 * Hidden or Locked. [locale] re-keys the groups when the user changes it.
 *
 * An Initial group keeps the order its items arrive in, and the Unlocked Private Space group
 * lists its items A-Z. [withAppListOrder] applies the App List order.
 */
internal fun List<Launchable>.toLaunchableGroups(
    builtinGroup: LaunchableGroup.Builtin,
    privateSpaceStatus: PrivateSpaceStatus = PrivateSpaceStatus(),
    locale: Locale
): List<LaunchableGroup> {
    val collation: InitialGroupCollation = collationsByLocale.computeIfAbsent(locale) { key -> InitialGroupCollation(locale = key) }
    val privateSpace: ProfileInfo? = privateSpaceStatus.profile
    val (privateItems, otherItems) = partition { launchable -> launchable.isPrivateIn(privateSpace = privateSpace) }
    val initialGroups: List<LaunchableGroup.InitialGroup> = otherItems.groupBy { launchable -> collation.labelOf(name = launchable.name) }
        .map { (label: String, items: List<Launchable>) -> LaunchableGroup.InitialGroup(label = label, items = items) }
        .sortedWith(compareBy(collation.labelOrder) { group -> group.label })
    val privateSpaceGroup: LaunchableGroup.PrivateSpace? = when {
        !privateSpaceStatus.isContainerListed -> null
        privateSpaceStatus.isLocked -> LaunchableGroup.PrivateSpace.Locked
        else -> LaunchableGroup.PrivateSpace.Unlocked(items = privateItems.sortedWith(launchableNameOrder))
    }
    return listOf(LaunchableGroup.Star) + initialGroups + builtinGroup + listOfNotNull(privateSpaceGroup)
}

/**
 * The Group keys and their order in [locale], shared by every grouping that uses the locale.
 * Collators and transliterators are not thread-safe, so readers synchronize on this instance.
 */
private class InitialGroupCollation(private val locale: Locale) {
    private val foldCollator: Collator = Collator.getInstance(locale).apply { strength = Collator.PRIMARY }
    private val orderCollator: Collator = Collator.getInstance(locale)
    private val localeScripts: Set<Int> = locale.writtenScripts()
    private val latinTransliterator: Transliterator by lazy { Transliterator.getInstance(TO_LATIN_TRANSLITERATOR_ID) }
    private val labelsByName: MutableMap<String, String> = mutableMapOf()

    /** Orders Group keys with "#" first, then in the locale's collation. */
    val labelOrder: Comparator<String> = Comparator { first, second ->
        when {
            first == DIGIT_GROUP_LABEL || second == DIGIT_GROUP_LABEL -> first.compareTo(second)
            else -> synchronized(this) { orderCollator.compare(first, second) }
        }
    }

    fun labelOf(name: String): String = synchronized(this) {
        if (name.isEmpty()) return DIGIT_GROUP_LABEL
        if (Character.isDigit(name.codePointAt(0))) return DIGIT_GROUP_LABEL
        labelsByName.getOrPut(name) { name.toGroupLabel() }
    }

    /** The canonical Group letter [this] name keys, see Group key in `GLOSSARY.md`. */
    private fun String.toGroupLabel(): String {
        val codePoint: Int = codePointAt(0)
        val script: Int = UScript.getScript(codePoint)
        val letter: Int = if (script == UScript.LATIN || script in localeScripts) {
            codePoint
        } else {
            latinTransliterator.transliterate(this).codePointAt(0)
        }
        val uppercase: String = String(Character.toChars(letter)).uppercase(locale)
        val initial: String = String(Character.toChars(uppercase.codePointAt(0)))
        val keptLetter: Char? = GROUP_LETTERS
            .filter { candidate -> foldCollator.compare(initial, candidate.toString()) == 0 }
            .minWithOrNull { first, second -> orderCollator.compare(first.toString(), second.toString()) }
        if (keptLetter != null) return keptLetter.toString()
        val baseLetter: String = initial.baseLetter()
        return if (foldCollator.compare(initial, baseLetter) == 0) baseLetter else initial
    }
}
