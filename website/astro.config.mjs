// @ts-check
import { fileURLToPath } from "node:url";
import { defineConfig } from "astro/config";

// PRIVACY.md, the screenshots, and the icon are read from the repository
// root, never copied into this directory.
const repositoryRoot = fileURLToPath(new URL("..", import.meta.url));

export default defineConfig({
  site: "https://posato.app",
  trailingSlash: "always",
  build: {
    format: "directory",
    // External stylesheets keep the Content-Security-Policy free of inline styles.
    inlineStylesheets: "never",
  },
  // The privacy policy renders PRIVACY.md exactly, without typographic substitutions.
  markdown: { smartypants: false },
  devToolbar: { enabled: false },
  // Collapse whitespace as HTML does. Astro's default ("jsx") drops the line
  // break between text and a link on the next source line, which rendered
  // "See theprivacy policy" (RELEASE-002 and RELEASE-003).
  compressHTML: true,
  vite: {
    // Emit the favicon as a file; a data: URL would need img-src data: in the policy.
    build: { assetsInlineLimit: 0 },
    server: { fs: { allow: [repositoryRoot] } },
  },
});
