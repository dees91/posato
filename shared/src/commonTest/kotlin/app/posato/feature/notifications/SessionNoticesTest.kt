package app.posato.feature.notifications

import app.posato.feature.session.domain.LocalSessionStatus
import app.posato.feature.session.domain.SessionOrigin
import app.posato.feature.session.domain.SessionRecord
import app.posato.feature.sync.domain.SessionId
import app.posato.feature.sync.testIdentifier
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

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

class SessionNoticesSwitchTest {
    @Test
    fun `given a pause when the switch goes off and on again then its end notice is withdrawn and restored`() = runTest {
        val platform = RecordingNotificationPlatform(enabled = true)
        val notices = SessionNotices(platform, FixedTexts)
        val start = 1_790_000_000_000L
        val end = start + 25 * 60_000L
        val active = LocalSessionStatus.Active(SessionRecord(SessionId(testIdentifier(1)), start, end), end - start, origin = SessionOrigin.ADOPTED)
        notices.follow(flowOf(active))
        platform.calls.clear()

        notices.setEnabled(false)
        notices.setEnabled(true)

        assertEquals(listOf("cancel", "schedule"), platform.calls)
    }

    @Test
    fun `given permission allowed after the first pause started then its end notice is scheduled again`() = runTest {
        val platform = RecordingNotificationPlatform(enabled = true)
        val start = 1_790_000_000_000L
        val end = start + 25 * 60_000L
        val active = LocalSessionStatus.Active(SessionRecord(SessionId(testIdentifier(1)), start, end), end - start, origin = SessionOrigin.LOCAL)

        SessionNotices(platform, FixedTexts).follow(flowOf(LocalSessionStatus.Inactive, active))

        assertEquals(listOf("cancel", "schedule", "request", "schedule"), platform.calls)
    }

    @Test
    fun `given an unanswered permission prompt when the pause ends early then its end is withdrawn at once and not restored by the answer`() =
        runTest {
            val platform = RecordingNotificationPlatform(enabled = true, holdRequest = true)
            val statuses = Channel<LocalSessionStatus?>(Channel.UNLIMITED)
            val start = 1_790_000_000_000L
            val end = start + 25 * 60_000L
            val active = LocalSessionStatus.Active(SessionRecord(SessionId(testIdentifier(1)), start, end), end - start, origin = SessionOrigin.LOCAL)
            val following = launch { SessionNotices(platform, FixedTexts).follow(statuses.receiveAsFlow()) }
            statuses.send(LocalSessionStatus.Inactive)
            statuses.send(active)
            runCurrent()
            statuses.send(LocalSessionStatus.Inactive)
            runCurrent()

            assertEquals(listOf("cancel", "schedule", "request", "cancel"), platform.calls)

            platform.answer(NotificationPermission.ALLOWED)
            runCurrent()
            following.cancel()

            assertEquals(listOf("cancel", "schedule", "request", "cancel"), platform.calls)
        }

    @Test
    fun `given the switch turned on during the first pause when permission is allowed then its end is scheduled again`() = runTest {
        val platform = RecordingNotificationPlatform(enabled = false)
        val notices = SessionNotices(platform, FixedTexts)
        val start = 1_790_000_000_000L
        val end = start + 25 * 60_000L
        val active = LocalSessionStatus.Active(SessionRecord(SessionId(testIdentifier(1)), start, end), end - start, origin = SessionOrigin.LOCAL)
        notices.follow(flowOf(LocalSessionStatus.Inactive, active))
        platform.calls.clear()

        notices.setEnabled(true)

        assertEquals(listOf("schedule", "request", "schedule"), platform.calls)
    }
}

class SessionNoticesStaleEndTest {
    @Test
    fun `given a pause that ends while its notice text loads then the old end is not scheduled`() = runTest {
        val platform = RecordingNotificationPlatform(enabled = true)
        val texts = GatedTexts()
        val notices = SessionNotices(platform, texts)
        val statuses = Channel<LocalSessionStatus?>(Channel.UNLIMITED)
        val start = 1_790_000_000_000L
        val end = start + 25 * 60_000L
        val active = LocalSessionStatus.Active(SessionRecord(SessionId(testIdentifier(1)), start, end), end - start, origin = SessionOrigin.ADOPTED)
        val following = launch { notices.follow(statuses.receiveAsFlow()) }
        statuses.send(active)
        runCurrent()
        platform.calls.clear()
        texts.hold = true

        launch { notices.setEnabled(true) }
        runCurrent()
        texts.hold = false
        statuses.send(LocalSessionStatus.Inactive)
        runCurrent()
        texts.gate.complete(Unit)
        runCurrent()
        following.cancel()

        assertEquals(listOf("cancel"), platform.calls.filter { it == "cancel" || it == "schedule" })
    }
}

