import { loader } from "@monaco-editor/react";
import * as monaco from "monaco-editor";

const MONACO_CDN_VERSION = "0.55.1";
const MONACO_CDN_VS = `https://cdn.jsdelivr.net/npm/monaco-editor@${MONACO_CDN_VERSION}/min/vs`;
const PROBE_TIMEOUT_MS = 4000;

async function canReachMonacoCdn(): Promise<boolean> {
  const controller = new AbortController();
  const timeoutId = window.setTimeout(() => controller.abort(), PROBE_TIMEOUT_MS);

  try {
    await fetch(`${MONACO_CDN_VS}/loader.js`, {
      method: "GET",
      mode: "no-cors",
      cache: "no-store",
      signal: controller.signal
    });
    return true;
  } catch {
    return false;
  } finally {
    window.clearTimeout(timeoutId);
  }
}

export async function setupMonacoLoader(): Promise<void> {
  const useCdn = await canReachMonacoCdn();

  if (useCdn) {
    loader.config({
      paths: {
        vs: MONACO_CDN_VS
      }
    });
    return;
  }

  // Fallback for restricted network / tracking-prevention environments.
  loader.config({ monaco });
}
