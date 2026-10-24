package com.kirox.app.phone

data class Country(
    val regionCode: String,
    val name: String,
    val callingCode: Int
) {
    val flag: String
        get() = regionCode.uppercase().map { char ->
            Character.toChars(0x1F1E6 + (char.code - 'A'.code)).concatToString()
        }.joinToString("")

    val dialCode: String get() = "+$callingCode"
}
