#import <AppKit/AppKit.h>
#import <QuartzCore/QuartzCore.h>
#include <jni.h>

JNIEXPORT void JNICALL Java_app_posato_prototype_PrototypeMacWindow_configure(
    JNIEnv *environment,
    jobject receiver,
    jlong windowHandle,
    jboolean fullscreen
) {
    if (windowHandle == 0) return;
    void (^configure)(void) = ^{
        NSWindow *window = (__bridge NSWindow *)(void *)windowHandle;
        if (window.toolbar == nil) {
            NSToolbar *toolbar = [[NSToolbar alloc] initWithIdentifier:@"app.posato.prototype.window"];
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
        dispatch_sync(dispatch_get_main_queue(), configure);
    }
}
