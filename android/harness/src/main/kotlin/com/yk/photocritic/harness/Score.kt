package com.yk.photocritic.harness

/** Character error rate of an answer against the expected text (file 40). */
object Score {
    private val FENCE = Regex("```[A-Za-z]*\\n(.*?)(```|$)", RegexOption.DOT_MATCHES_ALL)
    private val SPACE = Regex("\\s+")

    /** The first fenced block's text if there is one (the extract prompt's format), else the whole answer. */
    fun body(answer: String): String = FENCE.find(answer)?.groupValues?.get(1) ?: answer

    /** Case and runs of white space are ignored. */
    fun normalize(text: String): String = text.replace(SPACE, " ").trim().uppercase()

    /** Edit distance divided by the expected length; 0.0 is exact. */
    fun cer(
        answer: String,
        truth: String,
    ): Double {
        val a = normalize(body(answer))
        val t = normalize(truth)
        if (t.isEmpty()) return if (a.isEmpty()) 0.0 else 1.0
        return distance(a, t).toDouble() / t.length
    }

    fun distance(
        a: String,
        b: String,
    ): Int {
        var previous = IntArray(b.length + 1) { it }
        for (i in 1..a.length) {
            val current = IntArray(b.length + 1)
            current[0] = i
            for (j in 1..b.length) {
                val substitution = previous[j - 1] + if (a[i - 1] == b[j - 1]) 0 else 1
                current[j] = minOf(previous[j] + 1, current[j - 1] + 1, substitution)
            }
            previous = current
        }
        return previous[b.length]
    }
}
