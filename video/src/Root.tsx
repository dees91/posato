import "./index.css";
import { Composition } from "remotion";
import { Hero, Walkthrough } from "./Composition";

export const RemotionRoot = () => (
  <>
    <Composition
      id="Hero"
      component={Hero}
      durationInFrames={720}
      fps={30}
      width={1600}
      height={1000}
    />
    <Composition
      id="Walkthrough"
      component={Walkthrough}
      durationInFrames={1410}
      fps={30}
      width={1600}
      height={1000}
    />
  </>
);
