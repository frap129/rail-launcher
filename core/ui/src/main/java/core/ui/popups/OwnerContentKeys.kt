package core.ui.popups

import core.data.popups.OwnerItems

/** The Owner key and every Launchable key the Pop-up shows. */
fun OwnerItems.contentKeys(ownerKey: String?): Set<String> = setOfNotNull(ownerKey) + items.map { item -> item.launchable.key }
