package core.data.launchables

import android.icu.lang.UScript
import android.icu.text.AlphabeticIndex
import android.icu.text.Collator
import android.icu.text.Transliterator
import android.icu.util.ULocale
import android.util.LruCache
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

/** The scripts whose locale index buckets names into a bounded set of groups instead of one per first character. */
private val INDEXED_SCRIPTS: Set<Int> = setOf(UScript.HAN, UScript.HANGUL, UScript.HIRAGANA, UScript.KATAKANA)

/** The letters a Group key may keep: A-Z plus the letters the supported alphabets add beyond them. */
private val GROUP_LETTERS: List<Char> = ('A'..'Z') + listOf('Æ', 'Ø', 'Å', 'Ä', 'Ö', 'Ñ', 'Œ')

/** The most names whose Group keys one locale keeps, so renamed and removed names age out. */
internal const val GROUP_KEY_CACHE_SIZE: Int = 1_024

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
    val collation: InitialGroupCollation = locale.initialGroupCollation()
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

private fun Locale.initialGroupCollation(): InitialGroupCollation =
    collationsByLocale.computeIfAbsent(this) { key -> InitialGroupCollation(locale = key) }

/**
 * Resolves the Group keys of [names] in [locale] ahead of the first grouping, so the collators and,
 * when a name needs one, the transliterator are built before the App List groups are.
 */
internal fun prewarmGroupKeys(names: List<String>, locale: Locale) {
    val collation: InitialGroupCollation = locale.initialGroupCollation()
    names.forEach { name -> collation.labelOf(name = name) }
}

/** How many names' Group keys [locale] keeps cached. */
internal fun cachedGroupKeyCount(locale: Locale): Int = locale.initialGroupCollation().cachedLabelCount

/**
 * The Group keys and their order in [locale], shared by every grouping that uses the locale.
 * Collators and transliterators are not thread-safe, so readers synchronize on this instance.
 */
private class InitialGroupCollation(private val locale: Locale) {
    private val foldCollator: Collator = Collator.getInstance(locale).apply { strength = Collator.PRIMARY }
    private val orderCollator: Collator = Collator.getInstance(locale)
    private val localeScripts: Set<Int> = locale.writtenScripts()
    private val indexBucketIndex: AlphabeticIndex<String> by lazy { AlphabeticIndex<String>(ULocale.forLocale(locale)) }

    /** The locale's own index letters in index order, empty unless the locale writes an indexed script. */
    private val indexLabels: List<String> by lazy {
        if (localeScripts.none { script -> script in INDEXED_SCRIPTS }) {
            emptyList()
        } else {
            val catchAllLabels: Set<String> =
                setOf(indexBucketIndex.underflowLabel, indexBucketIndex.inflowLabel, indexBucketIndex.overflowLabel)
            indexBucketIndex.bucketLabels.filterNot { label -> label in catchAllLabels }
        }
    }
    private val latinTransliterator: Transliterator by lazy { Transliterator.getInstance(TO_LATIN_TRANSLITERATOR_ID) }
    private val labelsByName: LruCache<String, String> = LruCache(GROUP_KEY_CACHE_SIZE)

    /** How many names' Group keys this locale keeps cached. */
    val cachedLabelCount: Int get() = labelsByName.size()

    /**
     * Orders Group keys with "#" first, then the locale's index letters in index order, then every
     * other key in the locale's collation.
     */
    val labelOrder: Comparator<String> = Comparator { first, second ->
        when {
            first == second -> 0

            first == DIGIT_GROUP_LABEL -> -1

            second == DIGIT_GROUP_LABEL -> 1

            else -> synchronized(this) {
                val firstRank: Int = indexLabels.indexOf(first)
                val secondRank: Int = indexLabels.indexOf(second)
                when {
                    firstRank >= 0 && secondRank >= 0 -> firstRank.compareTo(secondRank)
                    firstRank >= 0 -> -1
                    secondRank >= 0 -> 1
                    else -> orderCollator.compare(first, second)
                }
            }
        }
    }

    fun labelOf(name: String): String = synchronized(this) {
        if (name.isEmpty()) return DIGIT_GROUP_LABEL
        if (Character.isDigit(name.codePointAt(0))) return DIGIT_GROUP_LABEL
        labelsByName.get(name) ?: name.toGroupLabel().also { label -> labelsByName.put(name, label) }
    }

    /** The Group key [this] name takes, see Group key in `GLOSSARY.md`. */
    private fun String.toGroupLabel(): String {
        val script: Int = UScript.getScript(codePointAt(0))
        val isLocaleScript: Boolean = script in localeScripts
        return if (isLocaleScript && script in INDEXED_SCRIPTS) indexBucketLabel() else letterLabel(script, isLocaleScript)
    }

    /**
     * The locale's index letter for [this] name, which groups an indexed (CJK) script into a bounded set of keys;
     * a name the index only catches in its overflow bucket keys under "#".
     */
    private fun String.indexBucketLabel(): String {
        val label: String = indexBucketIndex.bucketLabels[indexBucketIndex.getBucketIndex(this)]
        return if (label in indexLabels) label else DIGIT_GROUP_LABEL
    }

    /** The canonical Group letter [this] name keys by its first letter, transliterated unless the locale writes [script]. */
    private fun String.letterLabel(script: Int, isLocaleScript: Boolean): String {
        val letter: Int = if (script == UScript.LATIN || isLocaleScript) {
            codePointAt(0)
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
