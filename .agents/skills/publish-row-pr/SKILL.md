---
name: publish-row-pr
description: "Publish a finished Posato roadmap row or retro change as a pull request: push the branch, write the body from the visual-pr template, attach screenshots through a secret gist, post the Verified evidence comment, set the release milestone, pass posato-control pr-evidence, hand the pull request to babysit-pr, and update the GitHub Projects mirror with roadmap-mirror. Use when work in a Posato worktree is committed, verified, and ready to be opened or marked ready."
---

# Publish a row's pull request

Run each step in the row's worktree, in order. `PC` is
`tools/posato-control/build/install/posato-control/bin/posato-control` and
`RM` is `tools/roadmap-mirror/build/install/roadmap-mirror/bin/roadmap-mirror`
(`./gradlew :posato-control:installDist :roadmap-mirror:installDist`).

1. **Ready to publish.** Every change is committed, `./gradlew quality` passed
   on the tip, and the verification runs from
   [verify-posato](../verify-posato/SKILL.md#evidence) were made at that tip,
   or at a commit with the same product tree. When the maintainer's PR agent
   `babysit-pr` is installed, run `babysit-pr adopt --branch` first: with
   pre-review it reviews the branch and opens the pull request itself, so
   stop here and report its answer, and post the evidence (step 6) on the
   pull request it opens; when it says pre-review is off, go on.
2. **Push.** `git push -u origin <branch>`; after a rebase,
   `git push --force-with-lease`. No commit carries a `Claude-Session`
   trailer.
3. **Body.** When the visual-pr skill is installed, write the body from its
   template (`~/.claude/skills/visual-pr/references/pr_description_template.md`;
   reference it, do not copy it into the repository). Otherwise use the same
   shape: one sentence for why, one to three notes for the reviewer, then the
   smallest outline that explains the change. Keep the draft in the ignored
   `.humanlayer/tasks/`. Name the roadmap row in the title, as
   `<ROW-ID>: <outcome>`.
4. **Screenshots.** Only when the change is visible. Put the PNGs in a secret
   gist, never in the repository: `gh gist create --secret` with a short text
   file, `git clone` the gist, add the images, push, and link
   `https://gist.githubusercontent.com/<owner>/<gist>/raw/<file>.png` from the
   body or the evidence comment. Show synthetic data only.
5. **Open.** `gh pr create --draft --title <title> --body-file <body>
   --milestone <version>` (the release's milestone, such as `1.6.0`; a
   backlog or retro change takes the current release's milestone or none).
6. **Evidence.** One comment with one line per target, each at the start of a
   line: `Verified <sha> on <target>: <scenario or command> -> <result>, run
   <run-id>` (`gh pr comment <n> --body-file <file>`). A documentation-only
   change needs none.
7. **Check.** `$PC pr-evidence --pr <n>` must pass. If it names product
   paths that differ from the head, verify the head again and post a new
   line; do not continue until it passes. Then `gh pr ready <n>`.
8. **Hand over.** `babysit-pr adopt <PR URL>` when the maintainer's PR agent
   is installed. After that, do not address review comments or merge.
9. **Mirror.** `$RM set-status <ROW-ID> in-progress --pr <n>`; a row that
   has no item yet gets one with `$RM add ... --status in-progress --pr <n>`
   (the roadmap's "GitHub Projects mirror"). A retro change without a row
   skips this step.

Report the pull request URL, the `pr-evidence` result, and the adopt result.
