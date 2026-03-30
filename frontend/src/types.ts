export interface AuthCheckResponse {
  authenticated: boolean;
  userId?: number;
  username?: string;
}

export interface AuthUser {
  id: number;
  username: string;
  email?: string;
  avatarUrl?: string;
  githubId?: string;
}

export interface ProjectDTO {
  id: number;
  name: string;
  description?: string;
  githubRepoUrl?: string;
  githubRepoName?: string;
  status: "INITIALIZING" | "ACTIVE" | "ARCHIVED" | "ERROR";
  createdAt?: string;
  updatedAt?: string;
  lastBackupAt?: string;
  fileCount?: number;
  totalSize?: number;
}

export interface Project {
  id: number;
  name: string;
  description?: string;
  localPath?: string;
  githubRepoUrl?: string;
  githubRepoName?: string;
  status: "INITIALIZING" | "ACTIVE" | "ARCHIVED" | "ERROR";
  createdAt?: string;
  updatedAt?: string;
}

export interface FileTreeNode {
  name: string;
  path: string;
  type: "FILE" | "DIRECTORY";
  size?: number;
  extension?: string;
  children?: FileTreeNode[];
}

export interface CodeUpdateMessage {
  projectId: number;
  filePath: string;
  content: string;
}

export type ChatMode = "chat" | "codeAnalysis" | "codeReview" | "codeExplain";

export interface ChatMessage {
  id: string;
  role: "user" | "assistant";
  text: string;
  mode: ChatMode;
  createdAt: number;
}
