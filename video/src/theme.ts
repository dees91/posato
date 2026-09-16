// Dark-appearance tokens from DESIGN.md ("Semantic palette"). The bezel color
// is the website's `--bezel` from website/src/styles/site.css, not a DESIGN.md
// token, so that the video frames match the site frames exactly.
export const colors = {
  canvas: "#141B17",
  surface: "#1C2520",
  surfaceContainer: "#232E26",
  text: "#F2F2E9",
  muted: "#AFB9AE",
  primary: "#A7C3A2",
  primaryContainer: "#334436",
  outline: "#6D816E",
  outlineVariant: "#39453B",
  moss: "#2E5D50",
  warmWhite: "#FFFEFA",
  ink: "#18231F",
  bezel: "#0F1411",
} as const;

export const fonts = {
  sans: '-apple-system, BlinkMacSystemFont, "SF Pro Display", "SF Pro Text", system-ui, sans-serif',
} as const;

export const wallpaper = `linear-gradient(160deg, ${colors.primary}, ${colors.moss})`;
