import type { CSSProperties, ReactNode } from "react";
import { AbsoluteFill, Easing, interpolate, spring, useCurrentFrame, useVideoConfig } from "remotion";
import { colors, fonts } from "../theme";

type SceneLayerProps = {
  readonly children: ReactNode;
  readonly durationInFrames: number;
  readonly exitFrames?: number;
  readonly style?: CSSProperties;
};

export const SceneLayer: React.FC<SceneLayerProps> = ({ children, durationInFrames, exitFrames = 6, style }) => {
  const frame = useCurrentFrame();
  const { fps } = useVideoConfig();
  const entrance = spring({ frame, fps, config: { damping: 20, stiffness: 115, mass: 0.85 } });
  const exit = interpolate(frame, [durationInFrames - exitFrames, durationInFrames], [1, 0], {
    extrapolateLeft: "clamp",
    extrapolateRight: "clamp",
    easing: Easing.inOut(Easing.ease),
  });
  return (
    <AbsoluteFill
      style={{
        backgroundColor: colors.canvas,
        color: colors.text,
        fontFamily: fonts.sans,
        opacity: Math.min(entrance, exit),
        overflow: "hidden",
        ...style,
      }}
    >
      <AbsoluteFill
        style={{ backgroundImage: `radial-gradient(circle at 78% 18%, ${colors.primary}0F 0, transparent 40%)` }}
      />
      {children}
    </AbsoluteFill>
  );
};
