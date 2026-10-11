package app.posato.feature.enforcement

/** Android shares the phone limits, as it shares the phone presentation. */
internal actual val devicePauseLimits: PauseLimits = PauseLimits.IPHONE

/** Each Android apply replaces what is paused, so a running part keeps its own record. */
internal actual val deviceRetainsPausedItems: Boolean = true
