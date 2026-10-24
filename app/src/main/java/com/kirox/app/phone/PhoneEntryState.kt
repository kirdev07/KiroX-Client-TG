package com.kirox.app.phone

import android.content.Context
import androidx.compose.runtime.*

@Stable
class PhoneEntryState private constructor(
    val resolver: CountryResolver,
    initialCountry: Country
) {
    var country by mutableStateOf(initialCountry)
        private set
    var digits by mutableStateOf("")
        private set
    var status by mutableStateOf<PhoneStatus>(PhoneStatus.Idle)
        private set
    val formattedNumber: String get() = resolver.formatNational(digits, country)
    val internationalNumber: String get() = country.dialCode + digits

    fun selectCountry(value: Country) {
        country = value
        resolver.save(value)
        status = PhoneStatus.Idle
    }

    fun detectAutomatically(): Boolean {
        val detected = resolver.automaticCountry() ?: return false
        selectCountry(detected)
        return true
    }

    fun updateNumber(raw: String) {
        val detected = resolver.detectInternational(raw, country)
        if (detected != null && detected.regionCode != country.regionCode) {
            country = detected
            resolver.save(detected)
        }
        digits = resolver.nationalDigits(raw, country)
        status = PhoneStatus.Idle
    }

    fun validate(): Boolean {
        val validation = resolver.validation(country, digits)
        status = when (validation) {
            PhoneValidation.Empty -> PhoneStatus.Error("Введите номер телефона.")
            PhoneValidation.Invalid -> PhoneStatus.Error("Проверьте номер телефона и код страны.")
            PhoneValidation.Possible -> PhoneStatus.Error("Номер выглядит неполным или неточным.")
            PhoneValidation.Valid -> PhoneStatus.Ready("Номер проверен.")
        }
        return validation == PhoneValidation.Valid
    }

    fun showExternalError(message: String) { status = PhoneStatus.Error(message) }

    companion object {
        fun create(context: Context): PhoneEntryState {
            val resolver = CountryResolver(context.applicationContext)
            return PhoneEntryState(resolver, resolver.initialCountry())
        }
    }
}

sealed interface PhoneStatus {
    data object Idle : PhoneStatus
    data class Error(val message: String) : PhoneStatus
    data class Ready(val message: String) : PhoneStatus
}
