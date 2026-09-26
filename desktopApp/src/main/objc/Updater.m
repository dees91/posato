#import <AppKit/AppKit.h>
#import <Sparkle/Sparkle.h>
#include <jni.h>

static const jint PosatoInstallStageNew = 0;
static const jint PosatoInstallStagePending = 1;
static const jint PosatoRefusalSessionActive = 1;
static const jint PosatoRefusalOtherInstance = 2;

typedef NS_ENUM(NSUInteger, PosatoCopyIndex) {
    PosatoCopyCheckForUpdates,
    PosatoCopyPreparing,
    PosatoCopyRefusedTitle,
    PosatoCopyRefusedSession,
    PosatoCopyRefusedOtherInstance,
    PosatoCopyRefusedOther,
    PosatoCopyConsentTitle,
    PosatoCopyConsentMessage,
    PosatoCopyConsentAllow,
    PosatoCopyConsentDeny,
    PosatoCopyCount,
};

static NSString *const PosatoAutomaticChecksDefaultsKey = @"SUEnableAutomaticChecks";

static JavaVM *posatoVirtualMachine;
static jclass posatoUpdaterClass;
static jmethodID posatoInstallRequested;
static jmethodID posatoCycleFinished;
static jmethodID posatoStateChanged;
static NSArray<NSString *> *posatoCopy;

static JNIEnv *PosatoAttachedEnvironment(void) {
    JNIEnv *environment = NULL;
    if (posatoVirtualMachine == NULL) return NULL;
    jint state = (*posatoVirtualMachine)->GetEnv(posatoVirtualMachine, (void **)&environment, JNI_VERSION_1_8);
    if (state == JNI_EDETACHED) {
        if ((*posatoVirtualMachine)->AttachCurrentThreadAsDaemon(posatoVirtualMachine, (void **)&environment, NULL) != JNI_OK) {
            return NULL;
        }
    } else if (state != JNI_OK) {
        return NULL;
    }
    return environment;
}

static void PosatoClearPendingException(JNIEnv *environment) {
    if ((*environment)->ExceptionCheck(environment)) {
        (*environment)->ExceptionClear(environment);
    }
}

static NSWindow *PosatoAlertWindow(void) {
    NSWindow *window = NSApp.keyWindow ?: NSApp.mainWindow;
    if (window.visible && ![window isKindOfClass:NSPanel.class]) return window;
    for (NSWindow *candidate in NSApp.windows) {
        if (candidate.visible && ![candidate isKindOfClass:NSPanel.class]) return candidate;
    }
    return nil;
}

static void PosatoPresentAlert(NSAlert *alert, void (^completion)(NSModalResponse)) {
    NSWindow *window = PosatoAlertWindow();
    if (window == nil) {
        [NSApp activateIgnoringOtherApps:YES];
        completion([alert runModal]);
        return;
    }
    [alert beginSheetModalForWindow:window completionHandler:completion];
}

@interface PosatoUserDriver : NSObject <SPUUserDriver>
- (instancetype)initWithStandardDriver:(SPUStandardUserDriver *)standardDriver;
- (void)completeAdmission:(jlong)token granted:(BOOL)granted refusal:(jint)refusal;
@end

@implementation PosatoUserDriver {
    SPUStandardUserDriver *_standard;
    NSMutableDictionary<NSNumber *, void (^)(SPUUserUpdateChoice)> *_pendingReplies;
    jlong _nextToken;
    NSPanel *_preparingPanel;
}

- (instancetype)initWithStandardDriver:(SPUStandardUserDriver *)standardDriver {
    self = [super init];
    if (self != nil) {
        _standard = standardDriver;
        _pendingReplies = [NSMutableDictionary dictionary];
        _nextToken = 1;
    }
    return self;
}

- (void)showUpdatePermissionRequest:(SPUUpdatePermissionRequest *)request reply:(void (^)(SUUpdatePermissionResponse *))reply {
    [_standard showUpdatePermissionRequest:request reply:reply];
}

- (void)showUserInitiatedUpdateCheckWithCancellation:(void (^)(void))cancellation {
    [_standard showUserInitiatedUpdateCheckWithCancellation:cancellation];
}

