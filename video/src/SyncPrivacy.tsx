import { AbsoluteFill, Easing, Img, interpolate, staticFile } from "remotion";
import { DeviceFrame } from "./components/DeviceFrame";
import { Wordmark } from "./components/Wordmark";
import { HERO } from "./storyboard";
import { colors, fonts } from "./theme";

const clamp = { extrapolateLeft: "clamp", extrapolateRight: "clamp" } as const;

/** Existing captures illustrate shared choices, without simulating delivery speed. */
export const SyncPrivacy = ({ frame }: { readonly frame: number }) => {
  const story = HERO;
  const enter = interpolate(frame, [story.sync.start, story.sync.start + 24], [0, 1], { ...clamp, easing: Easing.bezier(0.16, 1, 0.3, 1) });
  const privacy = interpolate(frame, [story.privacy.start, story.privacy.start + 24], [0, 1], { ...clamp, easing: Easing.bezier(0.65, 0, 0.35, 1) });
  const leave = interpolate(frame, [story.close.start, story.close.start + 24], [1, 0], clamp);
  return (
    <AbsoluteFill style={{ fontFamily: fonts.sans, color: colors.text, clipPath: `inset(0 ${(1 - enter) * 100}% ${(1 - leave) * 100}% 0)`, backgroundColor: colors.canvas }}>
      <div style={{ position: "absolute", left: 110, top: 80 }}><Wordmark size={38} /></div>
      <div style={{ position: "absolute", left: 110, top: 190, fontSize: 90, lineHeight: 1.05, fontWeight: 600, letterSpacing: "-0.04em", whiteSpace: "pre" }}>{story.sync.headline}</div>
      <div style={{ position: "absolute", left: 110, top: 420, fontSize: 28, color: colors.muted }}>Mac</div>
      <div style={{ position: "absolute", left: 110, top: 475, width: 900, height: 330, backgroundColor: colors.surface, borderRadius: 14, overflow: "hidden" }}>
        <div style={{ position: "absolute", inset: 36, overflow: "hidden" }}>
          <Img src={staticFile("mac-websites.png")} style={{ position: "absolute", width: 1463, maxWidth: "none", left: -377, top: -460 }} />
        </div>
      </div>
      <div style={{ position: "absolute", left: 1170, top: 170, fontSize: 28, color: colors.muted }}>iPhone</div>
      <DeviceFrame spec={{ device: "iphone", left: 1160, top: 220, width: 290 }} captures={["iphone-websites.png"]} opacities={[1]} translateX={30 * (1 - enter)} />
      <div style={{ position: "absolute", left: 110, top: 862, fontSize: 32, color: colors.primary }}>Sync websites, sessions and schedules with iCloud.</div>
      <AbsoluteFill style={{ backgroundColor: colors.text, color: colors.ink, clipPath: `inset(0 0 ${(1 - privacy) * 100}% 0)` }}>
        <div style={{ position: "absolute", left: 110, top: 80 }}><Wordmark size={38} color={colors.ink} /></div>
        <div style={{ position: "absolute", left: 110, top: 250, fontSize: 116, fontWeight: 600, lineHeight: 1.04, letterSpacing: "-0.04em", whiteSpace: "pre" }}>{story.privacy.headline}</div>
        <div style={{ position: "absolute", left: 115, top: 570, fontSize: 38, lineHeight: 1.45 }}>Encrypted on your device.<br />Stored in your private iCloud database.</div>
        <div style={{ position: "absolute", left: 115, top: 840, fontSize: 30 }}>No Posato account. No Posato server.</div>
      </AbsoluteFill>
    </AbsoluteFill>
  );
};
