import { AbsoluteFill, Sequence } from "remotion";
import { ActionScene } from "./scenes/ActionScene";
import { TitleScene } from "./scenes/TitleScene";
import { HERO, sceneDuration, WALKTHROUGH, type StoryScene } from "./storyboard";
import { colors } from "./theme";

const Story: React.FC<{ readonly scenes: readonly StoryScene[] }> = ({ scenes }) => (
  <AbsoluteFill style={{ backgroundColor: colors.canvas }}>
    {scenes.map((scene) => (
      <Sequence key={scene.id} name={scene.id} from={scene.start} durationInFrames={sceneDuration(scene)}>
        {scene.kind === "title" ? <TitleScene scene={scene} /> : <ActionScene scene={scene} />}
      </Sequence>
    ))}
  </AbsoluteFill>
);

export const Hero: React.FC = () => <Story scenes={HERO} />;
export const Walkthrough: React.FC = () => <Story scenes={WALKTHROUGH} />;
