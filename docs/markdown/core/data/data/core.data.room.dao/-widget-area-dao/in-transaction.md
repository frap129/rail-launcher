//[data](../../../index.md)/[core.data.room.dao](../index.md)/[WidgetAreaDao](index.md)/[inTransaction](in-transaction.md)

# inTransaction

[main]\
open suspend fun &lt;[T](in-transaction.md)&gt; [inTransaction](in-transaction.md)(block: suspend () -&gt; [T](in-transaction.md)): [T](in-transaction.md)

Runs [block](in-transaction.md) in one transaction so a multi-step change cannot be half-applied.
