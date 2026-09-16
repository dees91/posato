import { Img, staticFile } from "remotion";
import { frameMetrics, macWindowRadius, type FrameSpec } from "../layout";
import { colors, wallpaper } from "../theme";

type DeviceFrameProps = {
  readonly spec: FrameSpec;
  readonly captures: readonly string[];
  readonly opacities: readonly number[];
  readonly opacity?: number;
  readonly translateX?: number;
};

/** A generic bezel around stacked captures; the bezel geometry matches the website. */
export const DeviceFrame: React.FC<DeviceFrameProps> = ({ spec, captures, opacities, opacity = 1, translateX = 0 }) => {
  const metrics = frameMetrics(spec);
  const isMac = spec.device === "mac";
  return (
    <div
      style={{
        position: "absolute",
        left: spec.left,
        top: spec.top,
        width: spec.width,
        height: metrics.height,
        boxSizing: "border-box",
        border: `${metrics.border}px solid ${colors.bezel}`,
        borderRadius: metrics.radius,
        outline: `1px solid ${colors.outline}`,
        padding: `${metrics.padY}px ${metrics.padX}px`,
        background: isMac ? wallpaper : colors.bezel,
        overflow: "hidden",
        opacity,
        transform: `translateX(${translateX}px)${isMac ? " perspective(1700px) rotateX(1deg)" : ""}`,
        transformOrigin: "center center",
      }}
    >
      <div style={{ position: "relative", width: metrics.innerWidth, height: metrics.innerHeight }}>
        {captures.map((capture, index) => (
          <Img
            key={capture + index}
            src={staticFile(capture)}
            style={{
              position: "absolute",
              inset: 0,
              width: "100%",
              height: "100%",
              objectFit: "cover",
              objectPosition: "top center",
              borderRadius: isMac ? macWindowRadius(spec) : 0,
              opacity: opacities[index] ?? 0,
            }}
          />
        ))}
      </div>
    </div>
  );
};
