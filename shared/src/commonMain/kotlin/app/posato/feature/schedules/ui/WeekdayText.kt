package app.posato.feature.schedules.ui

import androidx.compose.runtime.Composable
import app.posato.core.designsystem.PosatoDevice
import app.posato.generated.resources.Res
import app.posato.generated.resources.schedules_allow_screen_time_computer
import app.posato.generated.resources.schedules_allow_screen_time_ipad
import app.posato.generated.resources.schedules_allow_screen_time_iphone
import app.posato.generated.resources.schedules_allow_screen_time_mac
import app.posato.generated.resources.schedules_allow_screen_time_phone
import app.posato.generated.resources.schedules_prepare_computer
import app.posato.generated.resources.schedules_prepare_ipad
import app.posato.generated.resources.schedules_prepare_iphone
import app.posato.generated.resources.schedules_prepare_phone
import app.posato.generated.resources.weekday_friday
import app.posato.generated.resources.weekday_monday
import app.posato.generated.resources.weekday_saturday
import app.posato.generated.resources.weekday_short_fri
import app.posato.generated.resources.weekday_short_mon
import app.posato.generated.resources.weekday_short_sat
import app.posato.generated.resources.weekday_short_sun
import app.posato.generated.resources.weekday_short_thu
import app.posato.generated.resources.weekday_short_tue
import app.posato.generated.resources.weekday_short_wed
import app.posato.generated.resources.weekday_sunday
import app.posato.generated.resources.weekday_thursday
import app.posato.generated.resources.weekday_tuesday
import app.posato.generated.resources.weekday_wednesday
import org.jetbrains.compose.resources.stringResource

/** Monday through Sunday, as the schedule editor shows each day. */
@Composable
internal fun ScheduleDay.fullLabel(): String {
    val name = when (this) {
        ScheduleDay.MONDAY -> Res.string.weekday_monday
        ScheduleDay.TUESDAY -> Res.string.weekday_tuesday
        ScheduleDay.WEDNESDAY -> Res.string.weekday_wednesday
        ScheduleDay.THURSDAY -> Res.string.weekday_thursday
        ScheduleDay.FRIDAY -> Res.string.weekday_friday
        ScheduleDay.SATURDAY -> Res.string.weekday_saturday
        ScheduleDay.SUNDAY -> Res.string.weekday_sunday
    }
    return stringResource(name)
}

/** Mon through Sun. [weekday] is 0 for Monday, as on the wire. */
@Composable
internal fun shortWeekday(weekday: Int): String {
    val name = when (weekday) {
        ScheduleDay.MONDAY.ordinal -> Res.string.weekday_short_mon
        ScheduleDay.TUESDAY.ordinal -> Res.string.weekday_short_tue
        ScheduleDay.WEDNESDAY.ordinal -> Res.string.weekday_short_wed
        ScheduleDay.THURSDAY.ordinal -> Res.string.weekday_short_thu
        ScheduleDay.FRIDAY.ordinal -> Res.string.weekday_short_fri
        ScheduleDay.SATURDAY.ordinal -> Res.string.weekday_short_sat
        else -> Res.string.weekday_short_sun
    }
    return stringResource(name)
}

/** The Screen Time consent sentence for the device that is asking. */
@Composable
internal fun schedulesAllowScreenTime(device: PosatoDevice): String {
    val name = when (device) {
        PosatoDevice.Mac -> Res.string.schedules_allow_screen_time_mac
        PosatoDevice.IPhone -> Res.string.schedules_allow_screen_time_iphone
        PosatoDevice.IPad -> Res.string.schedules_allow_screen_time_ipad
        PosatoDevice.Linux, PosatoDevice.Windows -> Res.string.schedules_allow_screen_time_computer
        PosatoDevice.Android -> Res.string.schedules_allow_screen_time_phone
    }
    return stringResource(name)
}

/** The heading while schedules still need the device prepared. */
@Composable
internal fun schedulesPrepare(device: PosatoDevice): String {
    val name = when (device) {
        PosatoDevice.IPhone -> Res.string.schedules_prepare_iphone
        PosatoDevice.IPad -> Res.string.schedules_prepare_ipad
        PosatoDevice.Linux, PosatoDevice.Windows, PosatoDevice.Mac -> Res.string.schedules_prepare_computer
        PosatoDevice.Android -> Res.string.schedules_prepare_phone
    }
    return stringResource(name)
}
