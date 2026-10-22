import { AbsoluteFill } from "remotion";
import { DeviceFrame } from "./components/DeviceFrame";
import { LAYOUTS } from "./layout";

export type StepStillProps = { readonly desktop: string; readonly phone: string };

/** A computer and a phone capture side by side in the shared generic bezels, on a transparent background. */
export const StepStill: React.FC<StepStillProps> = ({ desktop, phone }) => (
  <AbsoluteFill style={{ backgroundColor: "transparent" }}>
    <DeviceFrame spec={LAYOUTS.stepStill.mac} captures={[desktop]} opacities={[1]} />
    <DeviceFrame spec={LAYOUTS.stepStill.iphone} captures={[phone]} opacities={[1]} />
  </AbsoluteFill>
);
