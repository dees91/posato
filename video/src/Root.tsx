import { Composition, Still } from "remotion";
import { SocialPreview } from "./SocialPreview";
import { StepStill } from "./StepStill";
import { Hero, Walkthrough } from "./Story";
import { HERO, SOCIAL_PREVIEW, STILLS, totalFrames, VIDEO, WALKTHROUGH } from "./storyboard";

export const RemotionRoot = () => (
  <>
    <Composition id="Hero" component={Hero} durationInFrames={totalFrames(HERO)} fps={VIDEO.fps} width={VIDEO.width} height={VIDEO.height} />
    <Composition
      id="Walkthrough"
      component={Walkthrough}
      durationInFrames={totalFrames(WALKTHROUGH)}
      fps={VIDEO.fps}
      width={VIDEO.width}
      height={VIDEO.height}
    />
    <Still id="StepWebsites" component={StepStill} width={1920} height={1080} defaultProps={STILLS.StepWebsites} />
    <Still id="StepDuration" component={StepStill} width={1920} height={1080} defaultProps={STILLS.StepDuration} />
    <Still id="SocialPreview" component={SocialPreview} width={SOCIAL_PREVIEW.width} height={SOCIAL_PREVIEW.height} />
  </>
);
