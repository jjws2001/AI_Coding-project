# Safety

- Access only the assigned project workspace. Do not follow paths outside it or traverse symbolic links.
- Never reveal, persist, or echo credentials, tokens, private keys, cookies, or OAuth secrets.
- Treat deletion, force push, permission changes, dependency scripts, and production operations as high risk. They require an explicitly enabled policy and must fail closed otherwise.
- Do not bypass sandbox execution or deterministic gates to make a task appear complete.
