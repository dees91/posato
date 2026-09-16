import { capturePoint, LAYOUTS, type Device, type LayoutId, type Point } from "./layout";

// Executable projection of STORYBOARD.md. Frame numbers inside a scene are
// scene-relative; scenes overlap by SCENE_OVERLAP frames for the crossfade.
// Action targets are capture pixels of that device's PNG (Mac 1272 x 936,
// iPhone 660 x 1430), calibrated in Remotion Studio against the real captures.

export const VIDEO = { fps: 30, width: 1600, height: 1000 } as const;
export const SCENE_OVERLAP = 6;
export const SWAP_FRAMES = 8;
export const MAX_QUIET_FRAMES = 90;

export type Action = {
  readonly device: Device;
  readonly kind: "click" | "tap";
  readonly target: Point;
  readonly at: number;
  readonly travel?: number;
  readonly label: string;
  readonly swapTo?: number;
};

export type Callout = { readonly text: string; readonly from: number; readonly to: number };

export type DeviceTrack = { readonly captures: readonly string[]; readonly enterAt?: number };

export type StoryScene = {
  readonly id: string;
  readonly start: number;
  readonly end: number;
  readonly kind: "title" | "capture";
  readonly copy: Readonly<Record<string, string>>;
  readonly layout?: LayoutId;
  readonly layoutTo?: { readonly layout: LayoutId; readonly from: number; readonly to: number };
  readonly mac?: DeviceTrack;
  readonly iphone?: DeviceTrack;
  readonly actions?: readonly Action[];
  readonly callouts?: readonly Callout[];
  readonly pointerEntry?: Point;
  readonly pointerExit?: { readonly from: number; readonly to: number };
  readonly fadeOutAt?: number;
};

export const MAC_TARGETS = {
  pausedItemsSidebar: [119, 253],
  addField: [740, 212],
  addButton: [1158, 212],
  appsTab: [985, 116],
  chooseApps: [1133, 191],
  startSession: [415, 330],
  fortyFive: [487, 275],
  reviewSession: [415, 721],
  startThisPause: [417, 347],
  endSessionEarly: [423, 448],
  endSessionConfirm: [403, 229],
} as const satisfies Record<string, Point>;

export const IPHONE_TARGETS = {
  appsTab: [472, 420],
  chooseApps: [514, 530],
} as const satisfies Record<string, Point>;

const OPEN_COPY = {
  eyebrow: "Posato for Mac and iPhone",
  headline: "Pause. Then choose.",
  support: "A timed pause from the websites and apps you choose.",
} as const;

const CLOSE_COPY = {
  eyebrow: "Session active",
  headline: "A little room. Just for you.",
  footer: "Mac · iPhone · No account · No analytics · Apache 2.0",
} as const;

const websitesActions = (offset: number): readonly Action[] => [
  { device: "mac", kind: "click", target: MAC_TARGETS.addField, at: offset, label: "Add websites field", swapTo: 1 },
  { device: "mac", kind: "click", target: MAC_TARGETS.addButton, at: offset + 56, label: "Add", swapTo: 2 },
];

const iphoneAppsScene = (start: number, end: number): StoryScene => ({
  id: "apps-iphone",
  start,
  end,
  kind: "capture",
  layout: "phoneLead",
  mac: { captures: ["mac-apps.png"] },
  iphone: { captures: ["iphone-websites.png", "iphone-apps-empty.png", "iphone-apps.png"] },
  actions: [
    { device: "iphone", kind: "tap", target: IPHONE_TARGETS.appsTab, at: 30, label: "Apps tab", swapTo: 1 },
    { device: "iphone", kind: "tap", target: IPHONE_TARGETS.chooseApps, at: 84, label: "Choose apps", swapTo: 2 },
  ],
  callouts: [
    { text: "Choose apps on your iPhone.", from: 8, to: 90 },
    { text: "App choices stay on each device.", from: 94, to: end - start - SCENE_OVERLAP },
  ],
  copy: {},
});

