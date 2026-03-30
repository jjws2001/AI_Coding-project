interface LoginPageProps {
  onLogin: () => void;
}

export function LoginPage({ onLogin }: LoginPageProps) {
  return (
    <main className="login-page">
      <div className="login-overlay" />
      <button className="github-login-button" onClick={onLogin} type="button">
        {"\u4F7F\u7528github\u6388\u6743\u767B\u5F55"}
      </button>
    </main>
  );
}
