# EvalEx references Lombok's compile-time-only @Generated annotation.
-dontwarn lombok.Generated

# EvalEx's operator and function classes are read through reflection by
# their base classes; R8 drops the runtime annotations that describe them.
-keep class com.ezylang.evalex.operators.** { *; }
-keep class com.ezylang.evalex.functions.** { *; }
