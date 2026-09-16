import { Video } from "@remotion/media";
import { AbsoluteFill, staticFile } from "remotion";

export const EndPause = ({ trimBefore = 0 }: { trimBefore?: number }) => (
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
      You stay in control.
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
      End a session early. Your saved choices stay ready for next time.
    </div>
    <Video
      src={staticFile("mac-end.mp4")}
      muted
      trimBefore={trimBefore}
      objectFit="contain"
      style={{
        position: "absolute",
        left: 284,
        top: 190,
        width: 1032,
        height: 760,
      }}
    />
  </AbsoluteFill>
);
