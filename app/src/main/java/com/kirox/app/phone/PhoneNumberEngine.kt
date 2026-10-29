package com.kirox.app.phone

import com.google.i18n.phonenumbers.PhoneNumberUtil

internal class PhoneNumberEngine(
    private val phoneUtil: PhoneNumberUtil = PhoneNumberUtil.getInstance()
) {
    fun detectInternationalRegion(raw: String, currentRegion: String): String? {
        val normalized = normalize(raw)
        if (!normalized.startsWith("+") || normalized.length < 2) return null
        val digits = normalized.drop(1)
        val callingCode = (1..minOf(3, digits.length))
            .mapNotNull { digits.take(it).toIntOrNull() }
            .firstOrNull { phoneUtil.getRegionCodesForCountryCode(it).isNotEmpty() }
            ?: return null
        val regions = phoneUtil.getRegionCodesForCountryCode(callingCode)
        if (regions.size == 1) return regions.first()

        val parsedRegion = runCatching {
            phoneUtil.getRegionCodeForNumber(phoneUtil.parse(normalized, "ZZ"))
        }.getOrNull()
        if (!parsedRegion.isNullOrBlank()) return parsedRegion

        return currentRegion.takeIf { phoneUtil.getCountryCodeForRegion(it) == callingCode }
    }

    fun nationalDigits(raw: String, country: Country): String {
        val normalized = normalize(raw)
        if (!normalized.startsWith("+")) return normalized.filter(Char::isDigit).take(18)
        return runCatching {
            // Keeps an Italian-style leading zero, which nationalNumber drops.
            phoneUtil.getNationalSignificantNumber(phoneUtil.parse(normalized, "ZZ"))
        }.getOrElse {
            normalized.drop(1).removePrefix(country.callingCode.toString()).filter(Char::isDigit)
        }.take(18)
    }

    fun formatNational(digits: String, regionCode: String): String {
        val formatter = phoneUtil.getAsYouTypeFormatter(regionCode)
        var formatted = ""
        digits.filter(Char::isDigit).forEach { formatted = formatter.inputDigit(it) }
        return formatted
    }

    fun validation(regionCode: String, digits: String): PhoneValidation {
        if (digits.isBlank()) return PhoneValidation.Empty
        val number = runCatching { phoneUtil.parse(digits, regionCode) }.getOrNull()
            ?: return PhoneValidation.Invalid
        return when {
            phoneUtil.isValidNumberForRegion(number, regionCode) -> PhoneValidation.Valid
            phoneUtil.isPossibleNumber(number) -> PhoneValidation.Possible
            else -> PhoneValidation.Invalid
        }
    }

    /**
     * The number as Telegram expects it (+77011234567). A national prefix typed by habit
     * ("8 701…", "07911…") is dropped instead of being sent after the country code.
     */
    fun international(regionCode: String, digits: String): String? = runCatching {
        phoneUtil.format(phoneUtil.parse(digits, regionCode), PhoneNumberUtil.PhoneNumberFormat.E164)
    }.getOrNull()

    private fun normalize(raw: String) = raw.trim().replace(Regex("[^+\\d]"), "")
}
