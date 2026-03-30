import { useEffect, useMemo, useRef, useState } from "react";
import { aiChatStream, aiCodeExplain, aiCodeReview } from "../api/ai";
import type { ChatMessage, ChatMode } from "../types";

interface ChatPanelProps {
  projectId: number;
  selectedFilePath: string | null;
  selectedFileContent: string;
}

const MODE_OPTIONS: { value: ChatMode; label: string }[] = [
  { value: "chat", label: "chat" },
  { value: "codeAnalysis", label: "codeAnalysis" },
  { value: "codeReview", label: "codeReview" },
  { value: "codeExplain", label: "codeExplain" }
];

function generateSessionId(): string {
  return `session-${Date.now()}-${Math.random().toString(36).slice(2, 8)}`;
}

function buildAttachmentBlock(files: File[], fileTexts: string[]): string {
  if (files.length === 0) {
    return "";
  }

  const chunks = files.map((file, index) => {
    const content = fileTexts[index] ?? "";
    return `Attachment: ${file.name}\n${content.slice(0, 12000)}`;
  });

  return `\n\n${chunks.join("\n\n")}`;
}

function buildCodeAnalysisPrompt(
  question: string,
  selectedFilePath: string | null,
  selectedFileContent: string
): string {
  const codeContext = selectedFileContent
    ? `\n\nCurrent file: ${selectedFilePath ?? "unknown"}\n${selectedFileContent.slice(0, 18000)}`
    : "";
  return `Run code analysis and provide structure, risks, and optimization advice.\nQuestion: ${question}${codeContext}`;
}

export function ChatPanel({
  projectId,
  selectedFilePath,
  selectedFileContent
}: ChatPanelProps) {
  const [mode, setMode] = useState<ChatMode>("chat");
  const [input, setInput] = useState("");
  const [messages, setMessages] = useState<ChatMessage[]>([]);
  const [attachments, setAttachments] = useState<File[]>([]);
  const [sending, setSending] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const sessionId = useMemo(() => generateSessionId(), []);
  const streamAbortRef = useRef<AbortController | null>(null);

  useEffect(() => {
    return () => {
      streamAbortRef.current?.abort();
      streamAbortRef.current = null;
    };
  }, []);

  function appendAssistantChunk(messageId: string, chunk: string) {
    setMessages((prev) =>
      prev.map((message) =>
        message.id === messageId
          ? { ...message, text: `${message.text}${chunk}` }
          : message
      )
    );
  }

  async function sendMessage() {
    setError(null);
    const trimmedInput = input.trim();
    if (!trimmedInput && attachments.length === 0) {
      return;
    }

    const attachmentTexts = await Promise.all(
      attachments.map(async (file) => {
        try {
          return await file.text();
        } catch {
          return "";
        }
      })
    );

    const attachmentBlock = buildAttachmentBlock(attachments, attachmentTexts);
    const finalUserText = `${trimmedInput}${attachmentBlock}`.trim();
    const userMessage: ChatMessage = {
      id: `user-${Date.now()}`,
      role: "user",
      text: finalUserText,
      mode,
      createdAt: Date.now()
    };

    setMessages((prev) => [...prev, userMessage]);
    setInput("");
    setAttachments([]);
    setSending(true);

    try {
      const isStreamMode = mode === "chat" || mode === "codeAnalysis";
      if (isStreamMode) {
        const assistantMessageId = `assistant-${Date.now()}`;
        const streamPrompt =
          mode === "chat"
            ? finalUserText
            : buildCodeAnalysisPrompt(
                finalUserText,
                selectedFilePath,
                selectedFileContent
              );

        setMessages((prev) => [
          ...prev,
          {
            id: assistantMessageId,
            role: "assistant",
            text: "",
            mode,
            createdAt: Date.now()
          }
        ]);

        const abortController = new AbortController();
        streamAbortRef.current = abortController;
        let streamedText = "";

        await aiChatStream(
          {
            projectId,
            sessionId,
            message: streamPrompt
          },
          {
            signal: abortController.signal,
            onChunk: (chunk) => {
              streamedText += chunk;
              appendAssistantChunk(assistantMessageId, chunk);
            }
          }
        );

        if (!streamedText) {
          appendAssistantChunk(assistantMessageId, "[Empty stream response]");
        }
        streamAbortRef.current = null;
      } else {
        const responseText =
          mode === "codeReview"
            ? await aiCodeReview({
                projectId,
                code: selectedFileContent || finalUserText,
                sessionId
              })
            : await aiCodeExplain({
                projectId,
                code: selectedFileContent || finalUserText,
                sessionId
              });

        const aiMessage: ChatMessage = {
          id: `assistant-${Date.now()}`,
          role: "assistant",
          text: responseText,
          mode,
          createdAt: Date.now()
        };
        setMessages((prev) => [...prev, aiMessage]);
      }
    } catch (sendError) {
      setError(String(sendError));
      streamAbortRef.current = null;
    } finally {
      setSending(false);
    }
  }

  return (
    <section className="chat-panel">
      <header className="chat-header">
        <h3>AI Chat</h3>
        <select
          value={mode}
          onChange={(event) => setMode(event.target.value as ChatMode)}
        >
          {MODE_OPTIONS.map((option) => (
            <option key={option.value} value={option.value}>
              {option.label}
            </option>
          ))}
        </select>
      </header>

      <div className="chat-messages">
        {messages.length === 0 ? (
          <p className="placeholder-text">
            Ask a question and optionally attach files. chat/codeAnalysis use SSE streaming. Current mode: {mode}
          </p>
        ) : null}
        {messages.map((message) => (
          <article
            key={message.id}
            className={message.role === "assistant" ? "chat-message ai" : "chat-message user"}
          >
            <header>
              <strong>{message.role === "assistant" ? "AI" : "You"}</strong>
              <small>{message.mode}</small>
            </header>
            <pre>{message.text}</pre>
          </article>
        ))}
      </div>

      <footer className="chat-input-area">
        <textarea
          placeholder="Type your prompt..."
          value={input}
          onChange={(event) => setInput(event.target.value)}
          disabled={sending}
        />
        <div className="chat-actions">
          <input
            type="file"
            multiple
            onChange={(event) => setAttachments(Array.from(event.target.files ?? []))}
          />
          <button type="button" onClick={() => sendMessage()} disabled={sending}>
            {sending ? "Sending..." : "Send"}
          </button>
        </div>
        {attachments.length > 0 ? (
          <p className="helper-text">
            Selected: {attachments.map((file) => file.name).join(", ")}
          </p>
        ) : null}
        {error ? <p className="error-text">{error}</p> : null}
      </footer>
    </section>
  );
}
