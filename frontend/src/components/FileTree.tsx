import { useMemo, useState } from "react";
import type { FileTreeNode } from "../types";
import { normalizeFilePath } from "../api/client";

interface FileTreeProps {
  root: FileTreeNode | null;
  activePath: string | null;
  onOpenFile: (path: string) => void;
}

function sortNodes(nodes: FileTreeNode[]): FileTreeNode[] {
  return [...nodes].sort((a, b) => {
    if (a.type !== b.type) {
      return a.type === "DIRECTORY" ? -1 : 1;
    }
    return a.name.localeCompare(b.name);
  });
}

interface TreeNodeProps {
  node: FileTreeNode;
  activePath: string | null;
  onOpenFile: (path: string) => void;
}

function TreeNode({ node, activePath, onOpenFile }: TreeNodeProps) {
  const [expanded, setExpanded] = useState(true);
  const normalizedPath = useMemo(() => normalizeFilePath(node.path), [node.path]);
  const isActive = normalizedPath && normalizeFilePath(activePath ?? "") === normalizedPath;

  if (node.type === "DIRECTORY") {
    const children = sortNodes(node.children ?? []);
    const isRoot = node.path === "/" || node.path === "";
    return (
      <div className="tree-node">
        <button
          type="button"
          className="tree-folder"
          onClick={() => setExpanded((prev) => !prev)}
        >
          <span>{expanded ? "v" : ">"}</span>
          <strong>{isRoot ? node.name || "root" : node.name}</strong>
        </button>
        {expanded ? (
          <div className="tree-children">
            {children.map((child) => (
              <TreeNode
                key={`${child.path}-${child.name}`}
                node={child}
                activePath={activePath}
                onOpenFile={onOpenFile}
              />
            ))}
          </div>
        ) : null}
      </div>
    );
  }

  return (
    <button
      className={isActive ? "tree-file active" : "tree-file"}
      onDoubleClick={() => onOpenFile(normalizedPath)}
      title="Double click to open"
      type="button"
    >
      {node.name}
    </button>
  );
}

export function FileTree({ root, activePath, onOpenFile }: FileTreeProps) {
  if (!root) {
    return <p className="placeholder-text">Loading file tree...</p>;
  }

  return (
    <div className="file-tree">
      <TreeNode node={root} activePath={activePath} onOpenFile={onOpenFile} />
    </div>
  );
}
