const assert = require("node:assert/strict");
const { readFileSync } = require("node:fs");
const { join } = require("node:path");
const { test } = require("node:test");
const vm = require("node:vm");

// Exercise the prototype's actual pure model without a browser or dependencies.
const html = readFileSync(join(__dirname, "index.html"), "utf8");
const script = html.match(/<script>([\s\S]*?)<\/script>/)[1];
const modelSource = script.split("// UI adapter:")[0];
const context = vm.createContext({ URL });
vm.runInContext(modelSource, context);
const { Actions, reducer, readyState, activeState, initialState, scenarios } = vm.runInContext(
  "({ Actions, reducer, readyState, activeState, initialState, scenarios })",
  context
);
const setup = () => reducer(readyState(), Actions.OPEN_SESSION_SETUP);

test("the initial 25-minute selection resolves before review and start", () => {
  const reviewing = reducer(setup(), Actions.REVIEW_SESSION);
  const active = reducer(reviewing, Actions.START_SESSION);
  assert.equal(active.session.durationMinutes, 25);
  assert.equal(active.session.endsAt, "18:10");
  assert.equal(active.session.status, "active");
});

for (const [minutes, endsAt] of [[5, "17:50"], [25, "18:10"], [45, "18:30"], [60, "18:45"], [90, "19:15"], [375, "00:00 tomorrow"], [1440, "17:45 tomorrow"]]) {
  test(`${minutes} minutes has the same end in review, active state and early-end cancellation`, () => {
    const selected = reducer(setup(), { type: Actions.SET_DURATION, minutes });
    const reviewing = reducer(selected, Actions.REVIEW_SESSION);
    const active = reducer(reviewing, Actions.START_SESSION);
    const cancelled = reducer(reducer(active, Actions.REQUEST_EARLY_END), Actions.CANCEL_EARLY_END);
    assert.equal(cancelled.session.durationMinutes, minutes);
    assert.equal(cancelled.session.endsAt, endsAt);
    assert.equal(cancelled.lastOutcome.message, `The session remains active until ${endsAt}.`);
  });
}

for (const minutes of [0, 4, 1441, 25.5, NaN, Infinity, "invalid"]) {
  test(`invalid duration ${minutes} preserves the last valid choice`, () => {
    const before = setup();
    const after = reducer(before, { type: Actions.SET_DURATION, minutes });
    assert.equal(after.lastOutcome.tone, "blocked");
    assert.deepEqual(after.session, before.session);
  });
}

test("changing duration cannot change an active session", () => {
  const before = activeState();
  const after = reducer(before, { type: Actions.SET_DURATION, minutes: 60 });
  assert.equal(after.lastOutcome.tone, "blocked");
  assert.deepEqual(after.session, before.session);
});

test("returning to overview does not start a session or end an active one", () => {
  const cancelled = reducer(setup(), Actions.RETURN_TO_SESSION);
  assert.equal(cancelled.surface, "home");
  assert.equal(cancelled.session.status, "inactive");
  const active = reducer(activeState(), Actions.RETURN_TO_SESSION);
  assert.equal(active.surface, "active");
  assert.equal(active.session.status, "active");
  assert.equal(reducer(initialState(), Actions.RETURN_TO_SESSION).lastOutcome.tone, "blocked");
});

test("returning from review preserves the selected duration", () => {
  const selected = reducer(setup(), { type: Actions.SET_DURATION, minutes: 90 });
  const reviewing = reducer(selected, Actions.REVIEW_SESSION);
  const back = reducer(reviewing, Actions.OPEN_SESSION_SETUP);
  assert.equal(back.surface, "session-setup");
  assert.equal(back.session.durationMinutes, 90);
  assert.equal(back.session.endsAt, "19:15");
});

test("a missing local mapping can be repaired from the active session overview", () => {
  const missing = reducer(activeState(), Actions.REMOVE_MAPPING);
  const overview = reducer(missing, Actions.RETURN_TO_SESSION);
  const picker = reducer(overview, Actions.OPEN_APP_PICKER);
  assert.equal(picker.surface, "app-picker");
  // Use the fixture identifier declared by the prototype, not a real application ID.
  const fixtureId = vm.runInContext("syntheticApplications.macos[0].id", context);
  const repaired = reducer(picker, { type: Actions.SAVE_APP_MAPPING, appIds: [fixtureId] });
  assert.equal(repaired.surface, "active");
  assert.equal(repaired.session.status, "active");
  assert.equal(repaired.session.endsAt, "18:30");
  assert.equal(repaired.policy.localMappings.macos.length, 1);
});

test("an active session with a valid mapping cannot open the general app picker", () => {
  const after = reducer(activeState(), Actions.OPEN_APP_PICKER);
  assert.equal(after.surface, "active");
  assert.equal(after.lastOutcome.tone, "blocked");
});

for (const scenario of scenarios) {
  test(`guided scenario remains coherent: ${scenario.id}`, () => {
    let state = scenario.start();
    const intentionalBlocks = scenario.id === "second-device"
      ? [Actions.FINISH_ONBOARDING]
      : scenario.id === "action-required" ? [Actions.START_SESSION] : [];
    for (const [action] of scenario.steps) {
      state = reducer(state, action);
      if (state.lastOutcome.tone === "blocked") {
        assert.ok(intentionalBlocks.includes(action), `${action}: ${state.lastOutcome.message}`);
      }
    }
    assert.notEqual(state.lastOutcome.tone, "blocked");
  });
}
