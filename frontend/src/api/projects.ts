import type { FileTreeNode, Project, ProjectDTO } from "../types";
import { normalizeFilePath, request } from "./client";

export async function getUserProjects(): Promise<ProjectDTO[]> {
  return request<ProjectDTO[]>("/api/projects");
}

interface UploadPayload {
  name: string;
  file: File;
  githubRepo?: string;
}

export async function uploadProject(payload: UploadPayload): Promise<Project> {
  const formData = new FormData();
  formData.append("name", payload.name);
  formData.append("file", payload.file);
  if (payload.githubRepo) {
    formData.append("githubRepo", payload.githubRepo);
  }

  return request<Project>("/api/projects/upload", {
    method: "POST",
    body: formData
  });
}

export async function importProjectFromGitHub(githubRepo: string): Promise<Project> {
  const formData = new FormData();
  formData.append("githubRepo", githubRepo);
  return request<Project>("/api/projects/import/github", {
    method: "POST",
    body: formData
  });
}

export async function getProjectFileTree(projectId: number): Promise<FileTreeNode> {
  return request<FileTreeNode>(`/api/projects/${projectId}/files`);
}

export async function getFileContent(
  projectId: number,
  filePath: string,
  signal?: AbortSignal
): Promise<string> {
  const normalizedPath = normalizeFilePath(filePath);
  const encodedPath = encodeURIComponent(normalizedPath);
  return request<string>(
    `/api/projects/${projectId}/file-content?path=${encodedPath}`,
    { signal },
    true
  );
}
