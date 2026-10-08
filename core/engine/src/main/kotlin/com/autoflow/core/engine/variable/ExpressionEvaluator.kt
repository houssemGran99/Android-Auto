package com.autoflow.core.engine.variable

import kotlin.math.abs

class ExpressionException(message: String) : IllegalArgumentException(message)

/**
 * Evaluates arithmetic expressions with `+ - * / %`, parentheses and unary minus,
 * e.g. `(10 + 5) * 2`. Used by "Set variable" with math enabled (`$counter + 1`).
 */
object ExpressionEvaluator {
    fun evaluate(expression: String): Double = Parser(expression).parse()

    /** Formats whole numbers without a decimal part ("3" instead of "3.0"). */
    fun format(value: Double): String =
        if (value == Math.floor(value) && !value.isInfinite() && abs(value) < 1e15) {
            value.toLong().toString()
        } else {
            value.toString()
        }

    private class Parser(private val text: String) {
        private var pos = 0

        fun parse(): Double {
            val value = parseExpression()
            skipSpaces()
            if (pos != text.length) throw ExpressionException("Unexpected '${text[pos]}' at position $pos")
            return value
        }

        private fun parseExpression(): Double {
            var value = parseTerm()
            while (true) {
                skipSpaces()
                value = when (peek()) {
                    '+' -> { pos++; value + parseTerm() }
                    '-' -> { pos++; value - parseTerm() }
                    else -> return value
                }
            }
        }

        private fun parseTerm(): Double {
            var value = parseFactor()
            while (true) {
                skipSpaces()
                value = when (peek()) {
                    '*' -> { pos++; value * parseFactor() }
                    '/' -> {
                        pos++
                        val divisor = parseFactor()
                        if (divisor == 0.0) throw ExpressionException("Division by zero")
                        value / divisor
                    }
                    '%' -> {
                        pos++
                        val divisor = parseFactor()
                        if (divisor == 0.0) throw ExpressionException("Division by zero")
                        value % divisor
                    }
                    else -> return value
                }
            }
        }

        private fun parseFactor(): Double {
            skipSpaces()
            return when (val c = peek()) {
                '-' -> { pos++; -parseFactor() }
                '+' -> { pos++; parseFactor() }
                '(' -> {
                    pos++
                    val value = parseExpression()
                    skipSpaces()
                    if (peek() != ')') throw ExpressionException("Missing ')'")
                    pos++
                    value
                }
                null -> throw ExpressionException("Unexpected end of expression")
                else -> if (c.isDigit() || c == '.') parseNumber() else throw ExpressionException("Unexpected '$c' at position $pos")
            }
        }

        private fun parseNumber(): Double {
            val start = pos
            while (pos < text.length && (text[pos].isDigit() || text[pos] == '.')) pos++
            return text.substring(start, pos).toDoubleOrNull()
                ?: throw ExpressionException("Invalid number '${text.substring(start, pos)}'")
        }

        private fun peek(): Char? = text.getOrNull(pos)

        private fun skipSpaces() {
            while (pos < text.length && text[pos].isWhitespace()) pos++
        }
    }
}
