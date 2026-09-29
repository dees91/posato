"use strict";

// Loopback port of the Posato session proxy. Keep in sync with
// BoundedHTTPProxy.firefoxLoopbackPort in macosHelper.
const POSATO_PORT = 48151;
const PAUSE_PATH = "/blocked";
const PING_PATH = "/firefox-extension-ping";
const PROXY_REFUSED = "NS_ERROR_PROXY_FORBIDDEN";

function posatoUrl(path) {
  return "http://127.0.0.1:" + POSATO_PORT + path;
}

function isPosatoUrl(url) {
  return typeof url === "string" && url.indexOf("http://127.0.0.1:" + POSATO_PORT + "/") === 0;
}

// The extension never learns which sites are paused. It only reacts to the proxy's refusal,
// and only while the session listener answers, so an unrelated proxy failure is left alone.
async function posatoEnforcing() {
  try {
    const response = await fetch(posatoUrl(PAUSE_PATH), { method: "GET", cache: "no-store" });
    return response.ok;
  } catch (e) {
    return false;
  }
}

// A manual proxy that is not Posato's owns the refusal; system and PAC modes resolve
// privately, so the enforcing probe above decides for them.
async function posatoOwnsProxy() {
  try {
    const settings = await browser.proxy.settings.get({});
    if (settings.proxyType !== "manual") {
      return true;
    }
    return settings.http === "127.0.0.1" && Number(settings.httpPort) === POSATO_PORT &&
      settings.ssl === "127.0.0.1" && Number(settings.sslPort) === POSATO_PORT;
  } catch (e) {
    return false;
  }
}

async function ping() {
  try {
    await fetch(posatoUrl(PING_PATH), { method: "GET", cache: "no-store" });
  } catch (e) {
    // No session is active, or the listener is gone; nothing to record.
  }
}

browser.webRequest.onErrorOccurred.addListener(async (details) => {
  if (details.frameId !== 0 || details.tabId < 0) {
    return;
  }
  if (details.error !== PROXY_REFUSED) {
    return;
  }
  if (isPosatoUrl(details.url)) {
    return;
  }
  if (!(await posatoOwnsProxy())) {
    return;
  }
  if (!(await posatoEnforcing())) {
    return;
  }
  ping();
  try {
    await browser.tabs.update(details.tabId, { url: posatoUrl(PAUSE_PATH) });
  } catch (e) {
    // The tab went away; nothing to present.
  }
}, { urls: ["<all_urls>"] });

browser.runtime.onInstalled.addListener(() => { ping(); });
browser.runtime.onStartup.addListener(() => { ping(); });
