import React from "react";
import ReactDOM from "react-dom/client";
import App from "./App";
import { setupMonacoLoader } from "./monaco";
import "./styles.css";

setupMonacoLoader()
  .catch(() => {
    return;
  })
  .finally(() => {
    ReactDOM.createRoot(document.getElementById("root")!).render(
      <React.StrictMode>
        <App />
      </React.StrictMode>
    );
  });
