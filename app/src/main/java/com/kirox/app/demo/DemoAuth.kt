package com.kirox.app.demo

/** Local-only demo gate. It has no dependency on TDLib, network or account state. */
object DemoAuth {
    const val codeLength = 5
    private const val demoCode = "12345"

    fun verify(code: String): Boolean = code == demoCode
}