- (void)showUpdateFoundWithAppcastItem:(SUAppcastItem *)appcastItem state:(SPUUserUpdateState *)state reply:(void (^)(SPUUserUpdateChoice))reply {
    BOOL installing = state.stage == SPUUserUpdateStageInstalling;
    NSString *targetBuild = [appcastItem.versionString copy];
    __weak PosatoUserDriver *weakSelf = self;
    [_standard showUpdateFoundWithAppcastItem:appcastItem state:state reply:^(SPUUserUpdateChoice choice) {
        PosatoUserDriver *strongSelf = weakSelf;
        if (choice != SPUUserUpdateChoiceInstall) {
            reply(installing && choice == SPUUserUpdateChoiceDismiss ? SPUUserUpdateChoiceSkip : choice);
            return;
        }
        if (strongSelf == nil) {
            reply(SPUUserUpdateChoiceDismiss);
            return;
        }
        [strongSelf requestAdmissionForTarget:targetBuild
                                        stage:installing ? PosatoInstallStagePending : PosatoInstallStageNew
                                        reply:reply];
    }];
}

- (void)requestAdmissionForTarget:(NSString *)targetBuild stage:(jint)stage reply:(void (^)(SPUUserUpdateChoice))reply {
    JNIEnv *environment = PosatoAttachedEnvironment();
    if (environment == NULL || posatoUpdaterClass == NULL || targetBuild.length == 0) {
        reply(SPUUserUpdateChoiceDismiss);
        return;
    }
    jlong token = _nextToken++;
    _pendingReplies[@(token)] = [reply copy];
    jstring target = (*environment)->NewStringUTF(environment, targetBuild.UTF8String);
    if (target == NULL) {
        PosatoClearPendingException(environment);
        [_pendingReplies removeObjectForKey:@(token)];
        reply(SPUUserUpdateChoiceDismiss);
        return;
    }
    (*environment)->CallStaticVoidMethod(environment, posatoUpdaterClass, posatoInstallRequested, token, target, stage);
    (*environment)->DeleteLocalRef(environment, target);
    if ((*environment)->ExceptionCheck(environment)) {
        PosatoClearPendingException(environment);
        void (^pending)(SPUUserUpdateChoice) = _pendingReplies[@(token)];
        [_pendingReplies removeObjectForKey:@(token)];
        if (pending != nil) pending(SPUUserUpdateChoiceDismiss);
        return;
    }
    if (_pendingReplies[@(token)] != nil) {
        [self showPreparingPanel];
    }
}

- (void)showPreparingPanel {
    if (_preparingPanel != nil) return;
    NSPanel *panel = [[NSPanel alloc] initWithContentRect:NSMakeRect(0, 0, 320, 88)
                                                styleMask:NSWindowStyleMaskTitled
                                                  backing:NSBackingStoreBuffered
                                                    defer:NO];
    panel.title = @"Posato";
    panel.releasedWhenClosed = NO;
    NSProgressIndicator *spinner = [[NSProgressIndicator alloc] initWithFrame:NSMakeRect(20, 28, 32, 32)];
    spinner.style = NSProgressIndicatorStyleSpinning;
    [spinner startAnimation:nil];
    NSTextField *label = [NSTextField labelWithString:posatoCopy[PosatoCopyPreparing]];
    label.frame = NSMakeRect(64, 34, 236, 20);
    [panel.contentView addSubview:spinner];
    [panel.contentView addSubview:label];
    [panel center];
    [panel makeKeyAndOrderFront:nil];
    _preparingPanel = panel;
}

- (void)closePreparingPanel {
    [_preparingPanel close];
    _preparingPanel = nil;
}

- (void)completeAdmission:(jlong)token granted:(BOOL)granted refusal:(jint)refusal {
    void (^reply)(SPUUserUpdateChoice) = _pendingReplies[@(token)];
    if (reply == nil) return;
    [self closePreparingPanel];
    [_pendingReplies removeObjectForKey:@(token)];
    if (granted) {
        reply(SPUUserUpdateChoiceInstall);
        return;
    }
    reply(SPUUserUpdateChoiceDismiss);
    NSAlert *alert = [[NSAlert alloc] init];
    alert.messageText = posatoCopy[PosatoCopyRefusedTitle];
    if (refusal == PosatoRefusalSessionActive) {
        alert.informativeText = posatoCopy[PosatoCopyRefusedSession];
    } else if (refusal == PosatoRefusalOtherInstance) {
        alert.informativeText = posatoCopy[PosatoCopyRefusedOtherInstance];
    } else {
        alert.informativeText = posatoCopy[PosatoCopyRefusedOther];
    }
    PosatoPresentAlert(alert, ^(NSModalResponse response) {});
}

