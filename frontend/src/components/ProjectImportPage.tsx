import { useEffect, useMemo, useState } from "react";
import { getUserProjects, importProjectFromGitHub, uploadProject } from "../api/projects";
import type { AuthUser, Project, ProjectDTO } from "../types";

interface ProjectImportPageProps {
  user: AuthUser;
  onProjectReady: (project: Pick<Project, "id" | "name">) => void;
  onLogout: () => void;
}

type ImportMode = "zip" | "github";

function getRepoName(repoUrl: string): string {
  const cleaned = repoUrl.trim().replace(/\.git$/, "");
  const segments = cleaned.split("/").filter(Boolean);
  return segments[segments.length - 1] ?? "github-project";
}

export function ProjectImportPage({
  user,
  onProjectReady,
  onLogout
}: ProjectImportPageProps) {
  const [projects, setProjects] = useState<ProjectDTO[]>([]);
  const [mode, setMode] = useState<ImportMode>("zip");
  const [projectName, setProjectName] = useState("");
  const [githubRepo, setGithubRepo] = useState("");
  const [zipFile, setZipFile] = useState<File | null>(null);
  const [loadingProjects, setLoadingProjects] = useState(true);
  const [uploading, setUploading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const defaultNameFromRepo = useMemo(() => {
    if (!githubRepo.trim()) {
      return "";
    }
    return getRepoName(githubRepo);
  }, [githubRepo]);

  useEffect(() => {
    async function loadProjects() {
      try {
        setLoadingProjects(true);
        const result = await getUserProjects();
        setProjects(result);
      } catch (loadError) {
        setError(String(loadError));
      } finally {
        setLoadingProjects(false);
      }
    }

    loadProjects().catch(() => {
      return;
    });
  }, []);

  async function submitImport() {
    setError(null);

    const finalProjectName =
      projectName.trim() || (mode === "github" ? defaultNameFromRepo : "");
    if (!finalProjectName) {
      setError("Project name is required.");
      return;
    }

    if (mode === "zip" && !zipFile) {
      setError("Please choose a zip file.");
      return;
    }

    if (mode === "github" && !githubRepo.trim()) {
      setError("GitHub repository URL is required.");
      return;
    }

    try {
      setUploading(true);
      const createdProject =
        mode === "zip"
          ? await uploadProject({
              name: finalProjectName,
              file: zipFile as File
            })
          : await importProjectFromGitHub(githubRepo.trim());
      onProjectReady({ id: createdProject.id, name: createdProject.name });
    } catch (uploadError) {
      setError(String(uploadError));
    } finally {
      setUploading(false);
    }
  }

  return (
    <main className="import-page">
      <header className="import-header">
        <div className="user-chip">
          {user.avatarUrl ? <img src={user.avatarUrl} alt={user.username} /> : null}
          <span>{user.username}</span>
        </div>
        <button className="outline-button" onClick={onLogout} type="button">
          Logout
        </button>
      </header>

      <section className="import-card">
        <h2>Import Project Code</h2>
        <p>Select one import method. The editor starts after import completes.</p>

        <div className="mode-switch">
          <button
            className={mode === "zip" ? "mode-button active" : "mode-button"}
            onClick={() => setMode("zip")}
            type="button"
          >
            Upload ZIP
          </button>
          <button
            className={mode === "github" ? "mode-button active" : "mode-button"}
            onClick={() => setMode("github")}
            type="button"
          >
            Import from GitHub
          </button>
        </div>

        <div className="form-grid">
          <label>
            Project Name
            <input
              value={projectName}
              onChange={(event) => setProjectName(event.target.value)}
              placeholder={
                mode === "github"
                  ? defaultNameFromRepo || "Input project name"
                  : "Input project name"
              }
            />
          </label>

          {mode === "zip" ? (
            <label>
              ZIP File
              <input
                type="file"
                accept=".zip,application/zip"
                onChange={(event) => setZipFile(event.target.files?.[0] ?? null)}
              />
            </label>
          ) : (
            <label>
              GitHub Repository URL
              <input
                value={githubRepo}
                onChange={(event) => setGithubRepo(event.target.value)}
                placeholder="https://github.com/owner/repo"
              />
            </label>
          )}
        </div>

        <button
          className="primary-button"
          disabled={uploading}
          onClick={() => {
            submitImport().catch(() => {
              return;
            });
          }}
          type="button"
        >
          {uploading ? "Importing..." : "Start Import"}
        </button>

        {error ? <p className="error-text">{error}</p> : null}
      </section>

      <section className="project-list-card">
        <h3>Existing Projects</h3>
        {loadingProjects ? <p>Loading project list...</p> : null}
        {!loadingProjects && projects.length === 0 ? <p>No projects yet.</p> : null}
        <ul>
          {projects.map((project) => (
            <li key={project.id}>
              <div>
                <strong>{project.name}</strong>
                <span>{project.status}</span>
              </div>
              <button
                className="outline-button"
                onClick={() => onProjectReady({ id: project.id, name: project.name })}
                type="button"
              >
                Open
              </button>
            </li>
          ))}
        </ul>
      </section>
    </main>
  );
}
