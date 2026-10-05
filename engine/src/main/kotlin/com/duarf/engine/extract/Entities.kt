package com.duarf.engine.extract

import com.duarf.engine.model.TextSpan
import kotlinx.serialization.Serializable

@Serializable
enum class BrandKind {
    BANK,
    PAYMENTS,
    GOVERNMENT,
    LAW_ENFORCEMENT,
    TELECOM,
    COURIER,
    ECOMMERCE,
    UTILITY
}

@Serializable
data class BrandDefinition(
    val id: String,
    val kind: BrandKind,
    val names: List<String>,
    val officialDomains: List<String>,
    val source: String,
    val verifiedOn: String
)

sealed class ExtractedEntity {
    abstract val span: TextSpan
    abstract val rawText: String
}

data class ExtractedUrl(
    override val span: TextSpan,
    override val rawText: String,
    val scheme: String?,
    val host: String,
    val registrableDomain: String,
    val tld: String,
    val path: String,
    val port: Int?,
    val userinfo: String?,
    val isPunycode: Boolean,
    val isIpLiteral: Boolean,
    val isDeobfuscated: Boolean,
    val fileExtension: String?
) : ExtractedEntity()

data class ExtractedFileName(
    override val span: TextSpan,
    override val rawText: String,
    val fileName: String,
    val extension: String,
    val isDoubleExtension: Boolean,
    val isApkFamily: Boolean
) : ExtractedEntity()

data class ExtractedPhone(
    override val span: TextSpan,
    override val rawText: String,
    val normalizedNumber: String,
    val isIndianMobile: Boolean,
    val countryCode: String?
) : ExtractedEntity()

data class ExtractedUpiId(
    override val span: TextSpan,
    override val rawText: String,
    val localPart: String,
    val handle: String
) : ExtractedEntity()

data class ExtractedAmount(
    override val span: TextSpan,
    override val rawText: String,
    val amount: Double?,
    val currency: String
) : ExtractedEntity()

data class ExtractedOtpCode(
    override val span: TextSpan,
    override val rawText: String,
    val code: String
) : ExtractedEntity()

data class ExtractedBrand(
    override val span: TextSpan,
    override val rawText: String,
    val brandId: String,
    val brandName: String,
    val brandKind: BrandKind,
    val officialDomains: List<String>
) : ExtractedEntity()

data class ExtractionResult(
    val urls: List<ExtractedUrl>,
    val fileNames: List<ExtractedFileName>,
    val phones: List<ExtractedPhone>,
    val upiIds: List<ExtractedUpiId>,
    val amounts: List<ExtractedAmount>,
    val otpCodes: List<ExtractedOtpCode>,
    val brands: List<ExtractedBrand>
)
