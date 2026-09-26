#import <AppKit/AppKit.h>
#import <QuartzCore/QuartzCore.h>
#import <ServiceManagement/ServiceManagement.h>
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


typedef NS_ENUM(jint, PosatoAlertChoice) {
    PosatoAlertChoicePrimary = 0,
    PosatoAlertChoiceSecondary = 1,
};

static JavaVM *presenceVirtualMachine;
static jclass presenceClass;
static jmethodID presenceMenuOpened;
static jmethodID presenceMenuClosed;
static jmethodID presenceMenuAction;
static jmethodID presencePowerOff;
static jmethodID presenceAlertFinished;
static NSStatusItem *presenceStatusItem;
static id presenceMenuDelegate;
static id presenceMenuTarget;
static BOOL presenceMainWindowVisible = YES;
static BOOL presenceLaunchedAtLogin = NO;
static id presenceLaunchObserver;

static JNIEnv *PresenceEnvironment(void) {
    JNIEnv *environment = NULL;
    if (presenceVirtualMachine == NULL) return NULL;
    jint state = (*presenceVirtualMachine)->GetEnv(presenceVirtualMachine, (void **)&environment, JNI_VERSION_1_8);
    if (state == JNI_EDETACHED) {
        if ((*presenceVirtualMachine)->AttachCurrentThreadAsDaemon(presenceVirtualMachine, (void **)&environment, NULL) != JNI_OK) {
            return NULL;
        }
    } else if (state != JNI_OK) {
        return NULL;
    }
    return environment;
}

static void PresenceCallWith(jmethodID method, jint first, jint second, int count) {
    JNIEnv *environment = PresenceEnvironment();
    if (environment == NULL || presenceClass == NULL || method == NULL) return;
    if (count == 2) {
        (*environment)->CallStaticVoidMethod(environment, presenceClass, method, first, second);
    } else if (count == 1) {
        (*environment)->CallStaticVoidMethod(environment, presenceClass, method, first);
    } else {
        (*environment)->CallStaticVoidMethod(environment, presenceClass, method);
    }
    if ((*environment)->ExceptionCheck(environment)) {
        (*environment)->ExceptionClear(environment);
    }
}

static NSString *PresenceString(JNIEnv *environment, jstring value) {
    if (value == NULL) return @"";
    const jchar *characters = (*environment)->GetStringChars(environment, value, NULL);
    if (characters == NULL) return @"";
    NSString *string = [[NSString alloc] initWithCharacters:characters length:(*environment)->GetStringLength(environment, value)];
    (*environment)->ReleaseStringChars(environment, value, characters);
    return string;
}

static BOOL PresenceHasVisibleWindow(NSWindow *excluded) {
    for (NSWindow *window in NSApp.windows) {
        if (window == excluded || !window.visible) continue;
        if ([NSStringFromClass(window.class) hasPrefix:@"NSStatusBar"]) continue;
        if ((window.styleMask & NSWindowStyleMaskTitled) == 0) continue;
        return YES;
    }
    return NO;
}

static void PresenceApplyPolicy(NSWindow *closing) {
    BOOL regular = presenceMainWindowVisible || PresenceHasVisibleWindow(closing);
    NSApplicationActivationPolicy policy = regular ? NSApplicationActivationPolicyRegular : NSApplicationActivationPolicyAccessory;
    if (NSApp.activationPolicy != policy) [NSApp setActivationPolicy:policy];
}

