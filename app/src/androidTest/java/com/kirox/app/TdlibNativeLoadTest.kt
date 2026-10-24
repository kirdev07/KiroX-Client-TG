package com.kirox.app

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.drinkless.tdlib.Client
import org.drinkless.tdlib.TdApi
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TdlibNativeLoadTest {
    @Test
    fun nativeLibraryLoadsAndExecutesSynchronousRequest() {
        System.loadLibrary("tdjni")
        val result = Client.execute<TdApi.OptionValue>(TdApi.GetOption("version"))
        assertNotNull(result)
    }
}
