import { useCallback, useEffect, useState } from "react";
import { checkAuth, getCurrentUser, logout } from "./api/auth";
import { getApiBaseUrl } from "./api/client";
import { EditorWorkbench } from "./components/EditorWorkbench";
import { LoginPage } from "./components/LoginPage";
import { OAuthCallbackPage } from "./components/OAuthCallbackPage";
import { ProjectImportPage } from "./components/ProjectImportPage";
import type { AuthUser } from "./types";

interface ActiveProject {
  id: number;
  name: string;
}

export default function App() {
  const [currentPath, setCurrentPath] = useState(window.location.pathname);
  const [authChecked, setAuthChecked] = useState(false);
  const [authenticated, setAuthenticated] = useState(false);
  const [user, setUser] = useState<AuthUser | null>(null);
  const [activeProject, setActiveProject] = useState<ActiveProject | null>(null);
  const [authError, setAuthError] = useState<string | null>(null);
  const isOAuthCallback = currentPath === "/oauth2/callback";

  useEffect(() => {
    function handlePopState() {
      setCurrentPath(window.location.pathname);
    }
    window.addEventListener("popstate", handlePopState);
    return () => window.removeEventListener("popstate", handlePopState);
  }, []);

  useEffect(() => {
    const params = new URLSearchParams(window.location.search);
    const oauthError = params.get("oauthError");
    if (!oauthError) {
      return;
    }
    setAuthError(oauthError);
    window.history.replaceState({}, "", window.location.pathname);
  }, []);

  const refreshAuth = useCallback(async () => {
    try {
      const authState = await checkAuth();
      if (!authState.authenticated) {
        setAuthenticated(false);
        setUser(null);
        return;
      }

      const currentUser = await getCurrentUser();
      setAuthenticated(true);
      setUser(currentUser);
      setAuthError(null);
    } catch (error) {
      setAuthenticated(false);
      setUser(null);
      setAuthError(String(error));
    } finally {
      setAuthChecked(true);
    }
  }, []);

  useEffect(() => {
    if (isOAuthCallback) {
      return;
    }
    refreshAuth().catch(() => {
      return;
    });
  }, [isOAuthCallback, refreshAuth]);

  function startGithubOAuth() {
    const oauthBaseUrl =
      import.meta.env.VITE_OAUTH_BASE_URL ?? getApiBaseUrl();
    window.location.href = `${oauthBaseUrl}/oauth2/authorization/github`;
  }

  async function handleLogout() {
    try {
      await logout();
    } finally {
      setAuthenticated(false);
      setUser(null);
      setActiveProject(null);
      window.history.replaceState({}, "", "/");
      setCurrentPath("/");
    }
  }

  if (isOAuthCallback) {
    return (
      <OAuthCallbackPage
        onSuccess={() => {
          refreshAuth()
            .then(() => {
              window.history.replaceState({}, "", "/");
              setCurrentPath("/");
            })
            .catch((error) => {
              setAuthError(String(error));
              setAuthChecked(true);
            });
        }}
        onFailure={(message) => {
          setAuthError(message);
          setAuthChecked(true);
          window.history.replaceState({}, "", "/");
          setCurrentPath("/");
        }}
      />
    );
  }

  if (!authChecked) {
    return (
      <main className="callback-page">
        <div className="loading-panel">
          <h2>Checking sign in state</h2>
          <p>Loading...</p>
        </div>
      </main>
    );
  }

  if (!authenticated || !user) {
    return (
      <>
        <LoginPage onLogin={startGithubOAuth} />
        {authError ? <p className="error-floating">{authError}</p> : null}
      </>
    );
  }

  if (!activeProject) {
    return (
      <ProjectImportPage
        user={user}
        onProjectReady={(project) =>
          setActiveProject({ id: project.id, name: project.name })
        }
        onLogout={() => {
          handleLogout().catch(() => {
            return;
          });
        }}
      />
    );
  }

  return (
    <EditorWorkbench
      projectId={activeProject.id}
      projectName={activeProject.name}
      onBack={() => setActiveProject(null)}
    />
  );
}
