import { AbsoluteFill } from "remotion";
import { DeviceFrame } from "./components/DeviceFrame";
import { Wordmark } from "./components/Wordmark";
import { PLAY_FEATURE_GRAPHIC } from "./storyboard";
import { colors, fonts } from "./theme";

/** The 1024 x 500 Google Play feature graphic: the wordmark and two Android captures, with no copy to translate. */
export const PlayFeatureGraphic: React.FC = () => (
  <AbsoluteFill style={{ backgroundColor: colors.canvas, color: colors.text, fontFamily: fonts.sans }}>
    <div style={{ position: "absolute", left: 96, top: 0, bottom: 0, display: "flex", alignItems: "center" }}>
      <Wordmark size={92} />
    </div>
    {PLAY_FEATURE_GRAPHIC.phones.map((capture, index) => (
      <DeviceFrame key={capture} spec={{ device: "iphone", left: 600 + index * 190, top: 70 + index * 40, width: 180 }} captures={[capture]} opacities={[1]} />
    ))}
  </AbsoluteFill>
);
