package app.posato.feature.sync.ui

/**
 * Whether this platform's **Remove workspace** keeps resuming within one press,
 * so the row may promise that the removal continues. Only the Mac does today;
 * the iPhone keeps its per-press cap (`IOS-008`) and shows its usual state.
 */
internal expect val workspaceRemovalResumes: Boolean
