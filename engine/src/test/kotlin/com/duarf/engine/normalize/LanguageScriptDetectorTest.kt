package com.duarf.engine.normalize

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class LanguageScriptDetectorTest {

    @Test
    fun `isBetaLanguage returns true for all 9 regional languages`() {
        val betaLangs = listOf("bn", "mr", "te", "ta", "or", "gu", "kn", "ml", "pa")
        for (lang in betaLangs) {
            com.google.common.truth.Truth.assertWithMessage("Expected $lang to be beta")
                .that(LanguageScriptDetector.isBetaLanguage(lang))
                .isTrue()
        }
        assertThat(LanguageScriptDetector.isBetaLanguage("en")).isFalse()
        assertThat(LanguageScriptDetector.isBetaLanguage("hi")).isFalse()
        assertThat(LanguageScriptDetector.isBetaLanguage(null)).isFalse()
        assertThat(LanguageScriptDetector.isBetaLanguage("fr")).isFalse()
    }

    @Test
    fun `getLanguageInfo returns correct metadata`() {
        val bn = LanguageScriptDetector.getLanguageInfo("bn")
        assertThat(bn).isNotNull()
        assertThat(bn!!.code).isEqualTo("bn")
        assertThat(bn.displayName).isEqualTo("বাংলা")
        assertThat(bn.isBeta).isTrue()

        val hi = LanguageScriptDetector.getLanguageInfo("hi")
        assertThat(hi).isNotNull()
        assertThat(hi!!.code).isEqualTo("hi")
        assertThat(hi.displayName).isEqualTo("हिंदी")
        assertThat(hi.isBeta).isFalse()

        val en = LanguageScriptDetector.getLanguageInfo("en")
        assertThat(en).isNotNull()
        assertThat(en!!.code).isEqualTo("en")
        assertThat(en.displayName).isEqualTo("English")
        assertThat(en.isBeta).isFalse()
    }

    @Test
    fun `detectLanguageInfo correctly identifies scripts and beta status`() {
        // Bengali
        val bnInfo = LanguageScriptDetector.detectLanguageInfo("আপনার বিদ্যুৎ বিল বাকি আছে। অবিলম্বে যোগাযোগ করুন।")
        assertThat(bnInfo).isNotNull()
        assertThat(bnInfo!!.code).isEqualTo("bn")
        assertThat(bnInfo.displayName).isEqualTo("বাংলা")
        assertThat(bnInfo.isBeta).isTrue()

        // Telugu
        val teInfo = LanguageScriptDetector.detectLanguageInfo("మీ విద్యుత్ బిల్లు బకాయి ఉంది. వెంటనే చెల్లించండి.")
        assertThat(teInfo).isNotNull()
        assertThat(teInfo!!.code).isEqualTo("te")
        assertThat(teInfo.displayName).isEqualTo("తెలుగు")
        assertThat(teInfo.isBeta).isTrue()

        // Tamil
        val taInfo = LanguageScriptDetector.detectLanguageInfo("உங்கள் மின்சாரக் கட்டணம் நிலுவையில் உள்ளது. உடனடியாகத் தொடர்பு கொள்ளவும்.")
        assertThat(taInfo).isNotNull()
        assertThat(taInfo!!.code).isEqualTo("ta")
        assertThat(taInfo.displayName).isEqualTo("தமிழ்")
        assertThat(taInfo.isBeta).isTrue()

        // Odia
        val orInfo = LanguageScriptDetector.detectLanguageInfo("ଆପଣଙ୍କ ବିଦ୍ୟୁତ୍ ବିଲ୍ ବାକି ଅଛି। ତୁରନ୍ତ ଯୋଗାଯୋଗ କରନ୍ତୁ।")
        assertThat(orInfo).isNotNull()
        assertThat(orInfo!!.code).isEqualTo("or")
        assertThat(orInfo.displayName).isEqualTo("ଓଡ଼ିଆ")
        assertThat(orInfo.isBeta).isTrue()

        // Gujarati
        val guInfo = LanguageScriptDetector.detectLanguageInfo("તમારું વીજળી બિલ બાકી છે. તાત્કાલિક સંપર્ક કરો.")
        assertThat(guInfo).isNotNull()
        assertThat(guInfo!!.code).isEqualTo("gu")
        assertThat(guInfo.displayName).isEqualTo("ગુજરાતી")
        assertThat(guInfo.isBeta).isTrue()

        // Kannada
        val knInfo = LanguageScriptDetector.detectLanguageInfo("ನಿಮ್ಮ ವಿದ್ಯುತ್ ಬಿಲ್ ಬಾಕಿ ಇದೆ. ತಕ್ಷಣ ಸಂಪರ್ಕಿಸಿ.")
        assertThat(knInfo).isNotNull()
        assertThat(knInfo!!.code).isEqualTo("kn")
        assertThat(knInfo.displayName).isEqualTo("ಕನ್ನಡ")
        assertThat(knInfo.isBeta).isTrue()

        // Malayalam
        val mlInfo = LanguageScriptDetector.detectLanguageInfo("നിങ്ങളുടെ വൈദ്യുതി ബിൽ കുടിശ്ശികയാണ്. ഉടൻ ബന്ധപ്പെടുക.")
        assertThat(mlInfo).isNotNull()
        assertThat(mlInfo!!.code).isEqualTo("ml")
        assertThat(mlInfo.displayName).isEqualTo("മലയാളം")
        assertThat(mlInfo.isBeta).isTrue()

        // Punjabi
        val paInfo = LanguageScriptDetector.detectLanguageInfo("ਤੁਹਾਡਾ ਬਿਜਲੀ ਦਾ ਬਿੱਲ ਬਾਕੀ ਹੈ। ਤੁਰੰਤ ਸੰਪਰਕ ਕਰੋ।")
        assertThat(paInfo).isNotNull()
        assertThat(paInfo!!.code).isEqualTo("pa")
        assertThat(paInfo.displayName).isEqualTo("ਪੰਜਾਬੀ")
        assertThat(paInfo.isBeta).isTrue()

        // Marathi (Devanagari with Marathi function words)
        val mrInfo = LanguageScriptDetector.detectLanguageInfo("तुमचे वीज बिल थकबाकी आहे आणि आज रात्री वीज पुरवठा खंडित केला जाईल.")
        assertThat(mrInfo).isNotNull()
        assertThat(mrInfo!!.code).isEqualTo("mr")
        assertThat(mrInfo.displayName).isEqualTo("मराठी")
        assertThat(mrInfo.isBeta).isTrue()

        // Hindi (Devanagari without Marathi function words)
        val hiInfo = LanguageScriptDetector.detectLanguageInfo("आपका बिजली बिल बकाया है और आज रात बिजली काट दी जाएगी।")
        assertThat(hiInfo).isNotNull()
        assertThat(hiInfo!!.code).isEqualTo("hi")
        assertThat(hiInfo.displayName).isEqualTo("हिंदी")
        assertThat(hiInfo.isBeta).isFalse()

        // English
        val enInfo = LanguageScriptDetector.detectLanguageInfo("Your electricity bill is overdue. Please pay immediately.")
        assertThat(enInfo).isNotNull()
        assertThat(enInfo!!.code).isEqualTo("en")
        assertThat(enInfo.displayName).isEqualTo("English")
        assertThat(enInfo.isBeta).isFalse()
    }
}
