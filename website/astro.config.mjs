// @ts-check
import fs from "node:fs";
import { fileURLToPath } from "node:url";
import { defineConfig } from "astro/config";

// Astro turns every Markdown file under src/pages into a route. pl/PRIVACY.md is
// the source imported by pl/privacy.astro. Publishing it too would add /pl/PRIVACY/
// and, on a case-insensitive disk, overwrite /pl/privacy/.
const readDirectory = fs.readdirSync;
fs.readdirSync = function (directory, options) {
  const entries = readDirectory.call(this, directory, options);
  const directoryPath =
    typeof directory === "string" ? directory : directory instanceof URL ? fileURLToPath(directory) : "";
  if (!directoryPath.replaceAll("\\", "/").endsWith("/src/pages/pl")) {
    return entries;
  }
  return entries.filter((entry) => {
    const name = typeof entry === "string" ? entry : entry.name;
    return name !== "PRIVACY.md";
  });
};

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
