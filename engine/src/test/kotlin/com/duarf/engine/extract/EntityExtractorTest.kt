package com.duarf.engine.extract

import com.duarf.engine.normalize.TextNormalizer
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class EntityExtractorTest {

    private val psl = PublicSuffixList()
    private val brands = listOf(
        BrandDefinition(
            id = "sbi",
            kind = BrandKind.BANK,
            names = listOf("sbi", "state bank of india", "एसबीआई"),
            officialDomains = listOf("sbi.co.in", "onlinesbi.sbi"),
            source = "https://sbi.co.in",
            verifiedOn = "2026-10-06"
        ),
        BrandDefinition(
            id = "hdfc",
            kind = BrandKind.BANK,
            names = listOf("hdfc", "hdfc bank"),
            officialDomains = listOf("hdfcbank.com"),
            source = "https://hdfcbank.com",
            verifiedOn = "2026-10-06"
        ),
        BrandDefinition(
            id = "electricity",
            kind = BrandKind.UTILITY,
            names = listOf("electricity board", "bijli vibhag"),
            officialDomains = listOf(),
            source = "test",
            verifiedOn = "2026-10-06"
        )
    )
    private val upiHandles = setOf("okaxis", "okhdfcbank", "oksbi", "ybl", "paytm", "apl")

    private val extractor = EntityExtractor(psl, brands, upiHandles)

    @Test
    fun `extracts obfuscated and scheme-less URLs`() {
        val input = "Click here: hxxps://secure-sbi[.]xyz/update or visit onlinesbi.sbi to verify"
        val normalized = TextNormalizer.normalize(input)
        val result = extractor.extract(normalized)

        assertThat(result.urls).hasSize(2)

        val obfuscatedUrl = result.urls.first { it.host.contains("secure-sbi") }
        assertThat(obfuscatedUrl.isDeobfuscated).isTrue()
        assertThat(obfuscatedUrl.registrableDomain).isEqualTo("secure-sbi.xyz")
        assertThat(obfuscatedUrl.tld).isEqualTo("xyz")

        val bareUrl = result.urls.first { it.host == "onlinesbi.sbi" }
        assertThat(bareUrl.scheme).isNull()
        assertThat(bareUrl.registrableDomain).isEqualTo("onlinesbi.sbi")
    }

    @Test
    fun `detects userinfo trick in URLs`() {
        val input = "Login at https://sbi.co.in@evil.example.com/login"
        val normalized = TextNormalizer.normalize(input)
        val result = extractor.extract(normalized)

        assertThat(result.urls).hasSize(1)
        val url = result.urls.first()
        assertThat(url.userinfo).isEqualTo("sbi.co.in")
        assertThat(url.host).isEqualTo("evil.example.com")
        assertThat(url.registrableDomain).isEqualTo("example.com")
    }

    @Test
    fun `detects IP literal URLs`() {
        val input = "Download update from http://192.168.1.100/app.apk"
        val normalized = TextNormalizer.normalize(input)
        val result = extractor.extract(normalized)

        assertThat(result.urls).hasSize(1)
        val url = result.urls.first()
        assertThat(url.isIpLiteral).isTrue()
        assertThat(url.fileExtension).isEqualTo("apk")
    }

    @Test
    fun `extracts APK files and double extensions`() {
        val input = "Please find attached wedding_card.pdf.apk and setup.xapk"
        val normalized = TextNormalizer.normalize(input)
        val result = extractor.extract(normalized)

        assertThat(result.fileNames).hasSize(2)
        val doubleExt = result.fileNames.first { it.fileName.contains("wedding_card") }
        assertThat(doubleExt.isDoubleExtension).isTrue()
        assertThat(doubleExt.isApkFamily).isTrue()
        assertThat(doubleExt.extension).isEqualTo("apk")
    }

    @Test
    fun `extracts Indian and international phones`() {
        val input = "Call manager on +91 9876543210 or foreign support +44 7911123456"
        val normalized = TextNormalizer.normalize(input)
        val result = extractor.extract(normalized)

        assertThat(result.phones).hasSize(2)
        val indian = result.phones.first { it.isIndianMobile }
        assertThat(indian.normalizedNumber).isEqualTo("+919876543210")
        assertThat(indian.countryCode).isEqualTo("+91")

        val foreign = result.phones.first { !it.isIndianMobile }
        assertThat(foreign.countryCode).isEqualTo("+44")
    }

    @Test
    fun `extracts UPI IDs and ignores emails`() {
        val input = "Send payment to user@okaxis or contact support@example.com"
        val normalized = TextNormalizer.normalize(input)
        val result = extractor.extract(normalized)

        assertThat(result.upiIds).hasSize(1)
        assertThat(result.upiIds.first().localPart).isEqualTo("user")
        assertThat(result.upiIds.first().handle).isEqualTo("okaxis")
    }

    @Test
    fun `extracts amounts in INR`() {
        val input = "Your account is credited with Rs. 50,000 and reward of ₹5 lakhs pending"
        val normalized = TextNormalizer.normalize(input)
        val result = extractor.extract(normalized)

        assertThat(result.amounts).hasSize(2)
        val amt1 = result.amounts.first { it.amount == 50000.0 }
        assertThat(amt1).isNotNull()
        val amt2 = result.amounts.first { it.amount == 500000.0 }
        assertThat(amt2).isNotNull()
    }

    @Test
    fun `extracts OTP code when keyword is near`() {
        val input = "Your one time password (OTP) for transaction is 482910. Do not share."
        val normalized = TextNormalizer.normalize(input)
        val result = extractor.extract(normalized)

        assertThat(result.otpCodes).hasSize(1)
        assertThat(result.otpCodes.first().code).isEqualTo("482910")
    }

    @Test
    fun `extracts brands in Latin and Indic scripts`() {
        val input = "Urgent notice from SBI and एसबीआई regarding your account"
        val normalized = TextNormalizer.normalize(input)
        val result = extractor.extract(normalized)

        assertThat(result.brands).hasSize(2)
        assertThat(result.brands.all { it.brandId == "sbi" }).isTrue()
    }
}
