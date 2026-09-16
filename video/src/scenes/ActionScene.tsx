import { AbsoluteFill, Easing, interpolate, useCurrentFrame } from "remotion";
import { DeviceFrame } from "../components/DeviceFrame";
import { Pointer } from "../components/Pointer";
import { SceneLayer } from "../components/SceneLayer";
import { TapRing } from "../components/TapRing";
import { Callout } from "../components/Typography";
import { capturePoint, LAYOUTS, lerpLayout, type Device, type Layout, type Point } from "../layout";
import { sceneDuration, SWAP_FRAMES, type Action, type DeviceTrack, type StoryScene } from "../storyboard";

const POINTER_ENTRY: Point = [1560, 1040];
const ZOOM_STEP = 0.02;
const ZOOM_MAX = 1.06;
const clamp = { extrapolateLeft: "clamp", extrapolateRight: "clamp" } as const;
const ease = Easing.inOut(Easing.ease);

const clickLead = (action: Action) => (action.kind === "click" ? (action.travel ?? 24) + 6 : 10);

type Move = {
  readonly to: Point;
  readonly moveStart: number;
  readonly moveEnd: number;
  readonly clickFrame?: number;
  readonly hideAt?: number;
  readonly label: string;
};

function captureOpacities(track: DeviceTrack, device: Device, actions: readonly Action[], frame: number): number[] {
  const swaps = actions.filter((action) => action.device === device && action.swapTo !== undefined);
  let position = 0;
  let previous = 0;
  for (const swap of swaps) {
    const progress = interpolate(frame, [swap.at, swap.at + SWAP_FRAMES], [0, 1], clamp);
    position = previous + ((swap.swapTo ?? previous) - previous) * progress;
    if (frame >= swap.at + SWAP_FRAMES) {
      previous = swap.swapTo ?? previous;
    }
  }
  return track.captures.map((_, index) => Math.max(0, 1 - Math.abs(position - index)));
}

function currentLayout(scene: StoryScene, frame: number): Layout {
  const base = LAYOUTS[scene.layout ?? "macSolo"];
  if (!scene.layoutTo) {
    return base;
  }
  const t = interpolate(frame, [scene.layoutTo.from, scene.layoutTo.to], [0, 1], { ...clamp, easing: ease });
  return lerpLayout(base, LAYOUTS[scene.layoutTo.layout], t);
}

function target(layout: Layout, action: Action): Point {
  const spec = layout[action.device];
  if (!spec) {
    throw new Error(`Action ${action.label} targets a device absent from the layout`);
  }
  return capturePoint(spec, action.target);
}

export const ActionScene: React.FC<{ readonly scene: StoryScene }> = ({ scene }) => {
  const frame = useCurrentFrame();
  const duration = sceneDuration(scene);
  const actions = scene.actions ?? [];
  const layout = currentLayout(scene, frame);
  const baseLayout = LAYOUTS[scene.layout ?? "macSolo"];

  const zoomSegments = actions.map((action, index) => ({
    start: action.at - clickLead(action),
    end: action.at + 10,
    from: Math.min(ZOOM_MAX, 1 + ZOOM_STEP * index),
    to: Math.min(ZOOM_MAX, 1 + ZOOM_STEP * (index + 1)),
  }));
  const zoom = zoomSegments.reduce(
    (value, segment) => value + (segment.to - segment.from) * interpolate(frame, [segment.start, segment.end], [0, 1], { ...clamp, easing: ease }),
    1,
  );
  const targets = actions.map((action) => target(baseLayout, action));
  const origin: Point = targets.length
    ? [targets.reduce((sum, point) => sum + point[0], 0) / targets.length, targets.reduce((sum, point) => sum + point[1], 0) / targets.length]
    : [800, 500];

  const clicks = actions.filter((action) => action.kind === "click");
  const moves: Move[] = clicks.map((action) => ({
    to: target(layout, action),
    moveStart: action.at - clickLead(action),
    moveEnd: action.at - 6,
    clickFrame: action.at,
    label: action.label,
  }));
  if (scene.pointerExit) {
    moves.push({ to: POINTER_ENTRY, moveStart: scene.pointerExit.from, moveEnd: scene.pointerExit.to, hideAt: scene.pointerExit.to - 6, label: "Pointer leaves" });
  }
  let moveIndex = 0;
  moves.forEach((move, index) => {
    if (frame >= move.moveStart) {
      moveIndex = index;
    }
  });
  const move = moves[moveIndex];
  const pointerFrom = moveIndex === 0 ? scene.pointerEntry ?? POINTER_ENTRY : moves[moveIndex - 1].to;

  const iphoneEnter = scene.iphone?.enterAt;
  const iphoneOpacity = iphoneEnter === undefined ? 1 : interpolate(frame, [iphoneEnter, iphoneEnter + 20], [0, 1], clamp);
  const iphoneShift = iphoneEnter === undefined ? 0 : interpolate(frame, [iphoneEnter, iphoneEnter + 20], [260, 0], { ...clamp, easing: Easing.out(Easing.cubic) });

  return (
    <SceneLayer durationInFrames={duration}>
      <AbsoluteFill style={{ transform: `scale(${zoom})`, transformOrigin: `${origin[0]}px ${origin[1]}px` }}>
        {scene.mac && layout.mac ? (
          <DeviceFrame spec={layout.mac} captures={scene.mac.captures} opacities={captureOpacities(scene.mac, "mac", actions, frame)} />
        ) : null}
        {scene.iphone && layout.iphone ? (
          <DeviceFrame
            spec={layout.iphone}
            captures={scene.iphone.captures}
            opacities={captureOpacities(scene.iphone, "iphone", actions, frame)}
            opacity={iphoneOpacity}
            translateX={iphoneShift}
          />
        ) : null}
        {actions
          .filter((action) => action.kind === "tap")
          .map((action) => (
            <TapRing key={action.label + action.at} at={target(layout, action)} tapFrame={action.at} label={action.label} />
          ))}
        {move ? (
          <Pointer
            from={pointerFrom}
            to={move.to}
            moveStart={move.moveStart}
            moveEnd={move.moveEnd}
            clickFrame={move.clickFrame}
            hideAt={move.hideAt}
            label={move.label}
          />
        ) : null}
      </AbsoluteFill>
      {(scene.callouts ?? []).map((callout) => (
        <Callout key={callout.text + callout.from} from={callout.from} to={callout.to}>
          {callout.text}
        </Callout>
      ))}
    </SceneLayer>
  );
};