static NSImage *PresenceMark(BOOL filled) {
    NSImage *image = [NSImage imageWithSize:NSMakeSize(18, 18) flipped:NO drawingHandler:^BOOL(NSRect rect) {
        NSAffineTransform *slant = [NSAffineTransform transform];
        [slant translateXBy:9 yBy:9];
        [slant rotateByDegrees:-12];
        [slant translateXBy:-9 yBy:-9];
        NSBezierPath *leading = [NSBezierPath bezierPathWithRoundedRect:NSMakeRect(4.5, 3, 3.5, 10) xRadius:1.75 yRadius:1.75];
        NSBezierPath *trailing = [NSBezierPath bezierPathWithRoundedRect:NSMakeRect(10, 3, 3.5, 13) xRadius:1.75 yRadius:1.75];
        [leading transformUsingAffineTransform:slant];
        [trailing transformUsingAffineTransform:slant];
        [NSColor.blackColor set];
        if (filled) {
            [leading fill];
            [trailing fill];
        } else {
            leading.lineWidth = 1.2;
            trailing.lineWidth = 1.2;
            [leading stroke];
            [trailing fill];
        }
        return YES;
    }];
    image.template = YES;
    return image;
}

@interface PosatoPresenceMenuDelegate : NSObject <NSMenuDelegate>
@end

@implementation PosatoPresenceMenuDelegate
- (void)menuWillOpen:(NSMenu *)menu {
    PresenceCallWith(presenceMenuOpened, 0, 0, 0);
}

- (void)menuDidClose:(NSMenu *)menu {
    PresenceCallWith(presenceMenuClosed, 0, 0, 0);
}
@end

@interface PosatoPresenceMenuTarget : NSObject
- (void)choose:(NSMenuItem *)item;
@end

@implementation PosatoPresenceMenuTarget
- (void)choose:(NSMenuItem *)item {
    jint action = (jint)item.tag;
    dispatch_async(dispatch_get_global_queue(QOS_CLASS_USER_INITIATED, 0), ^{
        PresenceCallWith(presenceMenuAction, action, 0, 1);
    });
}
@end

JNIEXPORT void JNICALL Java_app_posato_desktop_MacPresence_nativeInstallLaunchProbe(JNIEnv *environment, jclass receiver) {
    presenceLaunchObserver = [NSNotificationCenter.defaultCenter
        addObserverForName:NSApplicationDidFinishLaunchingNotification
                    object:nil
                     queue:nil
                usingBlock:^(NSNotification *notification) {
                    NSAppleEventDescriptor *event = NSAppleEventManager.sharedAppleEventManager.currentAppleEvent;
                    NSAppleEventDescriptor *property = [event paramDescriptorForKeyword:keyAEPropData];
                    presenceLaunchedAtLogin = event.eventID == kAEOpenApplication && property.enumCodeValue == keyAELaunchedAsLogInItem;
                }];
}