class SessionNoticesPermissionTriggerTest {
    @Test
    fun `given no pause started here when the switch goes off and on then the system is not asked`() = runTest {
        val platform = RecordingNotificationPlatform(enabled = true)
        val notices = SessionNotices(platform, FixedTexts)
        notices.follow(flowOf(LocalSessionStatus.Inactive))
        platform.calls.clear()

        notices.setEnabled(false)
        notices.setEnabled(true)

        assertEquals(emptyList(), platform.calls.filter { it == "request" })
    }

    @Test
    fun `given the first local pause starts while the permission read is pending then the system is still asked`() = runTest {
        val platform = RecordingNotificationPlatform(enabled = true, holdPermission = true)
        val statuses = MutableStateFlow<LocalSessionStatus?>(LocalSessionStatus.Inactive)
        val start = 1_790_000_000_000L
        val end = start + 25 * 60_000L
        val following = launch { SessionNotices(platform, FixedTexts).follow(statuses) }
        runCurrent()

        statuses.value = LocalSessionStatus.Active(SessionRecord(SessionId(testIdentifier(1)), start, end), end - start, origin = SessionOrigin.LOCAL)
        runCurrent()
        platform.answerPermission(NotificationPermission.NOT_DETERMINED)
        runCurrent()
        following.cancel()

        assertTrue("request" in platform.calls)
    }

    @Test
    fun `given notices turned off while the started-elsewhere text loads then nothing is posted`() = runTest {
        val platform = RecordingNotificationPlatform(enabled = true)
        val texts = GatedTexts(holdStarted = true)
        val notices = SessionNotices(platform, texts)
        val statuses = Channel<LocalSessionStatus?>(Channel.UNLIMITED)
        val start = 1_790_000_000_000L
        val end = start + 25 * 60_000L
        val following = launch { notices.follow(statuses.receiveAsFlow()) }
        statuses.send(LocalSessionStatus.Inactive)
        statuses.send(LocalSessionStatus.Active(SessionRecord(SessionId(testIdentifier(1)), start, end), end - start, origin = SessionOrigin.ADOPTED))
        runCurrent()

        notices.setEnabled(false)
        texts.gate.complete(Unit)
        runCurrent()
        following.cancel()

        assertEquals(emptyList(), platform.calls.filter { it == "post" })
    }
}

private class GatedTexts(
    private val holdStarted: Boolean = false,
) : SessionNoticeTexts {
    val gate = CompletableDeferred<Unit>()
    var hold = false

    override suspend fun pauseOver(): NoticeText {
        if (hold) {
            gate.await()
        }
        return NoticeText("over", "over")
    }

    override suspend fun startedElsewhere(needsResume: Boolean): NoticeText {
        if (holdStarted) {
            gate.await()
        }
        return NoticeText("started", "started")
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
    private val holdRequest: Boolean = false,
    private val holdPermission: Boolean = false,
) : SessionNotificationPlatform {
    private var pendingPermission: ((NotificationPermission) -> Unit)? = null

    fun answerPermission(permission: NotificationPermission) {
        pendingPermission?.invoke(permission)
        pendingPermission = null
    }

    private var asked = false
    private var pendingAnswer: ((NotificationPermission) -> Unit)? = null
    val calls = mutableListOf<String>()

    fun answer(permission: NotificationPermission) {
        pendingAnswer?.invoke(permission)
        pendingAnswer = null
    }

    override val receivedPauseNeedsResume: Boolean = false

    override fun permission(handler: (NotificationPermission) -> Unit) {
        if (holdPermission) {
            pendingPermission = handler
        } else {
            handler(NotificationPermission.NOT_DETERMINED)
        }
    }

    override fun requestPermission(handler: (NotificationPermission) -> Unit) {
        calls += "request"
        if (holdRequest) {
            pendingAnswer = handler
        } else {
            handler(NotificationPermission.ALLOWED)
        }
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
