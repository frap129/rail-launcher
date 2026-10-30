package core.util

/** The exception's type name, safe to log where its message may carry private identifiers. */
fun Throwable.logTypeName(): String = javaClass.simpleName