- (void)dropPendingReplies {
    [_pendingReplies removeAllObjects];
    [self closePreparingPanel];
}

- (void)showUpdateReleaseNotesWithDownloadData:(SPUDownloadData *)downloadData {
    [_standard showUpdateReleaseNotesWithDownloadData:downloadData];
}

- (void)showUpdateReleaseNotesFailedToDownloadWithError:(NSError *)error {
    [_standard showUpdateReleaseNotesFailedToDownloadWithError:error];
}

- (void)showUpdateNotFoundWithError:(NSError *)error acknowledgement:(void (^)(void))acknowledgement {
    [_standard showUpdateNotFoundWithError:error acknowledgement:acknowledgement];
}

- (void)showUpdaterError:(NSError *)error acknowledgement:(void (^)(void))acknowledgement {
    [_standard showUpdaterError:error acknowledgement:acknowledgement];
}

- (void)showDownloadInitiatedWithCancellation:(void (^)(void))cancellation {
    [_standard showDownloadInitiatedWithCancellation:cancellation];
}

- (void)showDownloadDidReceiveExpectedContentLength:(uint64_t)expectedContentLength {
    [_standard showDownloadDidReceiveExpectedContentLength:expectedContentLength];
}

- (void)showDownloadDidReceiveDataOfLength:(uint64_t)length {
    [_standard showDownloadDidReceiveDataOfLength:length];
}

- (void)showDownloadDidStartExtractingUpdate {
    [_standard showDownloadDidStartExtractingUpdate];
}

- (void)showExtractionReceivedProgress:(double)progress {
    [_standard showExtractionReceivedProgress:progress];
}

- (void)showReadyToInstallAndRelaunch:(void (^)(SPUUserUpdateChoice))reply {
    [_standard showReadyToInstallAndRelaunch:^(SPUUserUpdateChoice choice) {
        reply(choice == SPUUserUpdateChoiceInstall ? SPUUserUpdateChoiceInstall : SPUUserUpdateChoiceSkip);
    }];
}

- (void)showInstallingUpdateWithApplicationTerminated:(BOOL)applicationTerminated retryTerminatingApplication:(void (^)(void))retryTerminatingApplication {
    [_standard showInstallingUpdateWithApplicationTerminated:applicationTerminated retryTerminatingApplication:retryTerminatingApplication];
}

- (void)showUpdateInstalledAndRelaunched:(BOOL)relaunched acknowledgement:(void (^)(void))acknowledgement {
    [_standard showUpdateInstalledAndRelaunched:relaunched acknowledgement:acknowledgement];
}

- (void)showUpdateInFocus {
    [_standard showUpdateInFocus];
}

- (void)dismissUpdateInstallation {
    [self dropPendingReplies];
    [_standard dismissUpdateInstallation];
}

@end

static void PosatoForgetCookies(void) {
    NSHTTPCookieStorage *storage = NSHTTPCookieStorage.sharedHTTPCookieStorage;
    storage.cookieAcceptPolicy = NSHTTPCookieAcceptPolicyNever;
    for (NSHTTPCookie *cookie in [storage.cookies copy]) {
        [storage deleteCookie:cookie];
    }
}

@interface PosatoUpdaterDelegate : NSObject <SPUUpdaterDelegate>
@end

@implementation PosatoUpdaterDelegate

- (void)updater:(SPUUpdater *)updater didFinishUpdateCycleForUpdateCheck:(SPUUpdateCheck)updateCheck error:(nullable NSError *)error {
    PosatoForgetCookies();
    JNIEnv *environment = PosatoAttachedEnvironment();
    if (environment == NULL || posatoUpdaterClass == NULL) return;
    (*environment)->CallStaticVoidMethod(environment, posatoUpdaterClass, posatoCycleFinished);
    PosatoClearPendingException(environment);
}

@end

@interface PosatoUpdateMenuTarget : NSObject
@end

static SPUUpdater *posatoUpdater;
static PosatoUserDriver *posatoUserDriver;
static PosatoUpdaterDelegate *posatoUpdaterDelegate;
static PosatoUpdateMenuTarget *posatoMenuTarget;

