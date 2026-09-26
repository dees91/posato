package app.posato.feature.notifications

import app.posato.feature.session.domain.LocalSessionStatus
import app.posato.feature.session.domain.SessionOrigin
import app.posato.feature.session.domain.SessionRecord
import app.posato.feature.sync.domain.SessionId
import app.posato.feature.sync.testIdentifier
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class SessionNoticesTest {
    private val start = 1_790_000_000_000L

    private fun active(
        origin: SessionOrigin,
        seed: Int,
    ): LocalSessionStatus.Active {
        val end = start + 25 * 60_000L
        return LocalSessionStatus.Active(SessionRecord(SessionId(testIdentifier(seed)), start, end), end - start, origin = origin)
    }

    @Test
    fun `given notifications turned off then nothing is scheduled or announced`() = runTest {
        val platform = RecordingNotificationPlatform(enabled = false)

        SessionNotices(platform, FixedTexts).follow(
            flowOf(LocalSessionStatus.Inactive, active(SessionOrigin.ADOPTED, seed = 1), active(SessionOrigin.LOCAL, seed = 2)),
        )

        assertEquals(listOf("cancel"), platform.calls)
    }

    @Test
    fun `given two pauses started here then the system is asked for permission only once`() = runTest {
        val platform = RecordingNotificationPlatform(enabled = true)

        SessionNotices(platform, FixedTexts).follow(
            flowOf(
                LocalSessionStatus.Inactive,
                active(SessionOrigin.LOCAL, seed = 1),
                LocalSessionStatus.Inactive,
                active(SessionOrigin.LOCAL, seed = 2),
            ),
        )

        assertEquals(1, platform.calls.count { it == "request" })
    }
}

private object FixedTexts : SessionNoticeTexts {
    override suspend fun pauseOver(): NoticeText {
        return NoticeText("over", "over")
    }

    override suspend fun startedElsewhere(needsResume: Boolean): NoticeText {
        return NoticeText("started", "started")
    }
}

private class RecordingNotificationPlatform(
    private var enabled: Boolean,
) : SessionNotificationPlatform {
    private var asked = false
    val calls = mutableListOf<String>()

    override val receivedPauseNeedsResume: Boolean = false

    override fun permission(handler: (NotificationPermission) -> Unit) {
        handler(NotificationPermission.NOT_DETERMINED)
    }

    override fun requestPermission(handler: (NotificationPermission) -> Unit) {
        calls += "request"
        handler(NotificationPermission.ALLOWED)
    }

    override fun scheduleEnd(
        atEpochMillis: Long,
        title: String,
        body: String,
    ) {
        calls += "schedule"
    }

    override fun cancelEnd() {
        calls += "cancel"
    }

    override fun post(
        title: String,
        body: String,
    ) {
        calls += "post"
    }

    override fun isEnabled(): Boolean {
        return enabled
    }

    override fun setEnabled(enabled: Boolean) {
        this.enabled = enabled
    }

    override fun wasPermissionAsked(): Boolean {
        return asked
    }

    override fun markPermissionAsked() {
        asked = true
    }
}
