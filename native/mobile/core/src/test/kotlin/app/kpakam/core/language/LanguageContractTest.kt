package app.kpakam.core.language

import app.kpakam.core.ContractFiles
import app.kpakam.core.TestFixtures
import kotlinx.serialization.json.double
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestFactory

/** Runs every case in `language-cases.json`. */
class LanguageContractTest {
    private val file = ContractFiles.obj("language-cases.json")

    @Test
    fun `cases and lexicon share a version`() {
        val lexicon = Lexicon.parse(ContractFiles.text("language-lexicon.json"))
        assertEquals(lexicon.version, file.getValue("version").jsonPrimitive.content)
    }

    @TestFactory
    fun `scorer matches every contract case`(): List<DynamicTest> =
        file.getValue("cases").jsonArray.map { element ->
            val case = element.jsonObject
            val text = case.getValue("text").jsonPrimitive.content
            DynamicTest.dynamicTest(text) {
                assertEquals(case.getValue("expectedScore").jsonPrimitive.double, TestFixtures.scorer.score(text), 0.0)
            }
        }

    @Test
    fun `decomposed Yoruba input scores like composed input`() {
        val composed = "mo ta ìrẹsì"
        val decomposed = java.text.Normalizer.normalize(composed, java.text.Normalizer.Form.NFD)
        assertEquals(TestFixtures.scorer.score(composed), TestFixtures.scorer.score(decomposed), 0.0)
    }

    @Test
    fun `empty text scores zero`() {
        assertEquals(0.0, TestFixtures.scorer.score(""), 0.0)
    }
}
