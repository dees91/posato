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
      <div style={{ position: "absolute", left: 110, top: 150, fontSize: 90, lineHeight: 1.05, fontWeight: 600, letterSpacing: "-0.04em", whiteSpace: "pre" }}>{story.sync.headline}</div>
      {story.sync.desktops.map((desktop, index) => (
        <div key={desktop.label} style={{ position: "absolute", left: 110, top: 400 + index * 250, width: 900, height: 230, backgroundColor: desktop.background, borderRadius: 14, overflow: "hidden", translate: `${-30 * (1 - enter) * (index + 1)}px 0` }}>
          <div style={{ position: "absolute", left: 36, top: 8, fontSize: 20, color: desktop.ink }}>{desktop.label}</div>
          <div style={{ position: "absolute", inset: 36, overflow: "hidden" }}>
            <Img src={staticFile(desktop.capture)} style={{ position: "absolute", width: 1272 * desktop.scale, maxWidth: "none", left: -desktop.crop[0] * desktop.scale, top: -desktop.crop[1] * desktop.scale }} />
          </div>
        </div>
      ))}
      {story.sync.phones.map((phone, index) => (
        <div key={phone.label}>
          <div style={{ position: "absolute", left: 1095 + index * 240, top: 170 + index * 40, fontSize: 24, color: colors.muted }}>{phone.label}</div>
          <DeviceFrame spec={{ device: "iphone", left: 1085 + index * 240, top: 210 + index * 40, width: 215 }} captures={[phone.capture]} opacities={[1]} translateX={30 * (1 - enter) * (index + 1)} />
        </div>
      ))}
      <div style={{ position: "absolute", left: 110, top: 912, fontSize: 32, color: colors.primary }}>{story.sync.line}</div>
      <AbsoluteFill style={{ backgroundColor: colors.text, color: colors.ink, clipPath: `inset(0 0 ${(1 - privacy) * 100}% 0)` }}>
        <div style={{ position: "absolute", left: 110, top: 80 }}><Wordmark size={38} color={colors.ink} /></div>
        <div style={{ position: "absolute", left: 110, top: 250, fontSize: 116, fontWeight: 600, lineHeight: 1.04, letterSpacing: "-0.04em", whiteSpace: "pre" }}>{story.privacy.headline}</div>
        <div style={{ position: "absolute", left: 115, top: 570, fontSize: 38, lineHeight: 1.45, whiteSpace: "pre" }}>{story.privacy.lines.join("\n")}</div>
        <div style={{ position: "absolute", left: 115, top: 840, fontSize: 30 }}>No Posato account. No Posato server.</div>
      </AbsoluteFill>
    </AbsoluteFill>
  );
};
