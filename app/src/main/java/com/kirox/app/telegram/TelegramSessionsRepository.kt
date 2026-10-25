package com.kirox.app.telegram

import org.drinkless.tdlib.TdApi

/** One signed-in device or app on the account. */
data class TelegramSession(
    val id: Long,
    val isCurrent: Boolean,
    val isOfficial: Boolean,
    val application: String,
    val device: String,
    val platform: String,
    val location: String,
    /** Unix seconds. */
    val lastActive: Int,
    val unconfirmed: Boolean
)

/** Active sessions of the account (Settings → Devices): list and terminate. */
class TelegramSessionsRepository(private val client: TelegramClient) {

    /** Current session first, then the rest by last activity. */
    suspend fun sessions(): List<TelegramSession> =
        client.query(TdApi.GetActiveSessions()).sessions.map { it.toSession() }
            .sortedWith(compareByDescending<TelegramSession> { it.isCurrent }.thenByDescending { it.lastActive })

    suspend fun terminate(id: Long) { client.query(TdApi.TerminateSession(id)) }

    suspend fun terminateOthers() { client.query(TdApi.TerminateAllOtherSessions()) }

    private fun TdApi.Session.toSession() = TelegramSession(
        id = id,
        isCurrent = isCurrent,
        isOfficial = isOfficialApplication,
        application = listOf(applicationName, applicationVersion).filter { it.isNotBlank() }.joinToString(" "),
        device = deviceModel,
        platform = listOf(platform, systemVersion).filter { it.isNotBlank() }.joinToString(" "),
        location = location.orEmpty(),
        lastActive = lastActiveDate,
        unconfirmed = isUnconfirmed
    )
}
