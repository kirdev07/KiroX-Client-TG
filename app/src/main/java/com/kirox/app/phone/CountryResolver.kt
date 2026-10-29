package com.kirox.app.phone

import androidx.core.content.edit
import android.content.Context
import android.telephony.TelephonyManager
import com.google.i18n.phonenumbers.PhoneNumberUtil
import java.util.Locale

class CountryResolver(private val context: Context) {
    private val phoneUtil = PhoneNumberUtil.getInstance()
    private val phoneEngine = PhoneNumberEngine(phoneUtil)
    private val preferences = context.getSharedPreferences("kirox_phone", Context.MODE_PRIVATE)

    val countries: List<Country> by lazy {
        phoneUtil.supportedRegions
            .mapNotNull(::countryForRegion)
            .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name })
    }

    fun initialCountry(): Country {
        val candidates = listOfNotNull(
            preferences.getString(KEY_LAST_COUNTRY, null),
            simRegion(),
            Locale.getDefault().country
        )
        return candidates.firstNotNullOfOrNull(::countryForRegion)
            ?: countryForRegion("US")
            ?: countries.first()
    }

    fun automaticCountry(): Country? = simRegion()?.let(::countryForRegion)
        ?: countryForRegion(Locale.getDefault().country)

    fun save(country: Country) {
        val recent = listOf(country.regionCode) + recentCountries().map { it.regionCode }
        preferences.edit {
            putString(KEY_LAST_COUNTRY, country.regionCode)
            putString(KEY_RECENT_COUNTRIES, recent.distinct().take(5).joinToString(","))
        }
    }

    fun recentCountries(): List<Country> = preferences.getString(KEY_RECENT_COUNTRIES, "")
        .orEmpty().split(',').mapNotNull(::countryForRegion)

    fun countryForRegion(regionCode: String): Country? {
        val code = regionCode.uppercase(Locale.ROOT)
        if (code !in phoneUtil.supportedRegions) return null
        val displayLocale = Locale.getDefault()
        val localizedName = Locale.Builder().setRegion(code).build().getDisplayCountry(displayLocale)
        return Country(code, localizedName.ifBlank { code }, phoneUtil.getCountryCodeForRegion(code))
    }

    fun detectInternational(raw: String, current: Country): Country? {
        return phoneEngine.detectInternationalRegion(raw, current.regionCode)?.let(::countryForRegion)
    }

    fun nationalDigits(raw: String, country: Country): String {
        return phoneEngine.nationalDigits(raw, country)
    }

    fun formatNational(digits: String, country: Country): String {
        return phoneEngine.formatNational(digits, country.regionCode)
    }

    fun international(country: Country, digits: String): String? = phoneEngine.international(country.regionCode, digits)

    fun validation(country: Country, digits: String): PhoneValidation {
        return phoneEngine.validation(country.regionCode, digits)
    }

    private fun simRegion(): String? = runCatching {
        (context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager)
            ?.simCountryIso
            ?.takeIf { it.isNotBlank() }
            ?.uppercase(Locale.ROOT)
    }.getOrNull()

    private companion object {
        const val KEY_LAST_COUNTRY = "last_country"
        const val KEY_RECENT_COUNTRIES = "recent_countries"
    }
}

enum class PhoneValidation { Empty, Invalid, Possible, Valid }
