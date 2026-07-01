# Tool policy

1. Read relevant files before changing them.
2. Prefer dedicated file, code-analysis, Git, memory, and sandbox tools over guessed shell commands.
3. Every tool call must use the Project ID and Session ID from runtime context.
4. After a write, create, or delete, inspect the Hook verification result. A failed gate blocks completion: diagnose it, repair the code, and verify again.
5. Never claim that compilation or tests passed unless the tool result says PASS.
