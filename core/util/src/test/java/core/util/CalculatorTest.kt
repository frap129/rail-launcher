package core.util

import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CalculatorTest {
    private val inputEnglish: Locale = Locale.US
    private val inputGerman: Locale = Locale.GERMANY

    @Test
    fun `multiplies before it adds`() {
        assertEquals("14", evaluateCalculatorExpression(query = "2+3*4", locale = inputEnglish))
    }

    @Test
    fun `evaluates a power before it multiplies`() {
        assertEquals("18", evaluateCalculatorExpression(query = "2*3^2", locale = inputEnglish))
    }

    @Test
    fun `evaluates parentheses first`() {
        assertEquals("20", evaluateCalculatorExpression(query = "(2+3)*4", locale = inputEnglish))
        assertEquals("7", evaluateCalculatorExpression(query = "1+(2*3)", locale = inputEnglish))
        assertEquals("20", evaluateCalculatorExpression(query = "(2+3)*4", locale = inputGerman))
        assertEquals("7", evaluateCalculatorExpression(query = "1+(2*3)", locale = inputGerman))
    }

    @Test
    fun `accepts every operator`() {
        assertEquals("5", evaluateCalculatorExpression(query = "2+3", locale = inputEnglish))
        assertEquals("-1", evaluateCalculatorExpression(query = "2-3", locale = inputEnglish))
        assertEquals("6", evaluateCalculatorExpression(query = "2*3", locale = inputEnglish))
        assertEquals("2", evaluateCalculatorExpression(query = "6/3", locale = inputEnglish))
        assertEquals("1", evaluateCalculatorExpression(query = "7%3", locale = inputEnglish))
        assertEquals("8", evaluateCalculatorExpression(query = "2^3", locale = inputEnglish))
    }

    @Test
    fun `reads the decimal separator of two locales`() {
        assertEquals("3.5", evaluateCalculatorExpression(query = "1.5+2", locale = inputEnglish))
        assertEquals("3,5", evaluateCalculatorExpression(query = "1,5+2", locale = inputGerman))
    }

    @Test
    fun `rejects the decimal separator of another locale`() {
        assertNull(evaluateCalculatorExpression(query = "1,5+2", locale = inputEnglish))
        assertNull(evaluateCalculatorExpression(query = "1.5+2", locale = inputGerman))
    }

    @Test
    fun `formats results without float artifacts`() {
        assertEquals("0.3", evaluateCalculatorExpression(query = "0.1+0.2", locale = inputEnglish))
        assertEquals("0,3", evaluateCalculatorExpression(query = "0,1+0,2", locale = inputGerman))
        assertEquals("6", evaluateCalculatorExpression(query = "2*3.0", locale = inputEnglish))
        assertEquals("2.5", evaluateCalculatorExpression(query = "10/4", locale = inputEnglish))
    }

    @Test
    fun `formats a recurring decimal with the evaluation precision`() {
        assertEquals("0.3333333333333333", evaluateCalculatorExpression(query = "1/3", locale = inputEnglish))
        assertEquals("0,3333333333333333", evaluateCalculatorExpression(query = "1/3", locale = inputGerman))
    }

    @Test
    fun `formats large and small results with bounded output`() {
        assertEquals(
            "3.055500542404874E+954242508",
            evaluateCalculatorExpression(query = "9^999999999", locale = inputEnglish)
        )
        assertEquals(
            "3,055500542404874E+954242508",
            evaluateCalculatorExpression(query = "9^999999999", locale = inputGerman)
        )
        assertEquals("3.590846292887018E+954241", evaluateCalculatorExpression(query = "9^999999", locale = inputEnglish))
        assertEquals("3,590846292887018E+954241", evaluateCalculatorExpression(query = "9^999999", locale = inputGerman))
        assertEquals("1E+20", evaluateCalculatorExpression(query = "10^20", locale = inputEnglish))
        assertEquals("1E+20", evaluateCalculatorExpression(query = "10^20", locale = inputGerman))
        assertEquals("1E-9", evaluateCalculatorExpression(query = "1/10^9", locale = inputEnglish))
        assertEquals("1E-9", evaluateCalculatorExpression(query = "1/10^9", locale = inputGerman))
    }

    @Test
    fun `uses plain decimals only within the magnitude bounds`() {
        assertEquals("1000000000000000", evaluateCalculatorExpression(query = "10^15", locale = inputEnglish))
        assertEquals("1E+16", evaluateCalculatorExpression(query = "10^16", locale = inputEnglish))
        assertEquals("0.000001", evaluateCalculatorExpression(query = "10^-6", locale = inputEnglish))
        assertEquals("1E-7", evaluateCalculatorExpression(query = "10^-7", locale = inputEnglish))
    }

    @Test
    fun `rejects a bare number`() {
        assertNull(evaluateCalculatorExpression(query = "99", locale = inputEnglish))
        assertNull(evaluateCalculatorExpression(query = "-5", locale = inputEnglish))
    }

    @Test
    fun `rejects a phone number`() {
        assertNull(evaluateCalculatorExpression(query = "800-800-8000", locale = inputEnglish))
        assertNull(evaluateCalculatorExpression(query = "555-1234", locale = inputEnglish))
        assertNull(evaluateCalculatorExpression(query = "(555) 123-4567", locale = inputEnglish))
        assertNull(evaluateCalculatorExpression(query = "+1 800 555", locale = inputEnglish))
    }

    @Test
    fun `accepts a subtraction with a long first number`() {
        assertEquals("999999", evaluateCalculatorExpression(query = "1000000-1", locale = inputEnglish))
    }

    @Test
    fun `rejects an expression with a letter`() {
        assertNull(evaluateCalculatorExpression(query = "pi", locale = inputEnglish))
        assertNull(evaluateCalculatorExpression(query = "2x+1", locale = inputEnglish))
        assertNull(evaluateCalculatorExpression(query = "sin(1)", locale = inputEnglish))
    }

    @Test
    fun `rejects a query without a binary operator between two numbers`() {
        assertNull(evaluateCalculatorExpression(query = "2+", locale = inputEnglish))
        assertNull(evaluateCalculatorExpression(query = "+2", locale = inputEnglish))
        assertNull(evaluateCalculatorExpression(query = "(2)", locale = inputEnglish))
        assertNull(evaluateCalculatorExpression(query = "2(-3)", locale = inputEnglish))
        assertNull(evaluateCalculatorExpression(query = "(2)(3)", locale = inputEnglish))
        assertNull(evaluateCalculatorExpression(query = "--5", locale = inputEnglish))
    }

    @Test
    fun `accepts a unary minus after an operator or a parenthesis`() {
        assertEquals("-6", evaluateCalculatorExpression(query = "2*-3", locale = inputEnglish))
        assertEquals("0.5", evaluateCalculatorExpression(query = "2^-1", locale = inputEnglish))
        assertEquals("5", evaluateCalculatorExpression(query = "2--3", locale = inputEnglish))
        assertEquals("-6", evaluateCalculatorExpression(query = "(-2)*3", locale = inputEnglish))
        assertEquals("-2", evaluateCalculatorExpression(query = "-5+3", locale = inputEnglish))
    }

    @Test
    fun `produces no result for division by zero`() {
        assertNull(evaluateCalculatorExpression(query = "1/0", locale = inputEnglish))
        assertNull(evaluateCalculatorExpression(query = "1%0", locale = inputEnglish))
    }

    @Test
    fun `rejects an empty query`() {
        assertNull(evaluateCalculatorExpression(query = "", locale = inputEnglish))
    }
}
