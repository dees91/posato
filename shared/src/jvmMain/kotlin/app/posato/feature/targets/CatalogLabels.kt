package app.posato.feature.targets

import app.posato.generated.resources.Res
import app.posato.generated.resources.action_cancel
import app.posato.generated.resources.catalog_choose
import app.posato.generated.resources.catalog_choose_apps
import kotlinx.coroutines.runBlocking
import org.jetbrains.compose.resources.getString

/** The app picker's Cancel, outside Compose, in the language the desktop process is using. */
public fun catalogCancelLabel(): String = runBlocking { getString(Res.string.action_cancel) }

/** The app picker's window title. */
public fun catalogChooseAppsLabel(): String = runBlocking { getString(Res.string.catalog_choose_apps) }

/** The app picker's confirm button. */
public fun catalogChooseLabel(): String = runBlocking { getString(Res.string.catalog_choose) }