static void PosatoPublishState(void) {
    JNIEnv *environment = PosatoAttachedEnvironment();
    if (environment == NULL || posatoUpdaterClass == NULL || posatoUpdater == nil) return;
    jboolean automaticChecks = posatoUpdater.automaticallyChecksForUpdates ? JNI_TRUE : JNI_FALSE;
    jboolean canCheck = posatoUpdater.canCheckForUpdates ? JNI_TRUE : JNI_FALSE;
    (*environment)->CallStaticVoidMethod(environment, posatoUpdaterClass, posatoStateChanged, automaticChecks, canCheck);
    PosatoClearPendingException(environment);
}

@interface PosatoUpdaterObserver : NSObject
@end

@implementation PosatoUpdaterObserver

- (void)observeValueForKeyPath:(NSString *)keyPath ofObject:(id)object change:(NSDictionary *)change context:(void *)context {
    dispatch_async(dispatch_get_main_queue(), ^{
        PosatoPublishState();
    });
}

@end

static PosatoUpdaterObserver *posatoObserver;
static BOOL posatoConsentShowing;

@implementation PosatoUpdateMenuTarget

- (void)checkForUpdates:(id)sender {
    [posatoUpdater checkForUpdates];
}

- (BOOL)validateMenuItem:(NSMenuItem *)menuItem {
    return posatoUpdater.canCheckForUpdates;
}

@end

static void PosatoInstallMenuItem(void) {
    NSMenu *applicationMenu = NSApp.mainMenu.itemArray.firstObject.submenu;
    if (applicationMenu == nil) return;
    NSMenuItem *item = [[NSMenuItem alloc] initWithTitle:posatoCopy[PosatoCopyCheckForUpdates] action:@selector(checkForUpdates:) keyEquivalent:@""];
    item.target = posatoMenuTarget;
    [applicationMenu insertItem:item atIndex:MIN((NSInteger)1, applicationMenu.numberOfItems)];
}

static BOOL PosatoUpdaterConfigured(void) {
    NSDictionary *information = NSBundle.mainBundle.infoDictionary;
    NSString *feed = information[@"SUFeedURL"];
    NSString *key = information[@"SUPublicEDKey"];
    return [feed isKindOfClass:NSString.class] && feed.length > 0 && [key isKindOfClass:NSString.class] && key.length > 0;
}

static NSArray<NSString *> *PosatoReadCopy(JNIEnv *environment, jobjectArray copy) {
    if (copy == NULL || (*environment)->GetArrayLength(environment, copy) != (jsize)PosatoCopyCount) return nil;
    NSMutableArray<NSString *> *strings = [NSMutableArray arrayWithCapacity:PosatoCopyCount];
    for (jsize index = 0; index < (jsize)PosatoCopyCount; index++) {
        jstring element = (jstring)(*environment)->GetObjectArrayElement(environment, copy, index);
        const jchar *characters = element == NULL ? NULL : (*environment)->GetStringChars(environment, element, NULL);
        if (characters == NULL) {
            PosatoClearPendingException(environment);
            if (element != NULL) (*environment)->DeleteLocalRef(environment, element);
            return nil;
        }
        NSString *string = [NSString stringWithCharacters:(const unichar *)characters
                                                   length:(NSUInteger)(*environment)->GetStringLength(environment, element)];
        (*environment)->ReleaseStringChars(environment, element, characters);
        (*environment)->DeleteLocalRef(environment, element);
        if (string.length == 0) return nil;
        [strings addObject:string];
    }
    return [strings copy];
}