const startScene = (start: number, end: number): StoryScene => ({
  id: "start",
  start,
  end,
  kind: "capture",
  layout: "macSolo",
  layoutTo: { layout: "macWithPhone", from: 42, to: 62 },
  mac: { captures: ["mac-review-45.png", "mac-active-45.png"] },
  iphone: { captures: ["iphone-active-45.png"], enterAt: 44 },
  pointerEntry: capturePoint(LAYOUTS.macSolo.mac, MAC_TARGETS.reviewSession),
  actions: [
    { device: "mac", kind: "click", target: MAC_TARGETS.startThisPause, at: 30, travel: 20, label: "Start this pause", swapTo: 1 },
  ],
  pointerExit: { from: 36, to: 52 },
  callouts: [
    { text: "Nothing is blocked until you start.", from: 4, to: 40 },
    { text: "Restrictions active.", from: 44, to: end - start - SCENE_OVERLAP },
  ],
  copy: {},
});

export const HERO: readonly StoryScene[] = [
  { id: "open", start: 0, end: 66, kind: "title", copy: OPEN_COPY },
  {
    id: "websites",
    start: 60,
    end: 246,
    kind: "capture",
    layout: "macSolo",
    mac: { captures: ["mac-websites-empty.png", "mac-websites-typed.png", "mac-websites-added.png"] },
    actions: websitesActions(36),
    pointerExit: { from: 140, to: 160 },
    callouts: [
      { text: "Add exact domains.", from: 10, to: 96 },
      { text: "One at a time, or paste a list.", from: 100, to: 180 },
    ],
    copy: {},
  },
  iphoneAppsScene(240, 390),
  {
    id: "duration",
    start: 384,
    end: 516,
    kind: "capture",
    layout: "macSolo",
    mac: { captures: ["mac-duration.png", "mac-duration-45.png", "mac-review-45.png"] },
    actions: [
      { device: "mac", kind: "click", target: MAC_TARGETS.fortyFive, at: 34, label: "45 min", swapTo: 1 },
      { device: "mac", kind: "click", target: MAC_TARGETS.reviewSession, at: 90, label: "Review session", swapTo: 2 },
    ],
    callouts: [
      { text: "5 minutes to 24 hours.", from: 6, to: 94 },
      { text: "One last look.", from: 98, to: 126 },
    ],
    copy: {},
  },
  startScene(510, 606),
  { id: "close", start: 600, end: 660, kind: "title", fadeOutAt: 44, copy: CLOSE_COPY },
];

export const WALKTHROUGH: readonly StoryScene[] = [
  {
    id: "open",
    start: 0,
    end: 90,
    kind: "title",
    copy: { ...OPEN_COPY, steps: "Choose what to pause. Set a duration. Start." },
  },
  {
    id: "websites",
    start: 84,
    end: 354,
    kind: "capture",
    layout: "macSolo",
    mac: {
      captures: ["mac-session-inactive.png", "mac-websites-empty.png", "mac-websites-typed.png", "mac-websites-added.png"],
    },
    actions: [
      { device: "mac", kind: "click", target: MAC_TARGETS.pausedItemsSidebar, at: 30, label: "Paused items", swapTo: 1 },
      { device: "mac", kind: "click", target: MAC_TARGETS.addField, at: 96, label: "Add websites field", swapTo: 2 },
      { device: "mac", kind: "click", target: MAC_TARGETS.addButton, at: 168, label: "Add", swapTo: 3 },
    ],
    pointerExit: { from: 230, to: 250 },
    callouts: [
      { text: "Choose what to pause.", from: 8, to: 92 },
      { text: "Add exact domains.", from: 94, to: 172 },
      { text: "One at a time, or paste a list.", from: 176, to: 264 },
    ],
    copy: {},
  },
  {
    id: "apps-mac",
    start: 348,
    end: 528,
    kind: "capture",
    layout: "macSolo",
    mac: { captures: ["mac-websites.png", "mac-apps-empty.png", "mac-apps.png"] },
    actions: [
      { device: "mac", kind: "click", target: MAC_TARGETS.appsTab, at: 30, label: "Apps tab", swapTo: 1 },
      { device: "mac", kind: "click", target: MAC_TARGETS.chooseApps, at: 88, label: "Choose apps", swapTo: 2 },
    ],
    pointerExit: { from: 140, to: 160 },
    callouts: [
      { text: "Choose apps on this Mac.", from: 8, to: 92 },
      { text: "On this Mac only.", from: 96, to: 174 },
    ],
    copy: {},
  },
  iphoneAppsScene(522, 672),
  {
    id: "duration",
    start: 666,
    end: 876,
    kind: "capture",
    layout: "macSolo",
    mac: { captures: ["mac-session-inactive.png", "mac-duration.png", "mac-duration-45.png", "mac-review-45.png"] },
    actions: [
      { device: "mac", kind: "click", target: MAC_TARGETS.startSession, at: 30, label: "Start a session", swapTo: 1 },
      { device: "mac", kind: "click", target: MAC_TARGETS.fortyFive, at: 96, label: "45 min", swapTo: 2 },
      { device: "mac", kind: "click", target: MAC_TARGETS.reviewSession, at: 156, label: "Review session", swapTo: 3 },
    ],
    callouts: [
      { text: "Room for what matters.", from: 6, to: 92 },
      { text: "5 minutes to 24 hours.", from: 94, to: 160 },
      { text: "One last look.", from: 164, to: 204 },
    ],
    copy: {},
  },
  startScene(870, 1020),
  {
    id: "end-early",
    start: 1014,
    end: 1194,
    kind: "capture",
    layout: "macWithPhone",
    mac: { captures: ["mac-active-45.png", "mac-end-confirm.png", "mac-session-inactive.png"] },
    iphone: { captures: ["iphone-active-45.png"] },
    actions: [
      { device: "mac", kind: "click", target: MAC_TARGETS.endSessionEarly, at: 30, label: "End session early", swapTo: 1 },
      { device: "mac", kind: "click", target: MAC_TARGETS.endSessionConfirm, at: 96, label: "End session", swapTo: 2 },
    ],
    pointerExit: { from: 140, to: 160 },
    callouts: [
      { text: "Ready to return?", from: 8, to: 92 },
      { text: "You can always end early.", from: 94, to: 174 },
    ],
    copy: {},
  },
  { id: "close", start: 1188, end: 1260, kind: "title", fadeOutAt: 56, copy: CLOSE_COPY },
];

