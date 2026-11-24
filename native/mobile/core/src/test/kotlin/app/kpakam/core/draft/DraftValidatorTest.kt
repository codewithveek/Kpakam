package app.kpakam.core.draft

import app.kpakam.core.TestFixtures
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class DraftValidatorTest {
    private val validator = TestFixtures.draftValidator

    private fun errors(json: String): List<String> =
        (
            validator.validate(
                json,
            ) as? DraftValidation.Invalid
        )?.errors.orEmpty()

    @Test
    fun `a valid reply decodes`() {
        val result =
            validator.validate(
                """{"drafts":[{"kind":"sale","item":"rice","quantity":3,"unit":"bag","totalNaira":45000,""" +
                    """"paidNaira":null,"customerName":null,"confidence":0.9}],"needsClarification":null}""",
            )
        val response = (result as DraftValidation.Valid).response
        assertEquals(DraftKind.SALE, response.drafts.single().kind)
        assertEquals(45000.0, response.drafts.single().totalNaira)
    }

    @Test
    fun `a clarification with no drafts is valid`() {
        assertTrue(
            validator.validate("""{"drafts":[],"needsClarification":"How much was it?"}""") is DraftValidation.Valid,
        )
    }

    @Test
    fun `a code fence around the JSON is tolerated`() {
        assertTrue(validator.validate("```json\n{\"drafts\":[]}\n```") is DraftValidation.Valid)
    }

    @Test
    fun `prose is invalid`() {
        assertEquals(listOf("The reply is not valid JSON"), errors("Sure! Here is your JSON"))
    }

    @Test
    fun `schema problems are listed with their path`() {
        val found =
            errors(
                """{"drafts":[{"kind":"gift","totalNaira":0,"confidence":2,"extra":1}],"other":true}""",
            )
        assertTrue(
            found.contains("drafts[0].kind must be one of \"sale\", \"credit_sale\", \"repayment\", \"expense\""),
            found.toString(),
        )
        assertTrue(found.contains("drafts[0].totalNaira must be greater than 0"), found.toString())
        assertTrue(found.contains("drafts[0].confidence must be at most 1"), found.toString())
        assertTrue(found.contains("drafts[0].extra is not allowed"), found.toString())
        assertTrue(found.contains("other is not allowed"), found.toString())
    }

    @Test
    fun `missing fields and too many drafts fail`() {
        assertTrue(errors("""{}""").contains("drafts is required"))
        val six = (1..6).joinToString(",") { """{"kind":"sale","totalNaira":1,"confidence":1}""" }
        assertTrue(errors("""{"drafts":[$six]}""").contains("drafts must have at most 5 items"))
        assertTrue(
            errors("""{"drafts":[{"kind":"sale","confidence":1}]}""").contains("drafts[0].totalNaira is required"),
        )
        assertTrue(
            errors(
                """{"drafts":[{"kind":"sale","totalNaira":"5","confidence":1}]}""",
            ).contains("drafts[0].totalNaira must be number"),
        )
    }

    @Test
    fun `domain rules apply after the schema`() {
        assertEquals(
            listOf("drafts[0].paidNaira must not be more than totalNaira"),
            errors(
                """{"drafts":[{"kind":"credit_sale","totalNaira":5000,"paidNaira":6000,""" +
                    """"customerName":"Ada","confidence":1}]}""",
            ),
        )
        assertEquals(
            listOf("drafts[0].customerName is required for a repayment"),
            errors("""{"drafts":[{"kind":"repayment","totalNaira":5000,"confidence":1}]}"""),
        )
        assertEquals(
            listOf("drafts[0].customerName is required for a credit_sale"),
            errors("""{"drafts":[{"kind":"credit_sale","totalNaira":5000,"paidNaira":100,"confidence":1}]}"""),
        )
        assertEquals(
            listOf("drafts[0].totalNaira rounds to 0 kobo"),
            errors("""{"drafts":[{"kind":"sale","totalNaira":0.004,"confidence":1}]}"""),
        )
    }

    @Test
    fun `a fully paid credit sale needs no customer`() {
        assertTrue(
            validator.validate(
                """{"drafts":[{"kind":"credit_sale","totalNaira":5000,"paidNaira":5000,"confidence":1}]}""",
            ) is DraftValidation.Valid,
        )
    }
}