JNIEXPORT jboolean JNICALL Java_app_posato_desktop_update_MacUpdater_nativeStart(
    JNIEnv *environment,
    jobject receiver,
    jobjectArray copy
) {
    if (!PosatoUpdaterConfigured()) return JNI_FALSE;
    posatoCopy = PosatoReadCopy(environment, copy);
    if (posatoCopy == nil) return JNI_FALSE;
    if ((*environment)->GetJavaVM(environment, &posatoVirtualMachine) != JNI_OK) return JNI_FALSE;
    jclass updaterClass = (*environment)->FindClass(environment, "app/posato/desktop/update/MacUpdater");
    if (updaterClass == NULL) {
        PosatoClearPendingException(environment);
        return JNI_FALSE;
    }
    posatoInstallRequested = (*environment)->GetStaticMethodID(environment, updaterClass, "onInstallRequested", "(JLjava/lang/String;I)V");
    posatoCycleFinished = (*environment)->GetStaticMethodID(environment, updaterClass, "onCycleFinished", "()V");
    posatoStateChanged = (*environment)->GetStaticMethodID(environment, updaterClass, "onStateChanged", "(ZZ)V");
    if (posatoInstallRequested == NULL || posatoCycleFinished == NULL || posatoStateChanged == NULL) {
        PosatoClearPendingException(environment);
        return JNI_FALSE;
    }
    posatoUpdaterClass = (*environment)->NewGlobalRef(environment, updaterClass);
    (*environment)->DeleteLocalRef(environment, updaterClass);
    dispatch_async(dispatch_get_main_queue(), ^{
        NSBundle *bundle = NSBundle.mainBundle;
        SPUStandardUserDriver *standard = [[SPUStandardUserDriver alloc] initWithHostBundle:bundle delegate:nil];
        posatoUserDriver = [[PosatoUserDriver alloc] initWithStandardDriver:standard];
        posatoUpdaterDelegate = [[PosatoUpdaterDelegate alloc] init];
        posatoMenuTarget = [[PosatoUpdateMenuTarget alloc] init];
        posatoUpdater = [[SPUUpdater alloc] initWithHostBundle:bundle
                                             applicationBundle:bundle
                                                    userDriver:posatoUserDriver
                                                      delegate:posatoUpdaterDelegate];
        posatoUpdater.userAgentString = @"PosatoUpdater";
        posatoUpdater.httpHeaders = @{@"Accept-Language": @"en"};
        PosatoForgetCookies();
        NSError *error = nil;
        if (![posatoUpdater startUpdater:&error]) {
            posatoUpdater = nil;
            return;
        }
        PosatoInstallMenuItem();
        posatoObserver = [[PosatoUpdaterObserver alloc] init];
        [posatoUpdater addObserver:posatoObserver forKeyPath:@"automaticallyChecksForUpdates" options:0 context:NULL];
        [posatoUpdater addObserver:posatoObserver forKeyPath:@"canCheckForUpdates" options:0 context:NULL];
        PosatoPublishState();
    });
    return JNI_TRUE;
}

JNIEXPORT void JNICALL Java_app_posato_desktop_update_MacUpdateSettings_nativeSetAutomaticChecks(
    JNIEnv *environment,
    jobject receiver,
    jboolean enabled
) {
    dispatch_async(dispatch_get_main_queue(), ^{
        if (posatoUpdater == nil) return;
        posatoUpdater.automaticallyChecksForUpdates = enabled == JNI_TRUE;
    });
}

JNIEXPORT void JNICALL Java_app_posato_desktop_update_MacUpdateSettings_nativeCheckForUpdates(
    JNIEnv *environment,
    jobject receiver
) {
    dispatch_async(dispatch_get_main_queue(), ^{
        if (posatoUpdater == nil || !posatoUpdater.canCheckForUpdates) return;
        [posatoUpdater checkForUpdates];
    });
}

JNIEXPORT void JNICALL Java_app_posato_desktop_update_MacUpdateSettings_nativeAskForAutomaticChecksOnce(
    JNIEnv *environment,
    jobject receiver
) {
    dispatch_async(dispatch_get_main_queue(), ^{
        if (posatoUpdater == nil) return;
        if (posatoConsentShowing || [NSUserDefaults.standardUserDefaults objectForKey:PosatoAutomaticChecksDefaultsKey] != nil) return;
        posatoConsentShowing = YES;
        NSAlert *alert = [[NSAlert alloc] init];
        alert.messageText = posatoCopy[PosatoCopyConsentTitle];
        alert.informativeText = posatoCopy[PosatoCopyConsentMessage];
        [alert addButtonWithTitle:posatoCopy[PosatoCopyConsentAllow]];
        [alert addButtonWithTitle:posatoCopy[PosatoCopyConsentDeny]];
        PosatoPresentAlert(alert, ^(NSModalResponse response) {
            posatoConsentShowing = NO;
            if (response == NSAlertFirstButtonReturn) {
                posatoUpdater.automaticallyChecksForUpdates = YES;
            } else if (response == NSAlertSecondButtonReturn) {
                posatoUpdater.automaticallyChecksForUpdates = NO;
            }
        });
    });
}

JNIEXPORT void JNICALL Java_app_posato_desktop_update_MacUpdater_nativeCompleteAdmission(
    JNIEnv *environment,
    jobject receiver,
    jlong token,
    jboolean granted,
    jint refusal
) {
    dispatch_async(dispatch_get_main_queue(), ^{
        [posatoUserDriver completeAdmission:token granted:granted == JNI_TRUE refusal:refusal];
    });
}
