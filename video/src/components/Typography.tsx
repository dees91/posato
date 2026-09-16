import type { ReactNode } from "react";
import { interpolate, spring, useCurrentFrame, useVideoConfig } from "remotion";
import { colors } from "../theme";

const rise = (frame: number, fps: number, delay: number, config: { damping: number; stiffness: number; mass?: number }) =>
  spring({ frame: frame - delay, fps, config });

export const Eyebrow: React.FC<{ readonly children: ReactNode; readonly delay?: number }> = ({ children, delay = 0 }) => {
  const frame = useCurrentFrame();
  const { fps } = useVideoConfig();
  const progress = rise(frame, fps, delay, { damping: 18, stiffness: 130 });
  return (
    <div
      style={{
        color: colors.primary,
        fontSize: 22,
        fontWeight: 600,
        letterSpacing: 3,
        textTransform: "uppercase",
        opacity: progress,
        transform: `translateY(${(1 - progress) * 14}px)`,
      }}
    >
      {children}
    </div>
  );
};

export const Headline: React.FC<{ readonly children: ReactNode; readonly delay?: number; readonly size?: number }> = ({
  children,
  delay = 4,
  size = 84,
}) => {
  const frame = useCurrentFrame();
  const { fps } = useVideoConfig();
  const progress = rise(frame, fps, delay, { damping: 17, stiffness: 105, mass: 0.9 });
  return (
    <div
      style={{
        fontSize: size,
        lineHeight: 1.08,
        fontWeight: 500,
        letterSpacing: -size * 0.042,
        opacity: progress,
        transform: `translateY(${(1 - progress) * 26}px)`,
      }}
    >
      {children}
    </div>
  );
};

export const Support: React.FC<{ readonly children: ReactNode; readonly delay?: number; readonly size?: number }> = ({
  children,
  delay = 8,
  size = 30,
}) => {
  const frame = useCurrentFrame();
  const { fps } = useVideoConfig();
  const progress = rise(frame, fps, delay, { damping: 18, stiffness: 95 });
  return (
    <div
      style={{
        color: colors.muted,
        fontSize: size,
        lineHeight: 1.4,
        fontWeight: 400,
        opacity: progress,
        transform: `translateY(${(1 - progress) * 18}px)`,
      }}
    >
      {children}
    </div>
  );
};

/** A pill naming the action on screen; fades in over 8 frames and out over the last 6. */
export const Callout: React.FC<{ readonly children: ReactNode; readonly from: number; readonly to: number }> = ({
  children,
  from,
  to,
}) => {
  const frame = useCurrentFrame();
  if (frame < from || frame >= to) {
    return null;
  }
  const opacity = Math.min(
    interpolate(frame, [from, from + 8], [0, 1], { extrapolateLeft: "clamp", extrapolateRight: "clamp" }),
    interpolate(frame, [to - 6, to], [1, 0], { extrapolateLeft: "clamp", extrapolateRight: "clamp" }),
  );
  const scale = interpolate(frame, [from, from + 10], [0.94, 1], { extrapolateLeft: "clamp", extrapolateRight: "clamp" });
  return (
    <div
      style={{
        position: "absolute",
        left: 64,
        bottom: 44,
        display: "inline-flex",
        alignItems: "center",
        gap: 14,
        padding: "16px 26px",
        border: `1px solid ${colors.outline}`,
        borderRadius: 999,
        background: `${colors.surface}EE`,
        boxShadow: "0 18px 60px #00000066",
        fontSize: 30,
        fontWeight: 500,
        color: colors.text,
        opacity,
        transform: `scale(${scale})`,
        transformOrigin: "left bottom",
      }}
    >
      <span style={{ width: 12, height: 12, borderRadius: "50%", background: colors.primary, boxShadow: `0 0 18px ${colors.primary}` }} />
      {children}
    </div>
  );
};
