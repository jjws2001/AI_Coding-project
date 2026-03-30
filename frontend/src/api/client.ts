const API_BASE_URL = import.meta.env.VITE_API_BASE_URL ?? "";

function buildUrl(path: string): string {
  return `${API_BASE_URL}${path}`;
}

export async function request<T>(
  path: string,
  init?: RequestInit,
  allowText = false
): Promise<T> {
  const response = await fetch(buildUrl(path), {
    credentials: "include",
    ...init
  });

  if (!response.ok) {
    const body = await response.text();
    throw new Error(body || `Request failed: ${response.status}`);
  }

  if (allowText) {
    return (await response.text()) as T;
  }

  return (await response.json()) as T;
}

export function getApiBaseUrl(): string {
  return API_BASE_URL;
}

export function normalizeFilePath(path: string): string {
  return path.replace(/\\/g, "/").replace(/^\/+/, "");
}

export function encodeFilePath(path: string): string {
  return normalizeFilePath(path)
    .split("/")
    .filter(Boolean)
    .map((segment) => encodeURIComponent(segment))
    .join("/");
}
