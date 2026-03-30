import { useCallback, useEffect, useRef, useState } from "react";
import Editor from "@monaco-editor/react";
import { getFileContent, getProjectFileTree } from "../api/projects";
import { FileTree } from "./FileTree";
import { connectProjectSync } from "../lib/websocket";
import { normalizeFilePath } from "../api/client";
import { ChatPanel } from "./ChatPanel";
import type { FileTreeNode } from "../types";

interface EditorWorkbenchProps {
  projectId: number;
  projectName: string;
  onBack: () => void;
}

const LANGUAGE_MAP: Record<string, string> = {
  ts: "typescript",
  tsx: "typescript",
  js: "javascript",
  jsx: "javascript",
  java: "java",
  py: "python",
  json: "json",
  yml: "yaml",
  yaml: "yaml",
  xml: "xml",
  html: "html",
  css: "css",
  scss: "scss",
  md: "markdown",
  sh: "shell"
};

function detectLanguage(filePath: string | null): string {
  if (!filePath) {
    return "plaintext";
  }
  const ext = filePath.split(".").pop()?.toLowerCase() ?? "";
  return LANGUAGE_MAP[ext] ?? "plaintext";
}

interface SyncConnectionLike {
  sendUpdate: (message: { projectId: number; filePath: string; content: string }) => void;
  disconnect: () => void;
}

