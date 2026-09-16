import { AbsoluteFill } from "remotion";
import { DeviceFrame } from "./components/DeviceFrame";
import { LAYOUTS } from "./layout";

export type StepStillProps = { readonly mac: string; readonly iphone: string };

/** Mac and iPhone captures side by side in the shared bezels, on a transparent background. */
export const StepStill: React.FC<StepStillProps> = ({ mac, iphone }) => (
  <AbsoluteFill style={{ backgroundColor: "transparent" }}>
    <DeviceFrame spec={LAYOUTS.stepStill.mac} captures={[mac]} opacities={[1]} />
    <DeviceFrame spec={LAYOUTS.stepStill.iphone} captures={[iphone]} opacities={[1]} />
  </AbsoluteFill>
);
