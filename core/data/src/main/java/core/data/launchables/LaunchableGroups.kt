package core.data.launchables

import android.icu.text.Collator
import core.data.launchables.model.Launchable
import core.data.launchables.model.LaunchableGroup
import core.data.profiles.PrivateSpaceStatus
import core.data.profiles.ProfileInfo
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

private const val DIGIT_GROUP_LABEL: String = "#"

/** The letters a Group key may keep: A-Z plus the letters the supported alphabets add beyond them. */
private val GROUP_LETTERS: List<Char> = ('A'..'Z') + listOf('Æ', 'Ø', 'Å', 'Ä', 'Ö', 'Ñ', 'Œ')

/** Each locale's [InitialGroupCollation], so an unchanged locale reuses its collators and keys. */
private val collationsByLocale: ConcurrentHashMap<Locale, InitialGroupCollation> = ConcurrentHashMap()

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
 * Collators are not thread-safe, so readers synchronize on this instance.
 */
private class InitialGroupCollation(private val locale: Locale) {
    private val foldCollator: Collator = Collator.getInstance(locale).apply { strength = Collator.PRIMARY }
    private val orderCollator: Collator = Collator.getInstance(locale)
    private val labelsByInitial: MutableMap<Char, String> = mutableMapOf()

    /** Orders Group keys with "#" first, then in the locale's collation. */
    val labelOrder: Comparator<String> = Comparator { first, second ->
        when {
            first == DIGIT_GROUP_LABEL || second == DIGIT_GROUP_LABEL -> first.compareTo(second)
            else -> synchronized(this) { orderCollator.compare(first, second) }
        }
    }

    fun labelOf(name: String): String = synchronized(this) {
        val initial: Char = name.firstOrNull() ?: return DIGIT_GROUP_LABEL
        if (initial.isDigit()) return DIGIT_GROUP_LABEL
        labelsByInitial.getOrPut(initial) { initial.toGroupLabel() }
    }

    /** The canonical Group letter [this] collates as; see Group key in `GLOSSARY.md`. */
    private fun Char.toGroupLabel(): String {
        val uppercase: Char = toString().uppercase(locale).first()
        val canonical: Char? = GROUP_LETTERS
            .filter { letter -> foldCollator.compare(uppercase.toString(), letter.toString()) == 0 }
            .minWithOrNull { first, second -> orderCollator.compare(first.toString(), second.toString()) }
        return (canonical ?: uppercase).toString()
    }
}