JNIEXPORT jboolean JNICALL Java_app_posato_desktop_MacPresence_nativeLaunchedAtLogin(JNIEnv *environment, jclass receiver) {
    return presenceLaunchedAtLogin ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jboolean JNICALL Java_app_posato_desktop_MacPresence_nativeStart(JNIEnv *environment, jclass receiver, jstring closeWindowTitle) {
    if ((*environment)->GetJavaVM(environment, &presenceVirtualMachine) != JNI_OK) return JNI_FALSE;
    presenceMenuOpened = (*environment)->GetStaticMethodID(environment, receiver, "onMenuOpened", "()V");
    presenceMenuClosed = (*environment)->GetStaticMethodID(environment, receiver, "onMenuClosed", "()V");
    presenceMenuAction = (*environment)->GetStaticMethodID(environment, receiver, "onMenuAction", "(I)V");
    presencePowerOff = (*environment)->GetStaticMethodID(environment, receiver, "onPowerOff", "()V");
    presenceAlertFinished = (*environment)->GetStaticMethodID(environment, receiver, "onAlertFinished", "(II)V");
    if (presenceMenuOpened == NULL || presenceMenuClosed == NULL || presenceMenuAction == NULL || presencePowerOff == NULL ||
        presenceAlertFinished == NULL) {
        if ((*environment)->ExceptionCheck(environment)) (*environment)->ExceptionClear(environment);
        return JNI_FALSE;
    }
    presenceClass = (*environment)->NewGlobalRef(environment, receiver);
    NSString *closeTitle = PresenceString(environment, closeWindowTitle);
    dispatch_async(dispatch_get_main_queue(), ^{
        presenceMenuDelegate = [[PosatoPresenceMenuDelegate alloc] init];
        presenceMenuTarget = [[PosatoPresenceMenuTarget alloc] init];
        presenceStatusItem = [NSStatusBar.systemStatusBar statusItemWithLength:NSSquareStatusItemLength];
        presenceStatusItem.button.image = PresenceMark(NO);
        presenceStatusItem.menu = [[NSMenu alloc] init];
        presenceStatusItem.menu.delegate = presenceMenuDelegate;
        [NSWorkspace.sharedWorkspace.notificationCenter addObserverForName:NSWorkspaceWillPowerOffNotification
                                                                    object:nil
                                                                     queue:nil
                                                                usingBlock:^(NSNotification *notification) {
                                                                    PresenceCallWith(presencePowerOff, 0, 0, 0);
                                                                }];
        [NSNotificationCenter.defaultCenter addObserverForName:NSApplicationDidBecomeActiveNotification
                                                        object:nil
                                                         queue:nil
                                                    usingBlock:^(NSNotification *notification) {
                                                        PresenceApplyPolicy(nil);
                                                    }];
        [NSNotificationCenter.defaultCenter addObserverForName:NSWindowWillCloseNotification
                                                        object:nil
                                                         queue:nil
                                                    usingBlock:^(NSNotification *notification) {
                                                        NSWindow *closing = notification.object;
                                                        dispatch_async(dispatch_get_main_queue(), ^{
                                                            PresenceApplyPolicy(closing);
                                                        });
                                                    }];
        NSMenu *main = NSApp.mainMenu;
        if (main != nil && closeTitle.length > 0) {
            NSMenuItem *windowItem = [[NSMenuItem alloc] initWithTitle:@"Window" action:nil keyEquivalent:@""];
            NSMenu *windowMenu = [[NSMenu alloc] initWithTitle:@"Window"];
            [windowMenu addItemWithTitle:closeTitle action:@selector(performClose:) keyEquivalent:@"w"];
            windowItem.submenu = windowMenu;
            [main addItem:windowItem];
        }
    });
    return JNI_TRUE;
}

JNIEXPORT void JNICALL Java_app_posato_desktop_MacPresence_nativeUpdateMenu(
    JNIEnv *environment,
    jclass receiver,
    jobjectArray titles,
    jintArray actions,
    jbooleanArray enabled,
    jstring accessibility,
    jboolean filled
) {
    jsize count = (*environment)->GetArrayLength(environment, titles);
    NSMutableArray<NSString *> *itemTitles = [NSMutableArray arrayWithCapacity:(NSUInteger)count];
    for (jsize index = 0; index < count; index++) {
        jstring title = (jstring)(*environment)->GetObjectArrayElement(environment, titles, index);
        [itemTitles addObject:PresenceString(environment, title)];
        (*environment)->DeleteLocalRef(environment, title);
    }
    jint *actionValues = (*environment)->GetIntArrayElements(environment, actions, NULL);
    jboolean *enabledValues = (*environment)->GetBooleanArrayElements(environment, enabled, NULL);
    if (actionValues == NULL || enabledValues == NULL) return;
    NSMutableArray<NSNumber *> *itemActions = [NSMutableArray arrayWithCapacity:(NSUInteger)count];
    NSMutableArray<NSNumber *> *itemEnabled = [NSMutableArray arrayWithCapacity:(NSUInteger)count];
    for (jsize index = 0; index < count; index++) {
        [itemActions addObject:@(actionValues[index])];
        [itemEnabled addObject:@(enabledValues[index] == JNI_TRUE)];
    }
    (*environment)->ReleaseIntArrayElements(environment, actions, actionValues, JNI_ABORT);
    (*environment)->ReleaseBooleanArrayElements(environment, enabled, enabledValues, JNI_ABORT);
    NSString *description = PresenceString(environment, accessibility);
    BOOL fill = filled == JNI_TRUE;
    dispatch_async(dispatch_get_main_queue(), ^{
        if (presenceStatusItem == nil) return;
        NSMenu *menu = presenceStatusItem.menu;
        [menu removeAllItems];
        menu.autoenablesItems = NO;
        for (NSUInteger index = 0; index < itemTitles.count; index++) {
            NSInteger action = itemActions[index].integerValue;
            if (action < 0 && itemTitles[index].length == 0) {
                [menu addItem:NSMenuItem.separatorItem];
                continue;
            }
            NSMenuItem *item = [[NSMenuItem alloc] initWithTitle:itemTitles[index] action:nil keyEquivalent:@""];
            if (action >= 0) {
                item.action = @selector(choose:);
                item.target = presenceMenuTarget;
                item.tag = action;
            }
            item.enabled = itemEnabled[index].boolValue;
            [menu addItem:item];
        }
        presenceStatusItem.button.image = PresenceMark(fill);
        presenceStatusItem.button.image.accessibilityDescription = description;
        presenceStatusItem.button.accessibilityLabel = description;
        presenceStatusItem.button.toolTip = description;
    });
}

JNIEXPORT void JNICALL Java_app_posato_desktop_MacPresence_nativeSetMainWindowVisible(JNIEnv *environment, jclass receiver, jboolean visible) {
    BOOL shown = visible == JNI_TRUE;
    dispatch_async(dispatch_get_main_queue(), ^{
        presenceMainWindowVisible = shown;
        PresenceApplyPolicy(nil);
        if (shown) [NSApp activate];
    });
}

JNIEXPORT void JNICALL Java_app_posato_desktop_MacPresence_nativePresentAlert(
    JNIEnv *environment,
    jclass receiver,
    jint request,
    jstring title,
    jstring message,
    jstring primary,
    jstring secondary
) {
    NSString *alertTitle = PresenceString(environment, title);
    NSString *alertMessage = PresenceString(environment, message);
    NSString *primaryTitle = PresenceString(environment, primary);
    NSString *secondaryTitle = PresenceString(environment, secondary);
    dispatch_async(dispatch_get_main_queue(), ^{
        [NSApp activate];
        NSAlert *alert = [[NSAlert alloc] init];
        alert.messageText = alertTitle;
        alert.informativeText = alertMessage;
        [alert addButtonWithTitle:primaryTitle];
        if (secondaryTitle.length > 0) [alert addButtonWithTitle:secondaryTitle];
        void (^finish)(NSModalResponse) = ^(NSModalResponse response) {
            jint choice = response == NSAlertFirstButtonReturn ? PosatoAlertChoicePrimary : PosatoAlertChoiceSecondary;
            dispatch_async(dispatch_get_global_queue(QOS_CLASS_USER_INITIATED, 0), ^{
                PresenceCallWith(presenceAlertFinished, request, choice, 2);
            });
        };
        NSWindow *window = NSApp.keyWindow ?: NSApp.mainWindow;
        if (window != nil && window.visible && ![window isKindOfClass:NSPanel.class]) {
            [alert beginSheetModalForWindow:window completionHandler:finish];
        } else {
            finish([alert runModal]);
        }
    });
}

JNIEXPORT jint JNICALL Java_app_posato_desktop_MacPresence_nativeLoginItemStatus(JNIEnv *environment, jclass receiver) {
    return (jint)SMAppService.mainAppService.status;
}

JNIEXPORT jint JNICALL Java_app_posato_desktop_MacPresence_nativeSetLoginItem(JNIEnv *environment, jclass receiver, jboolean enabled) {
    NSError *error = nil;
    if (enabled == JNI_TRUE) {
        [SMAppService.mainAppService registerAndReturnError:&error];
    } else {
        [SMAppService.mainAppService unregisterAndReturnError:&error];
    }
    return (jint)SMAppService.mainAppService.status;
}
