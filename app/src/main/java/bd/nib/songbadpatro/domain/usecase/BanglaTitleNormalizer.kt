package bd.nib.songbadpatro.domain.usecase

import java.text.Normalizer

object BanglaTitleNormalizer {

    private val zeroWidthRegex = Regex("[\u200B-\u200F\uFEFF]")
    private val punctuationRegex = Regex("[\\p{Punct}।॥‘’“”–—…·•|:/\\\\]+")
    private val whitespaceRegex = Regex("\\s+")

    private val stopWords = setOf(
        "ও", "এবং", "যে", "থেকে", "করে", "হয়েছে", "হয়েছে", "হবে", "জন্য", "নিয়ে", "নিয়ে",
        "সঙ্গে", "সাথে", "একটি", "এই", "তার", "তাঁর", "করা", "বলে", "বলেন",
        "দ্য", "এ", "এর", "তে", "কে", "কি", "কী", "না",
        "the", "a", "an", "in", "on", "of", "to", "for", "and", "is", "are"
    )

    fun normalize(text: String): String {
        val nfc = Normalizer.normalize(text, Normalizer.Form.NFC)
        return nfc
            .replace(zeroWidthRegex, "")
            .lowercase()
            .replace("য়", "য়")
            .replace("ড়", "ড়")
            .replace("ঢ়", "ঢ়")
            .replace(punctuationRegex, " ")
            .replace(whitespaceRegex, " ")
            .trim()
    }

    fun tokenize(normalizedText: String): Set<String> {
        if (normalizedText.isBlank()) return emptySet()
        return normalizedText
            .split(" ")
            .map { it.trim() }
            .filter { it.length >= 2 && it !in stopWords }
            .toSet()
    }

    fun similarity(normalizedA: String, normalizedB: String): Double {
        if (normalizedA.isBlank() || normalizedB.isBlank()) return 0.0
        if (normalizedA == normalizedB) return 1.0

        val tokensA = tokenize(normalizedA)
        val tokensB = tokenize(normalizedB)
        if (tokensA.isEmpty() || tokensB.isEmpty()) return 0.0

        val intersection = tokensA.intersect(tokensB).size
        val union = tokensA.union(tokensB).size
        if (union == 0) return 0.0
        return intersection.toDouble() / union.toDouble()
    }
}
