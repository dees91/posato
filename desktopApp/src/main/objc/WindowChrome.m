#import <AppKit/AppKit.h>
#import <QuartzCore/QuartzCore.h>
#include <jni.h>

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
