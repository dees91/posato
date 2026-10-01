package app.posato.feature.enforcement

/** Android has no enforcement yet; it shares the phone limits, as it shares the phone presentation. */
internal actual val devicePauseLimits: PauseLimits = PauseLimits.IPHONE
