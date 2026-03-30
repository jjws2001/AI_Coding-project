import { getApiBaseUrl, request } from "./client";

interface ChatResponse {
  response: string;
}

interface BasePayload {
  projectId: number;
  sessionId: string;
}

export async function aiChat(
  payload: BasePayload & { message: string }
): Promise<string> {
  const response = await request<ChatResponse>("/api/ai/chat", {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(payload)
  });
  return response.response;
}

export async function aiCodeReview(
  payload: BasePayload & { code: string }
): Promise<string> {
  const response = await request<ChatResponse>("/api/ai/code/review", {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(payload)
  });
  return response.response;
}

export async function aiCodeExplain(
  payload: BasePayload & { code: string }
): Promise<string> {
  const response = await request<ChatResponse>("/api/ai/code/explain", {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(payload)
  });
  return response.response;
}

interface StreamHandlers {
  onChunk: (chunk: string) => void;
  signal?: AbortSignal;
}

function extractSseData(eventBlock: string): string {
  const lines = eventBlock.split(/\r?\n/);
  const dataLines = lines
    .filter((line) => line.startsWith("data:"))
    .map((line) => line.slice(5).trimStart());
  if (dataLines.length > 0) {
    return dataLines.join("\n");
  }
  return eventBlock.trim();
}

export async function aiChatStream(
  payload: BasePayload & { message: string },
  handlers: StreamHandlers
): Promise<string> {
  const response = await fetch(`${getApiBaseUrl()}/api/ai/chat/stream`, {
    method: "POST",
    credentials: "include",
    headers: {
      "Content-Type": "application/json",
      Accept: "text/event-stream"
    },
    body: JSON.stringify(payload),
    signal: handlers.signal
  });

  if (!response.ok) {
    throw new Error(await response.text());
  }
  if (!response.body) {
    throw new Error("SSE stream body is empty.");
  }

  const reader = response.body.getReader();
  const decoder = new TextDecoder("utf-8");
  let buffer = "";
  let fullText = "";

  while (true) {
    const { value, done } = await reader.read();
    if (done) {
      break;
    }

    buffer += decoder.decode(value, { stream: true }).replace(/\r\n/g, "\n");
    let separatorIndex = buffer.indexOf("\n\n");

    while (separatorIndex !== -1) {
      const eventBlock = buffer.slice(0, separatorIndex);
      buffer = buffer.slice(separatorIndex + 2);

      const eventText = extractSseData(eventBlock);
      if (eventText) {
        handlers.onChunk(eventText);
        fullText += eventText;
      }

      separatorIndex = buffer.indexOf("\n\n");
    }
  }

  buffer += decoder.decode().replace(/\r\n/g, "\n");
  const remaining = extractSseData(buffer);
  if (remaining) {
    handlers.onChunk(remaining);
    fullText += remaining;
  }

  return fullText;
}
