package com.example

import com.example.util.FileSearchMatcher
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FileSearchMatcherTest {

    @Test
    fun testPdfExtensionSearch() {
        // Busca com ".pdf"
        assertTrue(FileSearchMatcher.matches("documento.pdf", ".pdf"))
        assertTrue(FileSearchMatcher.matches("contrato.PDF", ".pdf"))
        assertTrue(FileSearchMatcher.matches("planilha.a.pdf", ".pdf"))
        assertFalse(FileSearchMatcher.matches("foto.jpg", ".pdf"))
    }

    @Test
    fun testPdfWordSearch() {
        // Busca com "pdf"
        assertTrue(FileSearchMatcher.matches("documento.pdf", "pdf"))
        assertTrue(FileSearchMatcher.matches("relatorio_pdf_final.docx", "pdf"))
        assertTrue(FileSearchMatcher.matches("manual.PDF", "pdf"))
        assertTrue(FileSearchMatcher.matches("qualquer.a.pdf", "pdf"))
        assertFalse(FileSearchMatcher.matches("foto.jpg", "pdf"))
    }

    @Test
    fun testEllipsisPrefixSearch() {
        // Busca com "...a.pdf"
        assertTrue(FileSearchMatcher.matches("relatorio_a.pdf", "...a.pdf"))
        assertTrue(FileSearchMatcher.matches("meu_documento_a.pdf", "...a.pdf"))
        assertTrue(FileSearchMatcher.matches("a.pdf", "...a.pdf"))
        assertTrue(FileSearchMatcher.matches("teste.a.pdf", "...a.pdf"))
        assertFalse(FileSearchMatcher.matches("relatorio_b.pdf", "...a.pdf"))
        assertFalse(FileSearchMatcher.matches("foto.jpg", "...a.pdf"))
    }

    @Test
    fun testAccentInsensitiveSearch() {
        // Busca sem acento encontra arquivo com acento
        assertTrue(FileSearchMatcher.matches("relatório_anual.pdf", "relatorio"))
        assertTrue(FileSearchMatcher.matches("currículo_vitae.docx", "curriculo"))
    }

    @Test
    fun testWildcardSearch() {
        // Busca com asterisco
        assertTrue(FileSearchMatcher.matches("fatura_janeiro_2025.pdf", "*janeiro*.pdf"))
        assertTrue(FileSearchMatcher.matches("backup_01.zip", "backup*.zip"))
    }
}