export const STILLS = {
  StepWebsites: { mac: "mac-websites.png", iphone: "iphone-websites.png" },
  StepDuration: { mac: "mac-duration-45.png", iphone: "iphone-duration-45.png" },
} as const;

export const SOCIAL_PREVIEW = {
  width: 1280,
  height: 640,
  iphone: "iphone-active-45.png",
  copy: {
    eyebrow: OPEN_COPY.eyebrow,
    headline: OPEN_COPY.headline,
    support: OPEN_COPY.support,
    footer: CLOSE_COPY.footer,
  },
} as const;

export function totalFrames(scenes: readonly StoryScene[]): number {
  return scenes[scenes.length - 1]?.end ?? 0;
}

export function sceneDuration(scene: StoryScene): number {
  return scene.end - scene.start;
}

export function copyString(scene: StoryScene, key: string): string {
  const value = scene.copy[key];
  if (typeof value !== "string" || value.length === 0) {
    throw new Error(`Scene ${scene.id} has no copy ${key}`);
  }
  return value;
}

/** Frames at which something visibly changes, used to forbid dead stretches. */
export function sceneEvents(scene: StoryScene): number[] {
  const events = new Set<number>([0, sceneDuration(scene)]);
  for (const action of scene.actions ?? []) {
    const lead = action.kind === "click" ? (action.travel ?? 24) + 6 : 10;
    events.add(action.at - lead);
    events.add(action.at);
    events.add(action.at + SWAP_FRAMES);
  }
  for (const callout of scene.callouts ?? []) {
    events.add(callout.from);
    events.add(callout.to);
  }
  if (scene.pointerExit) {
    events.add(scene.pointerExit.from);
    events.add(scene.pointerExit.to);
  }
  if (scene.layoutTo) {
    events.add(scene.layoutTo.from);
    events.add(scene.layoutTo.to);
  }
  if (scene.iphone?.enterAt !== undefined) {
    events.add(scene.iphone.enterAt);
    events.add(scene.iphone.enterAt + 20);
  }
  if (scene.fadeOutAt !== undefined) {
    events.add(scene.fadeOutAt);
  }
  return [...events].sort((a, b) => a - b);
}

export function sceneAssets(scene: StoryScene): string[] {
  return [...(scene.mac?.captures ?? []), ...(scene.iphone?.captures ?? [])];
}
