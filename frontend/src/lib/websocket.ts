import { Client } from "@stomp/stompjs";
import SockJS from "sockjs-client";
import type { CodeUpdateMessage } from "../types";
import { getApiBaseUrl } from "../api/client";

interface ProjectSyncHandlers {
  onUpdate: (message: CodeUpdateMessage) => void;
  onError?: (error: string) => void;
}

interface ProjectSyncConnection {
  sendUpdate: (message: CodeUpdateMessage) => void;
  disconnect: () => void;
}

export function connectProjectSync(
  projectId: number,
  handlers: ProjectSyncHandlers
): ProjectSyncConnection {
  const endpoint = `${getApiBaseUrl()}/ws`;
  const client = new Client({
    webSocketFactory: () => new SockJS(endpoint),
    reconnectDelay: 3000,
    debug: () => {
      return;
    }
  });

  client.onConnect = () => {
    client.subscribe(`/topic/project.${projectId}`, (frame) => {
      try {
        const body = JSON.parse(frame.body) as CodeUpdateMessage;
        handlers.onUpdate(body);
      } catch (error) {
        handlers.onError?.(`Failed to parse websocket update: ${String(error)}`);
      }
    });
  };

  client.onStompError = (frame) => {
    handlers.onError?.(
      `WebSocket error: ${frame.headers.message ?? "unknown error"}`
    );
  };

  client.activate();

  return {
    sendUpdate: (message) => {
      if (!client.connected) {
        handlers.onError?.("WebSocket is not connected.");
        return;
      }

      client.publish({
        destination: "/app/code.update",
        body: JSON.stringify(message)
      });
    },
    disconnect: () => {
      client.deactivate();
    }
  };
}
