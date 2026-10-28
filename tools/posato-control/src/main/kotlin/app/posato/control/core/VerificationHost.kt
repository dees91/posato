package app.posato.control.core

/**
 * A file in the guest user's home that tells Posato it runs on a verification host. Only then does the desktop app
 * publish its control ids to accessibility, where a screen reader would otherwise read them.
 */
const val CONTROL_IDS_MARKER: String = ".posato-control-ids"
