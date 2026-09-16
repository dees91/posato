import { AbsoluteFill, Img, interpolate, spring, staticFile, useCurrentFrame, useVideoConfig } from "remotion";
import { SceneLayer } from "../components/SceneLayer";
import { Eyebrow, Headline, Support } from "../components/Typography";
import { Wordmark } from "../components/Wordmark";
import { copyString, sceneDuration, type StoryScene } from "../storyboard";
import { colors } from "../theme";

export const TitleScene: React.FC<{ readonly scene: StoryScene }> = ({ scene }) => {
  const frame = useCurrentFrame();
  const { fps } = useVideoConfig();
  const duration = sceneDuration(scene);
  const closing = scene.fadeOutAt !== undefined;
  const finalFade = closing ? interpolate(frame, [scene.fadeOutAt ?? 0, (scene.fadeOutAt ?? 0) + 12], [1, 0], { extrapolateLeft: "clamp", extrapolateRight: "clamp" }) : 1;
  const mark = spring({ frame: frame - 4, fps, config: { damping: 16, stiffness: 105, mass: 0.9 } });
  const steps = scene.copy.steps;
  const footer = scene.copy.footer;

  return (
    <SceneLayer durationInFrames={duration} wash={!closing}>
      <AbsoluteFill style={{ opacity: finalFade }}>
      <div style={{ position: "absolute", left: 120, top: 0, bottom: 0, width: 1040, display: "flex", flexDirection: "column", justifyContent: "center", gap: 26 }}>
        {closing ? null : (
          <div style={{ opacity: mark, transform: `translateY(${(1 - mark) * 12}px)`, marginBottom: 18 }}>
            <Wordmark size={44} />
          </div>
        )}
        <Eyebrow delay={6}>{copyString(scene, "eyebrow")}</Eyebrow>
        <Headline delay={10}>{copyString(scene, "headline")}</Headline>
        {scene.copy.support ? <Support delay={18}>{scene.copy.support}</Support> : null}
        {steps ? <Support delay={26} size={26}>{steps}</Support> : null}
        {footer ? <Support delay={26} size={26}>{footer}</Support> : null}
      </div>
      {closing ? (
        <Img
          src={staticFile("forest.svg")}
          style={{
            position: "absolute",
            right: 140,
            top: 330,
            width: 300,
            height: 300,
            borderRadius: 66,
            opacity: mark,
            transform: `scale(${0.84 + mark * 0.16}) rotate(${-4 * (1 - mark)}deg)`,
            filter: `drop-shadow(0 30px 70px ${colors.moss}66)`,
          }}
        />
      ) : null}
      </AbsoluteFill>
    </SceneLayer>
  );
};
