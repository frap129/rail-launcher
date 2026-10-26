//[popups](../../../index.md)/[core.popups](../index.md)/[SwipeClaim](index.md)

# SwipeClaim

[main]\
enum [SwipeClaim](index.md) : [Enum](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-enum/index.html)&lt;[SwipeClaim](index.md)&gt; 

How the current touch relates to the Pop-up swipe gesture.

## Entries

| | |
|---|---|
| [Pending](-pending/index.md) | [main]<br>[Pending](-pending/index.md)<br>The drag has not passed touch slop yet. |
| [Claimed](-claimed/index.md) | [main]<br>[Claimed](-claimed/index.md)<br>The drag is a Pop-up swipe and owns the gesture. |
| [Rejected](-rejected/index.md) | [main]<br>[Rejected](-rejected/index.md)<br>The drag is not a Pop-up swipe, so the row keeps its normal gestures. |

## Properties

| Name | Summary |
|---|---|
| [name](-rejected/index.md#-372974862%2FProperties%2F191187011) | [main]<br>val [name](-rejected/index.md#-372974862%2FProperties%2F191187011): [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-string/index.html) |
| [ordinal](-rejected/index.md#-739389684%2FProperties%2F191187011) | [main]<br>val [ordinal](-rejected/index.md#-739389684%2FProperties%2F191187011): [Int](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-int/index.html) |

## Functions

| Name | Summary |
|---|---|
| [valueOf](value-of.md) | [main]<br>fun [valueOf](value-of.md)(value: [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-string/index.html)): [SwipeClaim](index.md)<br>Returns the enum constant of this type with the specified name. The string must match exactly an identifier used to declare an enum constant in this type. (Extraneous whitespace characters are not permitted.) |
| [values](values.md) | [main]<br>fun [values](values.md)(): [Array](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-array/index.html)&lt;[SwipeClaim](index.md)&gt;<br>Returns an array containing the constants of this enum type, in the order they're declared. |
