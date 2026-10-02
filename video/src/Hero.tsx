import { AbsoluteFill, Easing, Img, interpolate, staticFile, useCurrentFrame } from "remotion";
import { SyncPrivacy } from "./SyncPrivacy";
import { Pointer } from "./components/Pointer";
import { Wordmark } from "./components/Wordmark";
import { HERO, MAC_TARGETS } from "./storyboard";
import { colors, fonts } from "./theme";

const story = HERO;
const shots = [story.choice, story.duration, story.start, story.active] as const;
const clamp = { extrapolateLeft: "clamp", extrapolateRight: "clamp" } as const;
const cropPadding = 36;
const ease = Easing.bezier(0.65, 0, 0.35, 1);
const mix = (from: number, to: number, progress: number) => from + (to - from) * progress;

/** A shared crop moves between real captures, preserving the source UI. */
const HeroFrame = ({ frame }: { readonly frame: number }) => {
  let index = 0;
  shots.forEach((shot, candidate) => { if (frame >= shot.start) index = candidate; });
  const current = shots[index];
  const previous = shots[Math.max(0, index - 1)];
  const progress = index === 0 ? 1 : interpolate(frame, [current.start, current.start + story.transitionFrames], [0, 1], { ...clamp, easing: ease });
  const pose = current.pose.map((value, key) => mix(previous.pose[key], value, progress));
  const [left, top, width, height, scale] = pose;
  const titleTop = mix(previous.title[0], current.title[0], progress);
  const titleSize = mix(previous.title[1], current.title[1], progress);
  const closing = interpolate(frame, [story.close.start, story.close.start + story.closeTransitionFrames], [0, 1], {
    ...clamp, easing: Easing.bezier(0.16, 1, 0.3, 1),
  });
  const sameHeadline = previous.headline === current.headline;
  const captionColor = index < 1 ? colors.text : colors.primary;

  return (
    <AbsoluteFill style={{ backgroundColor: colors.canvas, color: colors.text, fontFamily: fonts.sans, overflow: "hidden" }}>
      <div style={{ position: "absolute", left: 110, top: 80 }}><Wordmark size={38} /></div>
      <AbsoluteFill style={{ opacity: frame >= story.sync.start + 24 ? 0 : 1 - closing, scale: 1 - closing * 0.04 }}>
        <div style={{ position: "absolute", left: 110, top: titleTop, fontSize: titleSize, lineHeight: 1.04, fontWeight: 600, letterSpacing: "-0.04em", color: captionColor, whiteSpace: "pre", width: 1380 }}>
          {index > 0 && !sameHeadline && progress < 1 ? (
            <div style={{ position: "absolute", opacity: Math.max(0, 1 - progress * 2), translate: `0 ${-20 * progress}px` }}>{previous.headline}</div>
          ) : null}
          <div style={{ opacity: sameHeadline ? 1 : Math.max(0, progress * 2 - 1), translate: `0 ${sameHeadline ? 0 : 20 * (1 - progress)}px` }}>{current.headline}</div>
        </div>
        <div style={{ position: "absolute", left, top, width, height, overflow: "hidden", borderRadius: 14, backgroundColor: colors.surface }}>
          {[previous, current].map((shot, layer) => (
            <AbsoluteFill key={layer} style={{ inset: cropPadding, width: width - cropPadding * 2, height: height - cropPadding * 2, overflow: "hidden", clipPath: layer === 0 ? undefined : `inset(0 ${(1 - progress) * 100}% 0 0)` }}>
            <Img
              src={staticFile(shot.capture)}
              style={{
                position: "absolute",
                width: 1272 * scale,
                height: 936 * scale,
                maxWidth: "none",
                left: -shot.crop[0] * scale,
                top: -shot.crop[1] * scale,
                translate: `0 ${layer === 0 ? -18 * progress : 18 * (1 - progress)}px`,
              }}
            />
            </AbsoluteFill>
          ))}
        </div>
        {index === 2 ? (
          <Pointer
            from={[660, 730]}
            to={[left + cropPadding + (MAC_TARGETS.startThisPause[0] - current.crop[0]) * scale, top + cropPadding + (MAC_TARGETS.startThisPause[1] - current.crop[1]) * scale]}
            moveStart={story.start.start + story.transitionFrames}
            moveEnd={story.start.click - 6}
            clickFrame={story.start.click}
            hideAt={story.start.click + 2}
            label="Start this pause"
          />
        ) : null}
      </AbsoluteFill>
      <AbsoluteFill style={{ opacity: closing, translate: `0 ${32 * (1 - closing)}px` }}>
        <div style={{ position: "absolute", left: 110, top: 360, fontSize: 106, fontWeight: 600, lineHeight: 1.08, letterSpacing: "-0.04em", whiteSpace: "pre-line", width: 1020 }}>
          {story.close.headline}
        </div>
        <div style={{ position: "absolute", right: 130, top: 260, width: 270, height: 380 }}>
          <div style={{ position: "absolute", left: 10, bottom: 0, width: 78, height: 300, borderRadius: 78, backgroundColor: colors.moss, rotate: "8deg", translate: `${-closing * 12}px 0` }} />
          <div style={{ position: "absolute", right: 10, top: 0, width: 78, height: 300, borderRadius: 78, backgroundColor: colors.primary, rotate: "8deg", translate: `${closing * 12}px 0` }} />
        </div>
      </AbsoluteFill>
    </AbsoluteFill>
  );
};

export const Hero = () => {
  const frame = useCurrentFrame();
  const loop = interpolate(frame, [story.frames - story.loopTransitionFrames, story.frames - 1], [0, 1], { ...clamp, easing: ease });
  return (
    <AbsoluteFill>
      <HeroFrame frame={frame} />
      {frame >= story.sync.start && frame < story.close.start + 24 ? <SyncPrivacy frame={frame} /> : null}
      {loop > 0 ? <AbsoluteFill style={{ clipPath: `inset(0 ${(1 - loop) * 100}% 0 0)` }}><HeroFrame frame={0} /></AbsoluteFill> : null}
    </AbsoluteFill>
  );
};
