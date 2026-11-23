package com.example

import com.example.domain.util.AlphaHelper
import com.example.domain.util.PhoneNormalizer
import com.example.domain.util.WhatsAppHelper
import org.junit.Assert.assertEquals
import org.junit.Test

class ExampleUnitTest {
    @Test
    fun addition_isCorrect() {
        assertEquals(4, 2 + 2)
    }

    @Test
    fun whatsapp_helper_formats_morocco_212_and_international_numbers() {
        assertEquals("212612345678", WhatsAppHelper.formatPhoneForWaMe("06 12 34 56 78", "212"))
        assertEquals("https://wa.me/212612345678", WhatsAppHelper.buildWaMeUrl("06 12 34 56 78", "212"))
        assertEquals("https://wa.me/212612345678", WhatsAppHelper.buildWaMeUrl("+212 6 12 34 56 78", "212"))
        assertEquals("https://wa.me/212612345678", WhatsAppHelper.buildWaMeUrl("00212 612-345-678", "212"))
        assertEquals("https://wa.me/33612345678", WhatsAppHelper.buildWaMeUrl("+33 6 12 34 56 78", "212"))
    }

    @Test
    fun phone_normalizer_and_alpha_helper_behave_consistently() {
        assertEquals(
            PhoneNormalizer.tailKey("06 12-34.56.78"),
            PhoneNormalizer.tailKey("+212 612 345 678")
        )
        assertEquals("E", AlphaHelper.computeFirstLetter("Élodie"))
        assertEquals("ا", AlphaHelper.computeFirstLetter("أحمد"))
    }
}
