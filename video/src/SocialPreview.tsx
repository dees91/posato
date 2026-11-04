import { AbsoluteFill } from "remotion";
import { DeviceFrame } from "./components/DeviceFrame";
import { Wordmark } from "./components/Wordmark";
import { SOCIAL_PREVIEW } from "./storyboard";
import { DeviceGlyph } from "./SyncPrivacy";
import { colors, fonts } from "./theme";

/** The 1280 x 640 repository card: the promise, the five platforms as the hero's named outlines, and two real phone captures. */
export const SocialPreview: React.FC = () => (
  <AbsoluteFill style={{ backgroundColor: colors.canvas, color: colors.text, fontFamily: fonts.sans }}>
    <div style={{ position: "absolute", left: 88, top: 0, bottom: 0, width: 760, display: "flex", flexDirection: "column", justifyContent: "center", gap: 20 }}>
      <div style={{ marginBottom: 6 }}>
        <Wordmark size={36} />
      </div>
      <div style={{ color: colors.primary, fontSize: 17, fontWeight: 600, letterSpacing: 2.2, textTransform: "uppercase" }}>{SOCIAL_PREVIEW.copy.eyebrow}</div>
      <div style={{ fontSize: 68, fontWeight: 500, lineHeight: 1.06, letterSpacing: -3 }}>{SOCIAL_PREVIEW.copy.headline}</div>
      <div style={{ color: colors.muted, fontSize: 25, lineHeight: 1.4, maxWidth: 640 }}>{SOCIAL_PREVIEW.copy.support}</div>
      <div style={{ display: "flex", gap: 0, marginTop: 14, marginLeft: -16 }}>
        {SOCIAL_PREVIEW.platforms.map((platform) => (
          <div key={platform.label} style={{ width: 120, display: "grid", justifyItems: "center", alignContent: "end", gap: 14, height: 112, scale: "0.82", transformOrigin: "50% 100%" }}>
            <DeviceGlyph device={platform} synced={1} check={false} />
            <div style={{ fontSize: 24, color: colors.text }}>{platform.label}</div>
          </div>
        ))}
      </div>
      <div style={{ color: colors.muted, fontSize: 20 }}>{SOCIAL_PREVIEW.copy.footer}</div>
    </div>
    {SOCIAL_PREVIEW.phones.map((capture, index) => (
      <DeviceFrame key={capture} spec={{ device: "iphone", left: 880 + index * 192, top: 92 + index * 36, width: 182 }} captures={[capture]} opacities={[1]} />
    ))}
  </AbsoluteFill>
);
