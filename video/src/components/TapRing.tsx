import { interpolate, useCurrentFrame } from "remotion";
import type { Point } from "../layout";
import { colors } from "../theme";

type TapRingProps = { readonly at: Point; readonly tapFrame: number; readonly label: string };

/** A fingertip on the iPhone: a sage dot that appears, presses, and emits one ring. */
export const TapRing: React.FC<TapRingProps> = ({ at, tapFrame, label }) => {
  const frame = useCurrentFrame();
  if (frame < tapFrame - 10 || frame > tapFrame + 18) {
    return null;
  }
  const appear = interpolate(frame, [tapFrame - 10, tapFrame - 2], [0, 1], { extrapolateLeft: "clamp", extrapolateRight: "clamp" });
  const drift = interpolate(frame, [tapFrame - 10, tapFrame - 2], [24, 0], { extrapolateLeft: "clamp", extrapolateRight: "clamp" });
  const press = interpolate(frame, [tapFrame - 2, tapFrame + 1, tapFrame + 6], [1, 0.82, 1], { extrapolateLeft: "clamp", extrapolateRight: "clamp" });
  const leave = interpolate(frame, [tapFrame + 10, tapFrame + 18], [1, 0], { extrapolateLeft: "clamp", extrapolateRight: "clamp" });
  const ring = interpolate(frame, [tapFrame, tapFrame + 14], [0, 1], { extrapolateLeft: "clamp", extrapolateRight: "clamp" });
  return (
    <div aria-label={label} style={{ position: "absolute", left: at[0], top: at[1], opacity: Math.min(appear, leave) }}>
      <div
        style={{
          position: "absolute",
          left: -28,
          top: -28,
          width: 56,
          height: 56,
          borderRadius: "50%",
          background: `${colors.primary}99`,
          boxShadow: `0 0 0 1.5px ${colors.warmWhite}AA, 0 8px 20px #0007`,
          transform: `translate(${drift}px, ${drift}px) scale(${press})`,
        }}
      />
      {frame >= tapFrame ? (
        <div
          style={{
            position: "absolute",
            left: -28,
            top: -28,
            width: 56,
            height: 56,
            borderRadius: "50%",
            border: `3px solid ${colors.primary}`,
            opacity: 1 - ring,
            transform: `scale(${1 + ring})`,
          }}
        />
      ) : null}
    </div>
  );
};
