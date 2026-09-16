import { AbsoluteFill } from "remotion";
import { DeviceFrame } from "./components/DeviceFrame";
import { Wordmark } from "./components/Wordmark";
import { SOCIAL_PREVIEW } from "./storyboard";
import { colors, fonts } from "./theme";

/** The 1280 x 640 repository card. */
export const SocialPreview: React.FC = () => (
  <AbsoluteFill style={{ backgroundColor: colors.canvas, color: colors.text, fontFamily: fonts.sans }}>
    <div style={{ position: "absolute", left: 88, top: 0, bottom: 0, width: 760, display: "flex", flexDirection: "column", justifyContent: "center", gap: 22 }}>
      <div style={{ marginBottom: 10 }}>
        <Wordmark size={36} />
      </div>
      <div style={{ color: colors.primary, fontSize: 18, fontWeight: 600, letterSpacing: 2.5, textTransform: "uppercase" }}>{SOCIAL_PREVIEW.copy.eyebrow}</div>
      <div style={{ fontSize: 72, fontWeight: 500, lineHeight: 1.06, letterSpacing: -3 }}>{SOCIAL_PREVIEW.copy.headline}</div>
      <div style={{ color: colors.muted, fontSize: 26, lineHeight: 1.4, maxWidth: 640 }}>{SOCIAL_PREVIEW.copy.support}</div>
      <div style={{ color: colors.muted, fontSize: 20, marginTop: 8 }}>{SOCIAL_PREVIEW.copy.footer}</div>
    </div>
    <DeviceFrame spec={{ device: "iphone", left: 960, top: 78, width: 232 }} captures={[SOCIAL_PREVIEW.iphone]} opacities={[1]} />
  </AbsoluteFill>
);
