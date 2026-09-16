import { interpolate, spring, useCurrentFrame, useVideoConfig } from "remotion";
import type { Point } from "../layout";
import { colors } from "../theme";

type PointerProps = {
  readonly from: Point;
  readonly to: Point;
  readonly moveStart: number;
  readonly moveEnd: number;
  readonly clickFrame?: number;
  readonly hideAt?: number;
  readonly label: string;
};

/** A springy arrow cursor with a click ring; coordinates are composition pixels of the arrow tip. */
export const Pointer: React.FC<PointerProps> = ({ from, to, moveStart, moveEnd, clickFrame, hideAt, label }) => {
  const frame = useCurrentFrame();
  const { fps } = useVideoConfig();
  const movement = spring({
    frame: frame - moveStart,
    fps,
    durationInFrames: Math.max(1, moveEnd - moveStart),
    config: { damping: 24, stiffness: 90 },
  });
  const x = interpolate(movement, [0, 1], [from[0], to[0]]);
  const y = interpolate(movement, [0, 1], [from[1], to[1]]);
  const clickDistance = clickFrame === undefined ? Infinity : Math.abs(frame - clickFrame);
  const ring = interpolate(clickDistance, [0, 12], [1, 0], { extrapolateLeft: "clamp", extrapolateRight: "clamp" });
  const press = clickFrame === undefined ? 1 : interpolate(frame, [clickFrame - 2, clickFrame, clickFrame + 4], [1, 0.9, 1], {
    extrapolateLeft: "clamp",
    extrapolateRight: "clamp",
  });
  const appear = interpolate(frame, [moveStart - 4, moveStart + 2], [0, 1], { extrapolateLeft: "clamp", extrapolateRight: "clamp" });
  const vanish = hideAt === undefined ? 1 : interpolate(frame, [hideAt, hideAt + 10], [1, 0], { extrapolateLeft: "clamp", extrapolateRight: "clamp" });

  return (
    <div
      aria-label={label}
      style={{
        position: "absolute",
        left: x,
        top: y,
        width: 40,
        height: 52,
        opacity: Math.min(appear, vanish),
        filter: "drop-shadow(0 6px 10px #0009)",
        transform: `scale(${press})`,
        transformOrigin: "2px 2px",
      }}
    >
      <div
        style={{
          position: "absolute",
          left: -29,
          top: -29,
          width: 62,
          height: 62,
          borderRadius: "50%",
          border: `4px solid ${colors.primary}`,
          boxShadow: `0 0 0 1px ${colors.warmWhite}66`,
          opacity: ring,
          transform: `scale(${1 + (1 - ring) * 0.8})`,
        }}
      />
      <svg viewBox="0 0 32 42" width="40" height="52" aria-hidden="true">
        <path
          d="M2 2v31l8.1-7.3 5.9 13.1 6.2-2.8-5.8-12.9H29L2 2Z"
          fill={colors.warmWhite}
          stroke={colors.ink}
          strokeWidth="2.5"
          strokeLinejoin="round"
        />
      </svg>
    </div>
  );
};
