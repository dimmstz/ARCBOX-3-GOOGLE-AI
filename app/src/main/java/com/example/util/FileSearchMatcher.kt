package com.example.util

import java.text.Normalizer
import java.util.regex.Pattern

/**
 * Utilitário avançado e robusto para busca de arquivos.
 * Suporta correspondência flexível com:
 * - Substrings e extensões simples (ex: "pdf", ".pdf", "jpg", ".jpg")
 * - Padrões de início/meio/fim com reticências ou asteriscos (ex: "...a.pdf", "*a.pdf", "...pdf")
 * - Remoção de acentuação (ex: "relatorio" casa com "relatório.pdf")
 * - Wildcards padrão ("*", "?", "...")
 * - Múltiplas palavras separadas por espaço (ordem livre, todas as palavras contidas)
 */
object FileSearchMatcher {

    /**
     * Remove acentuação e converte para minúsculas.
     */
    fun normalize(text: String): String {
        val nfd = Normalizer.normalize(text, Normalizer.Form.NFD)
        return "\\p{InCombiningDiacriticalMarks}+".toRegex().replace(nfd, "").lowercase()
    }

    /**
     * Verifica se o nome do arquivo bate com a consulta fornecida.
     */
    fun matches(fileName: String, query: String): Boolean {
        val trimmedQuery = query.trim()
        if (trimmedQuery.isEmpty()) return true

        val normName = normalize(fileName)
        val normQuery = normalize(trimmedQuery)

        // 1. Verificação direta de substring
        if (normName.contains(normQuery)) {
            return true
        }

        // 2. Se a busca começar com reticências ou asteriscos (ex: "...a.pdf", "*a.pdf", "...pdf")
        // O usuário utiliza "..." como indicador de "qualquer caractere antes" (wildcard)
        val strippedLeading = normQuery.trimStart('.', '*')
        if (strippedLeading.isNotEmpty()) {
            if (normName.contains(strippedLeading)) {
                return true
            }
            if (normName.endsWith(strippedLeading)) {
                return true
            }
        }

        // 3. Se a busca for uma extensão (ex: "pdf" ou ".pdf")
        val cleanExt = normQuery.trimStart('.')
        if (cleanExt.isNotEmpty()) {
            if (normName.endsWith(".$cleanExt") || normName.equals(cleanExt)) {
                return true
            }
        }

        // 4. Suporte a curingas / wildcards com "...", "*" e "?"
        val wildcardPattern = normQuery.replace("...", "*")
        if (wildcardPattern.contains("*") || wildcardPattern.contains("?")) {
            try {
                val regexPattern = buildString {
                    append("(?i).*")
                    var i = 0
                    while (i < wildcardPattern.length) {
                        val ch = wildcardPattern[i]
                        when (ch) {
                            '*' -> append(".*")
                            '?' -> append(".")
                            else -> append(Pattern.quote(ch.toString()))
                        }
                        i++
                    }
                    append(".*")
                }.toRegex()

                if (regexPattern.matches(normName)) {
                    return true
                }
            } catch (_: Exception) {
                // Se a expressão regular falhar na compilação, prossegue
            }
        }

        // 5. Suporte a múltiplas palavras separadas por espaço
        val words = normQuery.split("\\s+".toRegex()).filter { it.isNotBlank() }
        if (words.size > 1) {
            val allWordsMatch = words.all { word ->
                val cleanWord = word.trimStart('.', '*')
                if (cleanWord.isEmpty()) true
                else normName.contains(cleanWord)
            }
            if (allWordsMatch) {
                return true
            }
        }

        return false
    }
}
