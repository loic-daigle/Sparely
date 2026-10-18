package com.example.sparely.ui.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The amount fields' calculator: evaluateAmountExpression and the helpers around it. */
class AmountExpressionTest {

    private fun assertEvaluates(expected: Double, input: String) {
        val actual = input.toSafeDouble()
        assertEquals("\"$input\"", expected, actual ?: Double.NaN, 1e-9)
    }

    @Test
    fun addsUpAmountsWithEitherDecimalSeparator() {
        assertEvaluates(21.49, "12.50+8.99")
        assertEvaluates(21.49, "12,50+8,99")
        assertEvaluates(10.0, "20-5-5")
    }

    @Test
    fun multipliesAndDividesBeforeAddingAndSubtracting() {
        assertEvaluates(14.0, "2+3*4")
        assertEvaluates(75.0, "100÷4×3")
        assertEvaluates(3.0, "5−2")
        assertEvaluates(-10.0, "5*-2")
    }

    @Test
    fun roundsResultsToCents() {
        assertEvaluates(3.33, "10/3")
        assertEvaluates(6.67, "20/3")
    }

    @Test
    fun ignoresADanglingOperatorWhileTyping() {
        assertEvaluates(12.0, "12+")
        assertEvaluates(1.0, "1++")
    }

    @Test
    fun rejectsMathThatDoesNotWorkOut() {
        assertNull("5/0".toSafeDouble())
        assertNull("+".toSafeDouble())
        assertNull("1+*2".toSafeDouble())
        assertNull("abc".toSafeDouble())
    }

    @Test
    fun plainNumbersAreNotExpressions() {
        assertFalse("12.5".isAmountExpression())
        assertFalse("-5".isAmountExpression())
        assertTrue("12+3".isAmountExpression())
        assertTrue("5-".isAmountExpression())
        assertEvaluates(12.5, "12.5")
    }

    @Test
    fun inputFilterKeepsOperatorsButDropsLeadingOnes() {
        assertEquals("12+3", "1 2+a3".filterAmountExpressionInput())
        assertEquals("5+3", "-5+3".filterAmountExpressionInput())
        assertEquals("12", "+12".filterAmountExpressionInput())
        assertEquals("2×3÷4", "2×3÷4".filterAmountExpressionInput())
    }
}
