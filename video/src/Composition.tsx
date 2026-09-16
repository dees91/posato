import { ActivePause } from "./scenes/ActivePause";
import { EndPause } from "./scenes/EndPause";
import { TransitionSeries } from "@remotion/transitions";
import { ChooseItems } from "./scenes/ChooseItems";
import { ChooseApps } from "./scenes/ChooseApps";
import { ChooseDuration } from "./scenes/ChooseDuration";
import { ReviewPause } from "./scenes/ReviewPause";

export const Hero = () => (
  <TransitionSeries>
    <TransitionSeries.Sequence durationInFrames={120}>
      <ChooseItems />
    </TransitionSeries.Sequence>
    <TransitionSeries.Sequence durationInFrames={120}>
      <ChooseApps />
    </TransitionSeries.Sequence>
    <TransitionSeries.Sequence durationInFrames={120}>
      <ChooseDuration />
    </TransitionSeries.Sequence>
    <TransitionSeries.Sequence durationInFrames={120}>
      <ActivePause />
    </TransitionSeries.Sequence>
    <TransitionSeries.Sequence durationInFrames={240}>
      <EndPause trimBefore={90} />
    </TransitionSeries.Sequence>
  </TransitionSeries>
);

export const Walkthrough = () => (
  <TransitionSeries>
    <TransitionSeries.Sequence durationInFrames={240}>
      <ChooseItems />
    </TransitionSeries.Sequence>
    <TransitionSeries.Sequence durationInFrames={210}>
      <ChooseApps />
    </TransitionSeries.Sequence>
    <TransitionSeries.Sequence durationInFrames={240}>
      <ChooseDuration />
    </TransitionSeries.Sequence>
    <TransitionSeries.Sequence durationInFrames={180}>
      <ReviewPause />
    </TransitionSeries.Sequence>
    <TransitionSeries.Sequence durationInFrames={150}>
      <ActivePause />
    </TransitionSeries.Sequence>
    <TransitionSeries.Sequence durationInFrames={390}>
      <EndPause />
    </TransitionSeries.Sequence>
  </TransitionSeries>
);
