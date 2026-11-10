package core.util

import com.ezylang.evalex.Expression
import com.ezylang.evalex.config.ExpressionConfiguration
import com.ezylang.evalex.data.EvaluationValue
import java.math.BigDecimal
import java.math.MathContext
import java.text.DecimalFormatSymbols
import java.util.Locale

/** The operators the Calculator accepts; each must stand between two numbers. */
private const val CALCULATOR_OPERATORS: String = "+-*/%^"

/** The grouping characters the Calculator accepts. */
private const val CALCULATOR_GROUPING: String = "()"

/** The significant digits the Calculator evaluates with. */
private val CALCULATOR_MATH_CONTEXT: MathContext = MathContext.DECIMAL64

/** The evaluation configuration; the precision keeps huge results bounded. */
private val CALCULATOR_CONFIGURATION: ExpressionConfiguration = ExpressionConfiguration.builder()
    .mathContext(CALCULATOR_MATH_CONTEXT)
    .build()

/** The largest adjusted exponent Rail formats as a plain decimal. */
private const val MAX_PLAIN_DECIMAL_EXPONENT: Int = 15

/** The smallest adjusted exponent Rail formats as a plain decimal. */
private const val MIN_PLAIN_DECIMAL_EXPONENT: Int = -6

/** The shortest digit string Rail refuses to treat as arithmetic, as a phone number. */
private const val PHONE_NUMBER_MIN_DIGITS: Int = 7

/** The longest digit string Rail refuses to treat as arithmetic, as a phone number. */
private const val PHONE_NUMBER_MAX_DIGITS: Int = 15

/** The longest digit group in a phone number. */
private const val PHONE_NUMBER_MAX_GROUP_DIGITS: Int = 4

/** A number followed by an operator and then a number. */
private val OPERATOR_BETWEEN_NUMBERS: Regex = Regex("""\d(?:\.\d+)?[+\-*/%^]\.?\d""")

/** A phone number: an optional country code, then hyphen-separated digit groups. */
private val PHONE_NUMBER_SHAPE: Regex = Regex("""\+?\(?\d{1,$PHONE_NUMBER_MAX_GROUP_DIGITS}\)?(?:-\d{1,$PHONE_NUMBER_MAX_GROUP_DIGITS})+""")

/**
 * The Calculator result for [query], formatted for [locale], or null when [query] is not an
 * arithmetic expression.
 *
 * [query] is accepted only when it contains digits, [locale]'s decimal separator, the operators
 * `+ - * / % ^` and grouping parentheses, and a binary operator stands between two numbers. `%`
 * is modulo. A unary minus is accepted where it follows an operator or `(`, so `2*-3` and
 * `2--3` evaluate. That rejects bare numbers, a lone `-5`, phone-number-shaped strings such as
 * `800-800-8000` and any query with a letter, so names such as `pi` stay available to Search.
 * It also keeps the Calculator free of functions and constants, avoiding angle-versus-radian
 * ambiguity.
 *
 * Arithmetic uses [CALCULATOR_MATH_CONTEXT] and exact decimals, so `0.1+0.2` formats as `0.3`.
 * Results with an adjusted exponent from [MIN_PLAIN_DECIMAL_EXPONENT] to
 * [MAX_PLAIN_DECIMAL_EXPONENT] use plain decimals; others use scientific notation, so the
 * output length stays bounded. Division by zero produces no result.
 */
fun evaluateCalculatorExpression(query: String, locale: Locale): String? {
    val decimalSeparator: Char = DecimalFormatSymbols.getInstance(locale).decimalSeparator
    if (!query.isCalculatorQuery(decimalSeparator = decimalSeparator) || query.isPhoneNumberShaped()) {
        return null
    }
    val decimalQuery: String = query.replace(oldChar = decimalSeparator, newChar = '.')
    if (!OPERATOR_BETWEEN_NUMBERS.containsMatchIn(decimalQuery.withoutUnaryMinuses())) {
        return null
    }
    val evaluation: EvaluationValue = runCatching { Expression(decimalQuery, CALCULATOR_CONFIGURATION).evaluate() }.getOrNull()
        ?: return null
    if (!evaluation.isNumberValue) {
        return null
    }
    return evaluation.numberValue.stripTrailingZeros().formatCalculatorResult(decimalSeparator = decimalSeparator)
}

private fun String.isCalculatorQuery(decimalSeparator: Char): Boolean = isNotEmpty() &&
    all { character ->
        character in '0'..'9' || character == decimalSeparator || character in CALCULATOR_OPERATORS || character in CALCULATOR_GROUPING
    }

private fun String.isPhoneNumberShaped(): Boolean {
    val digitCount: Int = count { character -> character in '0'..'9' }
    return digitCount in PHONE_NUMBER_MIN_DIGITS..PHONE_NUMBER_MAX_DIGITS && PHONE_NUMBER_SHAPE.matches(this)
}

/** [this] without grouping or the unary minuses that follow an operator or `(`, so binary operators remain. */
private fun String.withoutUnaryMinuses(): String {
    val normalized: StringBuilder = StringBuilder(length)
    var previous: Char? = null
    for (character in this) {
        val isUnaryMinus: Boolean = character == '-' && (previous == null || previous in CALCULATOR_OPERATORS || previous == '(')
        if (!isUnaryMinus) {
            normalized.append(character)
            previous = character
        }
    }
    return normalized.toString().replace("(", "").replace(")", "")
}

private fun BigDecimal.formatCalculatorResult(decimalSeparator: Char): String {
    val adjustedExponent: Int = precision() - scale() - 1
    val formatted: String = if (adjustedExponent in MIN_PLAIN_DECIMAL_EXPONENT..MAX_PLAIN_DECIMAL_EXPONENT) {
        toPlainString()
    } else {
        toString()
    }
    return formatted.replace(oldChar = '.', newChar = decimalSeparator)
}
