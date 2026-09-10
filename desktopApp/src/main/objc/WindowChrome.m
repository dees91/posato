#import <AppKit/AppKit.h>
#import <QuartzCore/QuartzCore.h>
#include <jni.h>

JNIEXPORT void JNICALL Java_app_posato_desktop_MacWindow_announce(
    JNIEnv *environment,
    jobject receiver,
    jstring message
) {
    const jchar *characters = (*environment)->GetStringChars(environment, message, NULL);
    if (characters == NULL) return;
    NSString *announcement = [[NSString alloc] initWithCharacters:characters
                                                         length:(*environment)->GetStringLength(environment, message)];
    (*environment)->ReleaseStringChars(environment, message, characters);
    dispatch_async(dispatch_get_main_queue(), ^{
        NSAccessibilityPostNotificationWithUserInfo(NSApp, NSAccessibilityAnnouncementRequestedNotification, @{
            NSAccessibilityAnnouncementKey: announcement,
            NSAccessibilityPriorityKey: @(NSAccessibilityPriorityMedium)
        });
    });
}

JNIEXPORT jboolean JNICALL Java_app_posato_desktop_MacWindow_highContrast(
    JNIEnv *environment,
    jobject receiver
) {
    return NSWorkspace.sharedWorkspace.accessibilityDisplayShouldIncreaseContrast ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT void JNICALL Java_app_posato_desktop_MacWindow_configure(
    JNIEnv *environment,
    jobject receiver,
    jlong windowHandle,
    jboolean fullscreen
) {
    if (windowHandle == 0) return;
    NSWindow *window = (__bridge NSWindow *)(void *)windowHandle;
    void (^configure)(void) = ^{
        if (window.toolbar == nil) {
            NSToolbar *toolbar = [[NSToolbar alloc] initWithIdentifier:@"app.posato.desktop.window"];
            toolbar.displayMode = NSToolbarDisplayModeIconOnly;
            window.toolbar = toolbar;
        }
        window.toolbarStyle = NSWindowToolbarStyleUnified;
        window.titlebarSeparatorStyle = NSTitlebarSeparatorStyleNone;
        window.backgroundColor = NSColor.clearColor;
        window.opaque = NO;
        NSView *frame = window.contentView.superview;
        frame.wantsLayer = YES;
        frame.layer.cornerRadius = fullscreen ? 0.0 : 20.0;
        frame.layer.masksToBounds = YES;
        [window invalidateShadow];
    };
    if ([NSThread isMainThread]) {
        configure();
    } else {
        dispatch_async(dispatch_get_main_queue(), configure);
    }
}