export function EditorWorkbench({
  projectId,
  projectName,
  onBack
}: EditorWorkbenchProps) {
  const [fileTree, setFileTree] = useState<FileTreeNode | null>(null);
  const [openFiles, setOpenFiles] = useState<string[]>([]);
  const [activeFile, setActiveFile] = useState<string | null>(null);
  const [fileContents, setFileContents] = useState<Record<string, string>>({});
  const [dirtySet, setDirtySet] = useState<Set<string>>(new Set());
  const [statusMessage, setStatusMessage] = useState("Ready");
  const [syncError, setSyncError] = useState<string | null>(null);
  const syncRef = useRef<SyncConnectionLike | null>(null);

  useEffect(() => {
    setOpenFiles([]);
    setActiveFile(null);
    setFileContents({});
    setDirtySet(new Set());
    setSyncError(null);
  }, [projectId]);

  useEffect(() => {
    async function loadTree() {
      try {
        setStatusMessage("Loading file tree...");
        const tree = await getProjectFileTree(projectId);
        setFileTree(tree);
        setStatusMessage("File tree loaded");
      } catch (error) {
        setStatusMessage(`Failed to load file tree: ${String(error)}`);
      }
    }

    loadTree().catch(() => {
      return;
    });
  }, [projectId]);

  useEffect(() => {
    const connection = connectProjectSync(projectId, {
      onUpdate: (message) => {
        const path = normalizeFilePath(message.filePath);
        setFileContents((prev) => ({ ...prev, [path]: message.content }));
        setDirtySet((prev) => {
          const next = new Set(prev);
          next.delete(path);
          return next;
        });
        setStatusMessage(`Synced: ${path}`);
      },
      onError: (error) => setSyncError(error)
    });

    syncRef.current = connection;
    return () => {
      connection.disconnect();
      syncRef.current = null;
    };
  }, [projectId]);

  const openFile = useCallback(
    async (path: string) => {
      const normalizedPath = normalizeFilePath(path);
      if (!normalizedPath) {
        return;
      }

      setOpenFiles((prev) =>
        prev.includes(normalizedPath) ? prev : [...prev, normalizedPath]
      );
      setActiveFile(normalizedPath);

      if (fileContents[normalizedPath] !== undefined) {
        return;
      }

      const controller = new AbortController();
      const timeoutId = window.setTimeout(() => controller.abort(), 15000);
      try {
        setStatusMessage(`Loading ${normalizedPath} ...`);
        const content = await getFileContent(projectId, normalizedPath, controller.signal);
        setFileContents((prev) => ({ ...prev, [normalizedPath]: content }));
        setStatusMessage(`Opened ${normalizedPath}`);
      } catch (error) {
        const message =
          controller.signal.aborted && error instanceof Error
            ? `Loading timed out: ${normalizedPath}`
            : `Failed to load file: ${String(error)}`;
        setStatusMessage(message);
      } finally {
        window.clearTimeout(timeoutId);
      }
    },
    [fileContents, projectId]
  );

  const closeFile = useCallback((path: string) => {
    setOpenFiles((prev) => prev.filter((item) => item !== path));
    setFileContents((prev) => {
      const next = { ...prev };
      delete next[path];
      return next;
    });
    setDirtySet((prev) => {
      const next = new Set(prev);
      next.delete(path);
      return next;
    });
    setActiveFile((prev) => (prev === path ? null : prev));
    setStatusMessage(`Closed ${path}`);
  }, []);

  const saveActiveFile = useCallback(() => {
    if (!activeFile) {
      return;
    }
    const content = fileContents[activeFile] ?? "";
    syncRef.current?.sendUpdate({
      projectId,
      filePath: activeFile,
      content
    });
    setDirtySet((prev) => {
      const next = new Set(prev);
      next.delete(activeFile);
      return next;
    });
    setStatusMessage(`Saved ${activeFile}`);
  }, [activeFile, fileContents, projectId]);

  useEffect(() => {
    function handleKeyDown(event: KeyboardEvent) {
      if ((event.ctrlKey || event.metaKey) && event.key.toLowerCase() === "s") {
        event.preventDefault();
        saveActiveFile();
      }
    }
    window.addEventListener("keydown", handleKeyDown);
    return () => window.removeEventListener("keydown", handleKeyDown);
  }, [saveActiveFile]);

  const activeContent = activeFile ? fileContents[activeFile] ?? "" : "";

  return (
    <main className="editor-page">
      <header className="editor-topbar">
        <div>
          <strong>{projectName}</strong>
          <span>{statusMessage}</span>
        </div>
        <div className="topbar-actions">
          <button className="outline-button" onClick={saveActiveFile} type="button">
            Save (Ctrl+S)
          </button>
          <button className="outline-button" onClick={onBack} type="button">
            Back
          </button>
        </div>
      </header>

      <section className="editor-layout">
        <aside className="file-tree-pane">
          <FileTree root={fileTree} activePath={activeFile} onOpenFile={openFile} />
        </aside>

        <section className="editor-pane">
          <div className="editor-tabs">
            {openFiles.map((path) => {
              const isDirty = dirtySet.has(path);
              const isActive = path === activeFile;
              return (
                <div
                  key={path}
                  className={isActive ? "editor-tab active" : "editor-tab"}
                >
                  <button
                    type="button"
                    className="editor-tab-main"
                    onClick={() => setActiveFile(path)}
                  >
                    {path.split("/").pop()}
                    {isDirty ? "*" : ""}
                  </button>
                  <button
                    type="button"
                    className="editor-tab-close"
                    onClick={() => closeFile(path)}
                    aria-label={`Close ${path}`}
                    title="Close file"
                  >
                    x
                  </button>
                </div>
              );
            })}
          </div>

          <div className="monaco-holder">
            {activeFile ? (
              <Editor
                language={detectLanguage(activeFile)}
                value={activeContent}
                onChange={(value) => {
                  const nextValue = value ?? "";
                  setFileContents((prev) => ({ ...prev, [activeFile]: nextValue }));
                  setDirtySet((prev) => {
                    const next = new Set(prev);
                    next.add(activeFile);
                    return next;
                  });
                }}
                options={{
                  minimap: { enabled: false },
                  fontSize: 14,
                  automaticLayout: true
                }}
                theme="vs-dark"
              />
            ) : (
              <p className="placeholder-text">Double click a file in the left tree to open.</p>
            )}
          </div>
        </section>

        <aside className="chat-pane">
          <ChatPanel
            projectId={projectId}
            selectedFilePath={activeFile}
            selectedFileContent={activeContent}
          />
        </aside>
      </section>

      {syncError ? <p className="error-banner">{syncError}</p> : null}
    </main>
  );
}
