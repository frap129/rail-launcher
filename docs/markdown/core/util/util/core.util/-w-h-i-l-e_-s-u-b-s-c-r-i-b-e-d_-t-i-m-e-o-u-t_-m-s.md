//[util](../../index.md)/[core.util](index.md)/[WHILE_SUBSCRIBED_TIMEOUT_MS](-w-h-i-l-e_-s-u-b-s-c-r-i-b-e-d_-t-i-m-e-o-u-t_-m-s.md)

# WHILE_SUBSCRIBED_TIMEOUT_MS

[main]\
const val [WHILE_SUBSCRIBED_TIMEOUT_MS](-w-h-i-l-e_-s-u-b-s-c-r-i-b-e-d_-t-i-m-e-o-u-t_-m-s.md): [Long](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-long/index.html)

How long a flow started with `SharingStarted.WhileSubscribed` keeps its upstream subscription after the last collector leaves, so transient collector loss (for example a configuration change) does not restart it.
