//[launcher](../../../index.md)/[feature.launcher](../index.md)/[LauncherViewModel](index.md)/[onBack](on-back.md)

# onBack

[main]\
fun [onBack](on-back.md)()

Back from the App List returns to Home with Edit Favorites intact. Back on Home leaves Edit Favorites, keeping the pending order, and is a no-op outside Edit Favorites. Home always consumes Back so the launcher activity is never finished.
