package com.estoque.contagem.data

/**
 * Calculadora simples da contagem: "7×12+5" = 89.
 * Aceita + − × ÷ (também x * / -), vírgula decimal e respeita a ordem (× e ÷ antes de + e −).
 */
object Calc {

    const val PLUS = '+'
    const val MINUS = '−'
    const val TIMES = '×'
    const val DIVIDE = '÷'
    private val OPERATORS = setOf(PLUS, MINUS, TIMES, DIVIDE)
    private const val MAX_LENGTH = 40

    fun isOperator(c: Char) = c in OPERATORS

    /** Troca os símbolos digitados (x, *, /, -) pelos da calculadora. */
    fun normalize(expr: String): String = expr
        .replace('x', TIMES).replace('X', TIMES).replace('*', TIMES)
        .replace('/', DIVIDE).replace('-', MINUS).replace('.', ',')
        .filter { it.isDigit() || it == ',' || isOperator(it) }

    /** Tem alguma conta (operador entre números)? */
    fun hasOperation(expr: String): Boolean = expr.dropLast(1).any { isOperator(it) } || (expr.length > 1 && isOperator(expr.last()))

    /**
     * Resultado da conta, arredondado a 3 casas. Operador no fim é ignorado
     * (mostra o resultado parcial enquanto digita). Null se vazio ou inválido (ex.: ÷ 0).
     */
    fun evaluate(raw: String): Double? {
        var expr = normalize(raw)
        while (expr.isNotEmpty() && isOperator(expr.last())) expr = expr.dropLast(1)
        if (expr.isEmpty() || isOperator(expr.first())) return null

        // Separa números e operadores
        val numbers = mutableListOf<Double>()
        val ops = mutableListOf<Char>()
        val current = StringBuilder()
        for (c in expr) {
            if (isOperator(c)) {
                if (current.isEmpty()) return null // dois operadores seguidos
                numbers += current.toString().toNumber() ?: return null
                current.clear()
                ops += c
            } else {
                current.append(c)
            }
        }
        numbers += current.toString().toNumber() ?: return null

        // Primeiro × e ÷, depois + e −
        val terms = mutableListOf(numbers[0])
        val termOps = mutableListOf<Char>()
        for (i in ops.indices) {
            val n = numbers[i + 1]
            when (ops[i]) {
                TIMES -> terms[terms.lastIndex] = terms.last() * n
                DIVIDE -> {
                    if (n == 0.0) return null
                    terms[terms.lastIndex] = terms.last() / n
                }
                else -> {
                    terms += n
                    termOps += ops[i]
                }
            }
        }
        var result = terms[0]
        termOps.forEachIndexed { i, op -> result = if (op == PLUS) result + terms[i + 1] else result - terms[i + 1] }
        if (result.isNaN() || result.isInfinite()) return null
        return Qty.round(result)
    }

    private fun String.toNumber(): Double? {
        if (isEmpty() || count { it == ',' } > 1) return null
        val s = if (startsWith(",")) "0$this" else this
        return s.replace(',', '.').trimEnd('.').toDoubleOrNull()
    }

    /** Digita uma tecla na expressão: número, operador, vírgula. */
    fun press(expr: String, key: Char, allowsDecimals: Boolean): String {
        if (expr.length >= MAX_LENGTH && key != ',') return expr
        val lastNumber = expr.takeLastWhile { !isOperator(it) }
        return when {
            key.isDigit() -> {
                // Até 7 dígitos antes da vírgula e 3 depois, em cada número
                val intPart = lastNumber.substringBefore(',')
                val decPart = if (lastNumber.contains(',')) lastNumber.substringAfter(',') else null
                when {
                    decPart != null && decPart.length >= 3 -> expr
                    decPart == null && intPart.length >= 7 -> expr
                    lastNumber == "0" -> expr.dropLast(1) + key // evita "07"
                    else -> expr + key
                }
            }
            isOperator(key) -> when {
                expr.isEmpty() -> expr
                isOperator(expr.last()) -> expr.dropLast(1) + key // troca o operador
                expr.last() == ',' -> expr.dropLast(1) + key
                else -> expr + key
            }
            key == ',' -> when {
                !allowsDecimals || lastNumber.contains(',') -> expr
                lastNumber.isEmpty() -> expr + "0,"
                else -> "$expr,"
            }
            else -> expr
        }
    }

    /** Soma um valor rápido (+1, +5...) à conta. */
    fun addQuick(expr: String, value: Double): String {
        val n = Qty.toInput(value)
        return when {
            expr.isEmpty() -> n
            isOperator(expr.last()) -> expr + n
            else -> "$expr$PLUS$n"
        }.take(MAX_LENGTH)
    }

    /** Expressão com espaços para mostrar: "7 × 12 + 5". */
    fun pretty(expr: String): String = buildString {
        for (c in expr) if (isOperator(c)) append(" $c ") else append(c)
    }.trim()
}
