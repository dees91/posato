import { AbsoluteFill, Easing, Img, interpolate, staticFile } from "remotion";
import { Wordmark } from "./components/Wordmark";
import { HERO } from "./storyboard";
import { colors, fonts } from "./theme";

const clamp = { extrapolateLeft: "clamp", extrapolateRight: "clamp" } as const;

type Device = (typeof HERO.sync.devices)[number];

const ease = Easing.bezier(0.65, 0, 0.35, 1);
const pop = Easing.bezier(0.34, 1.56, 0.64, 1);
const cardInset = 36;
const card = { left: 820, top: 110, width: 680 };
const row = { top: 792, height: 112 };

/** A generic outline of a computer or a phone; its screen settles to the synced tint once the change reaches it. */
const DeviceGlyph = ({ device, synced }: { readonly device: Device; readonly synced: number }) => {
  const laptop = device.kind === "laptop";
  const width = laptop ? 92 : 44;
  const height = laptop ? 58 : 76;
  return (
    <div style={{ position: "relative", width, height, border: `3px solid ${colors.text}`, borderRadius: laptop ? 7 : 10, boxSizing: "border-box" }}>
      <div style={{ position: "absolute", inset: 4, borderRadius: laptop ? 3 : 6, backgroundColor: synced > 0 ? colors.primaryContainer : colors.surfaceContainer, overflow: "hidden" }}>
        {[0, 1].map((line) => (
          <div key={line} style={{ position: "absolute", left: "16%", top: `${30 + line * 28}%`, height: laptop ? 5 : 4, borderRadius: 3, width: `${(line === 0 ? 62 : 44) * synced}%`, backgroundColor: colors.primary }} />
        ))}
      </div>
      {laptop ? <div style={{ position: "absolute", left: -12, right: -12, bottom: -11, height: 5, borderRadius: 3, backgroundColor: colors.text }} /> : null}
      <div style={{ position: "absolute", right: -13, top: -13, width: 26, height: 26, borderRadius: 13, backgroundColor: colors.primary, color: colors.canvas, display: "grid", placeItems: "center", fontSize: 16, fontWeight: 800, opacity: synced, scale: `${0.4 + 0.6 * synced}` }}>✓</div>
    </div>
  );
};

/** One real capture of the Focus set shows a change; the rail carries it to every kind of device, so a new platform adds a device, not a screen. */
export const SyncPrivacy = ({ frame }: { readonly frame: number }) => {
  const story = HERO;
  const sync = story.sync;
  const local = frame - sync.start;
  const enter = interpolate(frame, [sync.start, sync.start + 24], [0, 1], { ...clamp, easing: Easing.bezier(0.16, 1, 0.3, 1) });
  const privacy = interpolate(frame, [story.privacy.start, story.privacy.start + 24], [0, 1], { ...clamp, easing: ease });
  const leave = interpolate(frame, [story.close.start, story.close.start + 24], [1, 0], clamp);
  const changed = interpolate(local, [sync.change, sync.change + 14], [0, 1], { ...clamp, easing: ease });
  const nudge = interpolate(local, [sync.change - 4, sync.change + 8, sync.change + 26], [1, 1.03, 1], { ...clamp, easing: ease });
  const light = interpolate(local, [sync.rail[0], sync.rail[1]], [0, 1], { ...clamp, easing: ease });
  const [cropLeft, cropTop, cropWidth, cropHeight] = sync.card.crop;
  const scale = (card.width - cardInset * 2) / cropWidth;
  const cardHeight = cropHeight * scale + cardInset * 2;
  const slot = card.width / sync.devices.length;
  const railTop = card.top + cardHeight + 26;
  return (
    <AbsoluteFill style={{ fontFamily: fonts.sans, color: colors.text, clipPath: `inset(0 ${(1 - enter) * 100}% ${(1 - leave) * 100}% 0)`, backgroundColor: colors.canvas }}>
      <div style={{ position: "absolute", left: 110, top: 80 }}><Wordmark size={38} /></div>
      <div style={{ position: "absolute", left: 110, top: 300, fontSize: 96, lineHeight: 1.05, fontWeight: 600, letterSpacing: "-0.04em", whiteSpace: "pre" }}>{sync.headline}</div>
      <div style={{ position: "absolute", left: 110, top: 912, fontSize: 32, color: colors.primary }}>{sync.line}</div>
      <div style={{ position: "absolute", left: card.left, top: card.top, width: card.width, height: cardHeight, borderRadius: 14, backgroundColor: colors.captureSurface, overflow: "hidden", scale: `${nudge}`, translate: `${30 * (1 - enter)}px 0` }}>
        {[sync.card.before, sync.card.after].map((capture, layer) => (
          <div key={capture} style={{ position: "absolute", inset: cardInset, overflow: "hidden", opacity: layer === 0 ? 1 : changed, maskImage: "linear-gradient(90deg, #000 82%, transparent)" }}>
            <Img src={staticFile(capture)} style={{ position: "absolute", width: 1272 * scale, maxWidth: "none", left: -cropLeft * scale, top: -cropTop * scale }} />
          </div>
        ))}
      </div>
      <div style={{ position: "absolute", left: card.left + slot / 2, width: card.width - slot, top: railTop, height: 3, borderRadius: 2, backgroundColor: colors.outlineVariant, overflow: "hidden" }}>
        <div style={{ position: "absolute", top: 0, bottom: 0, left: 0, width: `${light * 100}%`, backgroundColor: colors.primary }} />
      </div>
      {sync.devices.map((device, index) => {
        const reach = index / Math.max(1, sync.devices.length - 1);
        const at = sync.rail[0] + (sync.rail[1] - sync.rail[0]) * reach;
        const synced = interpolate(local, [at, at + 10], [0, 1], { ...clamp, easing: pop });
        return (
          <div key={device.label} style={{ position: "absolute", left: card.left + slot * index, width: slot, top: row.top, height: row.height, display: "grid", justifyItems: "center", alignContent: "end", gap: 18 }}>
            <DeviceGlyph device={device} synced={Math.min(1, synced)} />
            <div style={{ fontSize: 24, color: synced > 0.5 ? colors.text : colors.muted }}>{device.label}</div>
          </div>
        );
      })}
      <AbsoluteFill style={{ backgroundColor: colors.text, color: colors.ink, clipPath: `inset(0 0 ${(1 - privacy) * 100}% 0)` }}>
        <div style={{ position: "absolute", left: 110, top: 80 }}><Wordmark size={38} color={colors.ink} /></div>
        <div style={{ position: "absolute", left: 110, top: 250, fontSize: 116, fontWeight: 600, lineHeight: 1.04, letterSpacing: "-0.04em", whiteSpace: "pre" }}>{story.privacy.headline}</div>
        <div style={{ position: "absolute", left: 115, top: 570, fontSize: 38, lineHeight: 1.45, whiteSpace: "pre" }}>{story.privacy.lines.join("\n")}</div>
        <div style={{ position: "absolute", left: 115, top: 840, fontSize: 30 }}>No Posato account. No Posato server.</div>
      </AbsoluteFill>
    </AbsoluteFill>
  );
};
