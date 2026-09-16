import { colors } from "../theme";

/** The lowercase wordmark with the CSS open-interval mark from the website. */
export const Wordmark: React.FC<{ readonly size?: number; readonly color?: string }> = ({ size = 48, color = colors.text }) => {
  const markHeight = size * 1.15;
  const markWidth = size * 0.86;
  const bar = { width: size * 0.25, height: size * 0.9, borderRadius: size, background: colors.primary, transform: "rotate(8deg)" };
  return (
    <div style={{ display: "inline-flex", alignItems: "center", gap: size * 0.32, color }}>
      <div style={{ position: "relative", width: markWidth, height: markHeight }}>
        <div style={{ position: "absolute", left: size * 0.07, bottom: 0, ...bar }} />
        <div style={{ position: "absolute", right: size * 0.07, top: 0, ...bar }} />
      </div>
      <div style={{ fontSize: size, fontWeight: 600, letterSpacing: -size * 0.02, lineHeight: 1 }}>posato</div>
    </div>
  );
};
