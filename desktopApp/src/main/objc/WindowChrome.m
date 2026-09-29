#import <AppKit/AppKit.h>
#import <QuartzCore/QuartzCore.h>
#import <ServiceManagement/ServiceManagement.h>
#import <UserNotifications/UserNotifications.h>
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

static void PresenceActivate(void) {
    if (@available(macOS 14.0, *)) {
        [NSApp activate];
    } else {
        [NSApp activateIgnoringOtherApps:YES];
    }
}

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

JNIEXPORT void JNICALL Java_app_posato_desktop_MacPresenceNative_installLaunchProbe(JNIEnv *environment, jclass receiver) {
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

// 1 when this account has the console, 0 when another account does, -1 when unknown.
JNIEXPORT jint JNICALL Java_app_posato_desktop_MacPresenceNative_consoleIsOurs(JNIEnv *environment, jclass receiver) {
    CFDictionaryRef session = CGSessionCopyCurrentDictionary();
    if (session == NULL) return -1;
    CFBooleanRef onConsole = CFDictionaryGetValue(session, kCGSessionOnConsoleKey);
    jint result = onConsole == NULL ? -1 : (CFBooleanGetValue(onConsole) ? 1 : 0);
    CFRelease(session);
    return result;
}

JNIEXPORT jboolean JNICALL Java_app_posato_desktop_MacPresenceNative_launchedAtLogin(JNIEnv *environment, jclass receiver) {
    return presenceLaunchedAtLogin ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jboolean JNICALL Java_app_posato_desktop_MacPresenceNative_start(JNIEnv *environment, jclass receiver, jstring closeWindowTitle) {
    if ((*environment)->GetJavaVM(environment, &presenceVirtualMachine) != JNI_OK) return JNI_FALSE;
    jclass events = (*environment)->FindClass(environment, "app/posato/desktop/MacPresenceEvents");
    if (events == NULL) {
        if ((*environment)->ExceptionCheck(environment)) (*environment)->ExceptionClear(environment);
        return JNI_FALSE;
    }
    presenceMenuOpened = (*environment)->GetStaticMethodID(environment, events, "onMenuOpened", "()V");
    presenceMenuClosed = (*environment)->GetStaticMethodID(environment, events, "onMenuClosed", "()V");
    presenceMenuAction = (*environment)->GetStaticMethodID(environment, events, "onMenuAction", "(I)V");
    presencePowerOff = (*environment)->GetStaticMethodID(environment, events, "onPowerOff", "()V");
    presenceAlertFinished = (*environment)->GetStaticMethodID(environment, events, "onAlertFinished", "(II)V");
    if (presenceMenuOpened == NULL || presenceMenuClosed == NULL || presenceMenuAction == NULL || presencePowerOff == NULL ||
        presenceAlertFinished == NULL) {
        if ((*environment)->ExceptionCheck(environment)) (*environment)->ExceptionClear(environment);
        return JNI_FALSE;
    }
    presenceClass = (*environment)->NewGlobalRef(environment, events);
    (*environment)->DeleteLocalRef(environment, events);
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

JNIEXPORT void JNICALL Java_app_posato_desktop_MacPresenceNative_setMenu(
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

JNIEXPORT void JNICALL Java_app_posato_desktop_MacPresenceNative_setMainWindowVisible(JNIEnv *environment, jclass receiver, jboolean visible) {
    BOOL shown = visible == JNI_TRUE;
    dispatch_async(dispatch_get_main_queue(), ^{
        presenceMainWindowVisible = shown;
        PresenceApplyPolicy(nil);
        if (shown) PresenceActivate();
    });
}

JNIEXPORT void JNICALL Java_app_posato_desktop_MacPresenceNative_presentAlert(
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
        PresenceActivate();
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

JNIEXPORT jint JNICALL Java_app_posato_desktop_MacPresenceNative_loginItemStatus(JNIEnv *environment, jclass receiver) {
    return (jint)SMAppService.mainAppService.status;
}

JNIEXPORT jint JNICALL Java_app_posato_desktop_MacPresenceNative_setLoginItem(JNIEnv *environment, jclass receiver, jboolean enabled) {
    NSError *error = nil;
    if (enabled == JNI_TRUE) {
        [SMAppService.mainAppService registerAndReturnError:&error];
    } else {
        [SMAppService.mainAppService unregisterAndReturnError:&error];
    }
    return (jint)SMAppService.mainAppService.status;
}

@interface PosatoNotificationDelegate : NSObject <UNUserNotificationCenterDelegate>
@end

@implementation PosatoNotificationDelegate
- (void)userNotificationCenter:(UNUserNotificationCenter *)center
       willPresentNotification:(UNNotification *)notification
         withCompletionHandler:(void (^)(UNNotificationPresentationOptions))completionHandler {
    completionHandler(UNNotificationPresentationOptionBanner | UNNotificationPresentationOptionSound);
}
@end

static PosatoNotificationDelegate *notificationDelegate;

static UNUserNotificationCenter *NotificationCenter(void) {
    if (NSBundle.mainBundle.bundleIdentifier == nil) return nil;
    UNUserNotificationCenter *center = UNUserNotificationCenter.currentNotificationCenter;
    static dispatch_once_t once;
    dispatch_once(&once, ^{
        notificationDelegate = [[PosatoNotificationDelegate alloc] init];
        center.delegate = notificationDelegate;
    });
    return center;
}

static jint NotificationPermissionCode(UNAuthorizationStatus status) {
    switch (status) {
        case UNAuthorizationStatusNotDetermined:
            return 0;
        case UNAuthorizationStatusDenied:
            return 2;
        default:
            return 1;
    }
}

static jint NotificationPermissionNow(int64_t timeoutSeconds) {
    UNUserNotificationCenter *center = NotificationCenter();
    if (center == nil) return 2;
    __block jint code = 0;
    dispatch_semaphore_t done = dispatch_semaphore_create(0);
    [center getNotificationSettingsWithCompletionHandler:^(UNNotificationSettings *settings) {
        code = NotificationPermissionCode(settings.authorizationStatus);
        dispatch_semaphore_signal(done);
    }];
    dispatch_semaphore_wait(done, dispatch_time(DISPATCH_TIME_NOW, timeoutSeconds * NSEC_PER_SEC));
    return code;
}

JNIEXPORT jint JNICALL Java_app_posato_desktop_MacNotificationsNative_permission(JNIEnv *environment, jclass receiver) {
    return NotificationPermissionNow(5);
}

JNIEXPORT jint JNICALL Java_app_posato_desktop_MacNotificationsNative_requestPermission(JNIEnv *environment, jclass receiver) {
    UNUserNotificationCenter *center = NotificationCenter();
    if (center == nil) return 2;
    dispatch_semaphore_t done = dispatch_semaphore_create(0);
    [center requestAuthorizationWithOptions:(UNAuthorizationOptionAlert | UNAuthorizationOptionSound)
                                        completionHandler:^(BOOL granted, NSError *error) {
                                            dispatch_semaphore_signal(done);
                                        }];
    dispatch_semaphore_wait(done, dispatch_time(DISPATCH_TIME_NOW, 600 * NSEC_PER_SEC));
    return NotificationPermissionNow(5);
}

static UNMutableNotificationContent *NotificationContent(JNIEnv *environment, jstring title, jstring body) {
    UNMutableNotificationContent *content = [[UNMutableNotificationContent alloc] init];
    content.title = PresenceString(environment, title);
    content.body = PresenceString(environment, body);
    content.sound = UNNotificationSound.defaultSound;
    return content;
}

JNIEXPORT void JNICALL Java_app_posato_desktop_MacNotificationsNative_schedule(
    JNIEnv *environment,
    jclass receiver,
    jstring identifier,
    jstring title,
    jstring body,
    jdouble seconds
) {
    if (seconds <= 0 || NotificationCenter() == nil) return;
    UNTimeIntervalNotificationTrigger *trigger = [UNTimeIntervalNotificationTrigger triggerWithTimeInterval:seconds repeats:NO];
    UNNotificationRequest *request = [UNNotificationRequest requestWithIdentifier:PresenceString(environment, identifier)
                                                                          content:NotificationContent(environment, title, body)
                                                                          trigger:trigger];
    [NotificationCenter() addNotificationRequest:request withCompletionHandler:nil];
}

JNIEXPORT void JNICALL Java_app_posato_desktop_MacNotificationsNative_post(JNIEnv *environment, jclass receiver, jstring title, jstring body) {
    if (NotificationCenter() == nil) return;
    UNNotificationRequest *request = [UNNotificationRequest requestWithIdentifier:NSUUID.UUID.UUIDString
                                                                          content:NotificationContent(environment, title, body)
                                                                          trigger:nil];
    [NotificationCenter() addNotificationRequest:request withCompletionHandler:nil];
}

JNIEXPORT void JNICALL Java_app_posato_desktop_MacNotificationsNative_cancel(JNIEnv *environment, jclass receiver, jstring identifier) {
    if (NotificationCenter() == nil) return;
    [NotificationCenter() removePendingNotificationRequestsWithIdentifiers:@[ PresenceString(environment, identifier) ]];
}

JNIEXPORT jint JNICALL Java_app_posato_desktop_MacNotificationsNative_readFlag(JNIEnv *environment, jclass receiver, jstring key) {
    id value = [NSUserDefaults.standardUserDefaults objectForKey:PresenceString(environment, key)];
    if (value == nil) return -1;
    return [value boolValue] ? 1 : 0;
}

JNIEXPORT void JNICALL Java_app_posato_desktop_MacNotificationsNative_writeFlag(JNIEnv *environment, jclass receiver, jstring key, jboolean value) {
    [NSUserDefaults.standardUserDefaults setBool:(value == JNI_TRUE) forKey:PresenceString(environment, key)];
}

static JavaVM *backVirtualMachine;
static jclass backClass;
static jmethodID backSwipe;
static volatile BOOL backAvailable = NO;
static id backMonitor;

typedef NS_ENUM(jint, PosatoBackSwipe) {
    PosatoBackSwipeStarted = 0,
    PosatoBackSwipeChanged = 1,
    PosatoBackSwipeCompleted = 2,
    PosatoBackSwipeCancelled = 3,
};

static void BackSwipeCall(PosatoBackSwipe phase, CGFloat amount) {
    JNIEnv *environment = NULL;
    if (backVirtualMachine == NULL || backClass == NULL || backSwipe == NULL) return;
    jint state = (*backVirtualMachine)->GetEnv(backVirtualMachine, (void **)&environment, JNI_VERSION_1_8);
    if (state == JNI_EDETACHED) {
        if ((*backVirtualMachine)->AttachCurrentThreadAsDaemon(backVirtualMachine, (void **)&environment, NULL) != JNI_OK) return;
    } else if (state != JNI_OK) {
        return;
    }
    (*environment)->CallStaticVoidMethod(environment, backClass, backSwipe, phase, (jfloat)amount);
    if ((*environment)->ExceptionCheck(environment)) (*environment)->ExceptionClear(environment);
}

// Swipe between pages, as in Safari: a horizontal two-finger scroll that begins while a screen
// offers Back is tracked by AppKit and reported as a back gesture with its progress.
static void BackSwipeTrack(NSEvent *event) {
    if (!backAvailable || event.phase != NSEventPhaseBegan || ![NSEvent isSwipeTrackingFromScrollEventsEnabled]) return;
    NSWindow *window = event.window;
    if (window == nil || window != NSApp.mainWindow || window.attachedSheet != nil || NSApp.modalWindow != nil) return;
    if (fabs(event.scrollingDeltaX) <= fabs(event.scrollingDeltaY)) return;
    __block BOOL started = NO;
    __block BOOL decided = NO;
    __block BOOL lifted = NO;
    __block CGFloat liftedAmount = 0;
    [event trackSwipeEventWithOptions:NSEventSwipeTrackingLockDirection | NSEventSwipeTrackingClampGestureAmount
             dampenAmountThresholdMin:0
                                  max:1
                         usingHandler:^(CGFloat gestureAmount, NSEventPhase phase, BOOL isComplete, BOOL *stop) {
        if (decided) return;
        if (!started) {
            started = YES;
            BackSwipeCall(PosatoBackSwipeStarted, gestureAmount);
        }
        // After the fingers lift AppKit settles toward 1 when the swipe counts, speed included, and
        // toward 0 when it does not. The screen changes on that first settling frame instead of after
        // the settling, which has nothing on screen to move.
        BOOL settledTowardBack = lifted && gestureAmount > liftedAmount;
        BOOL settledAway = lifted && gestureAmount < liftedAmount;
        if (isComplete || settledTowardBack || settledAway || phase == NSEventPhaseCancelled) {
            decided = YES;
            BOOL back = isComplete ? gestureAmount >= 1.0 : settledTowardBack;
            BackSwipeCall(back ? PosatoBackSwipeCompleted : PosatoBackSwipeCancelled, gestureAmount);
        } else {
            if (phase == NSEventPhaseEnded) {
                lifted = YES;
                liftedAmount = gestureAmount;
            }
            BackSwipeCall(PosatoBackSwipeChanged, gestureAmount);
        }
    }];
}

JNIEXPORT void JNICALL Java_app_posato_desktop_MacBackGesture_install(JNIEnv *environment, jobject receiver) {
    if (backClass == NULL) {
        if ((*environment)->GetJavaVM(environment, &backVirtualMachine) != JNI_OK) return;
        jclass gesture = (*environment)->FindClass(environment, "app/posato/desktop/MacBackGesture");
        if (gesture == NULL) {
            if ((*environment)->ExceptionCheck(environment)) (*environment)->ExceptionClear(environment);
            return;
        }
        backSwipe = (*environment)->GetStaticMethodID(environment, gesture, "onSwipe", "(IF)V");
        if (backSwipe == NULL) {
            if ((*environment)->ExceptionCheck(environment)) (*environment)->ExceptionClear(environment);
            (*environment)->DeleteLocalRef(environment, gesture);
            return;
        }
        backClass = (*environment)->NewGlobalRef(environment, gesture);
        (*environment)->DeleteLocalRef(environment, gesture);
    }
    dispatch_async(dispatch_get_main_queue(), ^{
        if (backMonitor != nil) return;
        backMonitor = [NSEvent addLocalMonitorForEventsMatchingMask:NSEventMaskScrollWheel handler:^NSEvent *(NSEvent *event) {
            BackSwipeTrack(event);
            return event;
        }];
    });
}

JNIEXPORT void JNICALL Java_app_posato_desktop_MacBackGesture_setAvailable(JNIEnv *environment, jobject receiver, jboolean available) {
    backAvailable = available == JNI_TRUE;
}
