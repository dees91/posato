import {
  AbsoluteFill,
  CanvasImage,
  interpolate,
  staticFile,
  useCurrentFrame,
} from "remotion";

type CaptureSceneProps = {
  title: string;
  caption: string;
  mac: string;
  iphone?: string;
};

export const CaptureScene: React.FC<CaptureSceneProps> = ({
  title,
  caption,
  mac,
  iphone,
}) => {
  const frame = useCurrentFrame();
  return (
    <AbsoluteFill
      style={{
        backgroundColor: "#141B17",
        color: "#F2F2E9",
        fontFamily: "Arial, sans-serif",
      }}
    >
      <div
        style={{
          position: "absolute",
          left: 64,
          top: 38,
          fontSize: 64,
          fontWeight: 500,
          letterSpacing: -2,
        }}
      >
        {title}
      </div>
      <div
        style={{
          position: "absolute",
          left: 66,
          top: 119,
          fontSize: 28,
          color: "#AFB9AE",
        }}
      >
        {caption}
      </div>
      <div
        style={{
          position: "absolute",
          left: iphone ? 48 : 284,
          top: 190,
          width: iphone ? 1120 : 1032,
          height: 760,

          translate: interpolate(frame, [0, 14], ["0px 12px", "0px 0px"], {
            extrapolateRight: "clamp",
          }),
        }}
      >
        <CanvasImage
          src={staticFile(mac)}
          style={{
            width: "100%",
            height: "100%",
            objectFit: "contain",
            objectPosition: "left top",
          }}
        />
      </div>
      {iphone ? (
        <div
          style={{
            position: "absolute",
            right: 56,
            top: 191,
            width: 316,
            height: 688,
          }}
        >
          <CanvasImage
            src={staticFile(iphone)}
            style={{
              width: "100%",
              height: "100%",
              objectFit: "contain",
              borderRadius: 24,
            }}
          />
        </div>
      ) : null}
    </AbsoluteFill>
  );
};
