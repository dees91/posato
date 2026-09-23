#import <AppKit/AppKit.h>
#import <Sparkle/Sparkle.h>
#include <jni.h>

static const jint PosatoInstallStageNew = 0;
static const jint PosatoInstallStagePending = 1;

static JavaVM *posatoVirtualMachine;
static jclass posatoUpdaterClass;
static jmethodID posatoInstallRequested;
static jmethodID posatoCycleFinished;

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

@interface PosatoUserDriver : NSObject <SPUUserDriver>
- (instancetype)initWithStandardDriver:(SPUStandardUserDriver *)standardDriver;
- (void)completeAdmission:(jlong)token granted:(BOOL)granted refusal:(jint)refusal;
@end

@implementation PosatoUserDriver {
    SPUStandardUserDriver *_standard;
    NSMutableDictionary<NSNumber *, void (^)(SPUUserUpdateChoice)> *_pendingReplies;
    jlong _nextToken;
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
    }
}

- (void)completeAdmission:(jlong)token granted:(BOOL)granted refusal:(jint)refusal {
    void (^reply)(SPUUserUpdateChoice) = _pendingReplies[@(token)];
    if (reply == nil) return;
    [_pendingReplies removeObjectForKey:@(token)];
    if (granted) {
        reply(SPUUserUpdateChoiceInstall);
        return;
    }
    reply(SPUUserUpdateChoiceDismiss);
    NSAlert *alert = [[NSAlert alloc] init];
    alert.messageText = @"The update can't be installed now";
    alert.informativeText = refusal == 1
        ? @"End the current session first, then check for updates again."
        : @"Posato could not confirm that blocking has stopped. Check the Mac setup, then try again.";
    [alert runModal];
}

- (void)dropPendingReplies {
    [_pendingReplies removeAllObjects];
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

@interface PosatoUpdaterDelegate : NSObject <SPUUpdaterDelegate>
@end

@implementation PosatoUpdaterDelegate

- (void)updater:(SPUUpdater *)updater didFinishUpdateCycleForUpdateCheck:(SPUUpdateCheck)updateCheck error:(nullable NSError *)error {
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
    NSMenuItem *item = [[NSMenuItem alloc] initWithTitle:@"Check for Updates…" action:@selector(checkForUpdates:) keyEquivalent:@""];
    item.target = posatoMenuTarget;
    [applicationMenu insertItem:item atIndex:MIN((NSInteger)1, applicationMenu.numberOfItems)];
}

static BOOL PosatoUpdaterConfigured(void) {
    NSDictionary *information = NSBundle.mainBundle.infoDictionary;
    NSString *feed = information[@"SUFeedURL"];
    NSString *key = information[@"SUPublicEDKey"];
    return [feed isKindOfClass:NSString.class] && feed.length > 0 && [key isKindOfClass:NSString.class] && key.length > 0;
}

JNIEXPORT jboolean JNICALL Java_app_posato_desktop_update_MacUpdater_nativeStart(
    JNIEnv *environment,
    jobject receiver
) {
    if (!PosatoUpdaterConfigured()) return JNI_FALSE;
    if ((*environment)->GetJavaVM(environment, &posatoVirtualMachine) != JNI_OK) return JNI_FALSE;
    jclass updaterClass = (*environment)->FindClass(environment, "app/posato/desktop/update/MacUpdater");
    if (updaterClass == NULL) {
        PosatoClearPendingException(environment);
        return JNI_FALSE;
    }
    posatoInstallRequested = (*environment)->GetStaticMethodID(environment, updaterClass, "onInstallRequested", "(JLjava/lang/String;I)V");
    posatoCycleFinished = (*environment)->GetStaticMethodID(environment, updaterClass, "onCycleFinished", "()V");
    if (posatoInstallRequested == NULL || posatoCycleFinished == NULL) {
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
        NSError *error = nil;
        if (![posatoUpdater startUpdater:&error]) {
            posatoUpdater = nil;
            return;
        }
        PosatoInstallMenuItem();
    });
    return JNI_TRUE;
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
