import assert from "node:assert/strict";
import { existsSync } from "node:fs";
import { resolve } from "node:path";
import test from "node:test";
import { capturePoint, CAPTURE, frameMetrics, LAYOUTS, type Layout } from "./layout";
import {
  HERO,
  MAX_QUIET_FRAMES,
  SCENE_OVERLAP,
  sceneAssets,
  sceneDuration,
  sceneEvents,
  SOCIAL_PREVIEW,
  STILLS,
  totalFrames,
  VIDEO,
  WALKTHROUGH,
  type StoryScene,
} from "./storyboard";

const stories: ReadonlyArray<readonly [string, readonly StoryScene[], number, number]> = [
  ["Hero", HERO, 20, 24],
  ["Walkthrough", WALKTHROUGH, 35, 45],
];

const publicFile = (name: string) => resolve(process.cwd(), "public", name);

for (const [name, scenes, minSeconds, maxSeconds] of stories) {
  test(`${name} is one continuous timeline with ${SCENE_OVERLAP}-frame overlaps`, () => {
    assert.equal(scenes[0]?.start, 0);
    const total = totalFrames(scenes);
    assert.ok(total >= minSeconds * VIDEO.fps && total <= maxSeconds * VIDEO.fps, `${name} runs ${total / VIDEO.fps}s`);
    assert.equal(new Set(scenes.map((scene) => scene.id)).size, scenes.length);
    scenes.forEach((scene, index) => {
      assert.ok(Number.isInteger(scene.start) && Number.isInteger(scene.end) && scene.end > scene.start, `${scene.id} bounds`);
      if (index > 0) {
        assert.equal(scenes[index - 1].end - scene.start, SCENE_OVERLAP, `${scene.id} does not overlap by ${SCENE_OVERLAP}`);
      }
    });
    assert.equal(scenes[0]?.kind, "title");
    assert.equal(scenes[scenes.length - 1]?.kind, "title");
    assert.ok(scenes[scenes.length - 1]?.fadeOutAt !== undefined, `${name} must fade to the canvas for the loop seam`);
  });

  test(`${name} shows every action as a click or tap inside a callout window`, () => {
    for (const scene of scenes.filter((candidate) => candidate.kind === "capture")) {
      const duration = sceneDuration(scene);
      assert.ok((scene.actions ?? []).length > 0, `${scene.id} has no action`);
      assert.ok((scene.callouts ?? []).length > 0, `${scene.id} has no callout`);
      for (const action of scene.actions ?? []) {
        assert.ok(action.at > 0 && action.at < duration - SCENE_OVERLAP, `${scene.id}: ${action.label} is outside the scene`);
        assert.ok(action.label.trim().length > 0);
        const track = scene[action.device];
        assert.ok(track, `${scene.id}: ${action.label} targets a device without captures`);
        if (action.swapTo !== undefined) {
          assert.ok(action.swapTo < track.captures.length, `${scene.id}: ${action.label} swaps to a missing capture`);
        }
        const capture = CAPTURE[action.device];
        assert.ok(action.target[0] >= 0 && action.target[0] <= capture.width && action.target[1] >= 0 && action.target[1] <= capture.height, `${scene.id}: ${action.label} target is outside the capture`);
        assert.ok((scene.callouts ?? []).some((callout) => callout.from <= action.at && action.at < callout.to), `${scene.id}: ${action.label} at ${action.at} has no callout`);
        const layout: Layout = LAYOUTS[scene.layout ?? "macSolo"];
        assert.ok(layout[action.device], `${scene.id}: ${action.label} device is absent from layout ${scene.layout}`);
      }
      for (const callout of scene.callouts ?? []) {
        assert.ok(callout.text.trim().length > 0 && !/[–—]/.test(callout.text), `${scene.id}: callout copy`);
        assert.ok(callout.from >= 0 && callout.to <= duration && callout.to - callout.from >= 20, `${scene.id}: callout ${callout.text} window`);
      }
    }
  });

  test(`${name} never holds a static frame longer than ${MAX_QUIET_FRAMES} frames`, () => {
    for (const scene of scenes) {
      const events = sceneEvents(scene);
      for (let index = 1; index < events.length; index += 1) {
        assert.ok(events[index] - events[index - 1] <= MAX_QUIET_FRAMES, `${scene.id} is quiet from ${events[index - 1]} to ${events[index]}`);
      }
    }
  });

  test(`${name} copy and captures are populated and tracked`, () => {
    for (const scene of scenes) {
      for (const [key, value] of Object.entries(scene.copy)) {
        assert.ok(value.trim().length > 0 && !/[–—]/.test(value), `${scene.id}.${key}`);
      }
      for (const asset of sceneAssets(scene)) {
        assert.ok(existsSync(publicFile(asset)), `${scene.id} is missing public/${asset}`);
      }
    }
  });
}

test("stills and the social preview reference tracked captures", () => {
  for (const still of Object.values(STILLS)) {
    assert.ok(existsSync(publicFile(still.mac)), `missing ${still.mac}`);
    assert.ok(existsSync(publicFile(still.iphone)), `missing ${still.iphone}`);
  }
  assert.ok(existsSync(publicFile(SOCIAL_PREVIEW.iphone)));
  assert.ok(existsSync(publicFile("forest.svg")));
  assert.equal(SOCIAL_PREVIEW.width, 1280);
  assert.equal(SOCIAL_PREVIEW.height, 640);
});

test("layouts keep every frame inside the composition", () => {
  for (const [id, layout] of Object.entries(LAYOUTS) as ReadonlyArray<readonly [string, Layout]>) {
    const width = id === "stepStill" ? 1920 : VIDEO.width;
    const height = id === "stepStill" ? 1080 : VIDEO.height;
    for (const spec of [layout.mac, layout.iphone]) {
      if (!spec) continue;
      const metrics = frameMetrics(spec);
      assert.ok(spec.left >= 0 && spec.top >= 0, `${id} ${spec.device} origin`);
      assert.ok(spec.left + spec.width <= width && spec.top + metrics.height <= height, `${id} ${spec.device} overflows`);
      const corner = capturePoint(spec, [CAPTURE[spec.device].width, CAPTURE[spec.device].height]);
      assert.ok(corner[0] <= spec.left + spec.width && corner[1] <= spec.top + metrics.height, `${id} ${spec.device} capture mapping`);
    }
  }
});
