package com.expensetracker.app.util

/**
 * Recursive-descent evaluator for the embedded calculator (FR-TX-08).
 * Supports + - × ÷, parentheses and postfix % (x% = x/100).
 * Returns null on syntax errors, unmatched parentheses, division by zero (inf), etc.
 */
object CalcEval {

    fun evaluate(raw: String): Double? {
        val s = raw.replace('×', '*').replace('÷', '/')
        val p = Parser(s)
        val v = p.parseExpr() ?: return null
        if (!p.eof()) return null
        return if (v.isNaN() || v.isInfinite()) null else v
    }

    private class Parser(val s: String) {
        private var i = 0
        fun eof() = i >= s.length

        fun parseExpr(): Double? {
            var v = parseTerm() ?: return null
            while (true) {
                skip()
                if (i < s.length && (s[i] == '+' || s[i] == '-')) {
                    val op = s[i]; i++
                    val r = parseTerm() ?: return null
                    v = if (op == '+') v + r else v - r
                } else break
            }
            return v
        }

        fun parseTerm(): Double? {
            var v = parseUnary() ?: return null
            while (true) {
                skip()
                if (i < s.length && (s[i] == '*' || s[i] == '/')) {
                    val op = s[i]; i++
                    val r = parseUnary() ?: return null
                    v = if (op == '*') v * r else v / r
                } else break
            }
            return v
        }

        fun parseUnary(): Double? {
            skip()
            if (i < s.length && s[i] == '-') { i++; val v = parseUnary() ?: return null; return -v }
            if (i < s.length && s[i] == '+') { i++; return parseUnary() }
            return parsePostfix()
        }

        fun parsePostfix(): Double? {
            var v = parsePrimary() ?: return null
            while (true) {
                skip()
                if (i < s.length && s[i] == '%') { i++; v = v / 100.0 } else break
            }
            return v
        }

        fun parsePrimary(): Double? {
            skip()
            if (i >= s.length) return null
            if (s[i] == '(') {
                i++
                val v = parseExpr() ?: return null
                skip()
                if (i >= s.length || s[i] != ')') return null
                i++
                return v
            }
            if (s[i].isDigit() || s[i] == '.') {
                val start = i
                while (i < s.length && s[i].isDigit()) i++
                if (i < s.length && s[i] == '.') { i++; while (i < s.length && s[i].isDigit()) i++ }
                return s.substring(start, i).toDoubleOrNull()
            }
            return null
        }

        private fun skip() { while (i < s.length && s[i] == ' ') i++ }
    }
}
