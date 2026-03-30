import { request } from "./client";
import type { AuthCheckResponse, AuthUser } from "../types";

export async function checkAuth(): Promise<AuthCheckResponse> {
  return request<AuthCheckResponse>("/api/auth/check");
}

export async function getCurrentUser(): Promise<AuthUser> {
  return request<AuthUser>("/api/auth/user");
}

export async function logout(): Promise<void> {
  await request("/api/auth/logout", { method: "POST" });
}
