import { useEffect } from "react";

interface OAuthCallbackPageProps {
  onSuccess: () => void;
  onFailure: (message: string) => void;
}

export function OAuthCallbackPage({
  onSuccess,
  onFailure
}: OAuthCallbackPageProps) {
  useEffect(() => {
    const searchParams = new URLSearchParams(window.location.search);
    const success = searchParams.get("success");
    const error = searchParams.get("error");

    if (success === "true") {
      onSuccess();
      return;
    }

    onFailure(error ?? "GitHub OAuth failed.");
  }, [onFailure, onSuccess]);

  return (
    <main className="callback-page">
      <div className="loading-panel">
        <h2>Finishing GitHub sign in</h2>
        <p>Checking your session...</p>
      </div>
    </main>
  );
}
