---
name: debugging
description: Evidence-first workflow for build failures, exceptions, and behavioral bugs
triggers: bug, fix, error, exception, 报错, 异常, 修复, 排错
---

1. Reproduce or collect the exact error before editing.
2. Trace the failing path to the smallest responsible module.
3. Apply the smallest coherent fix.
4. Run syntax/compile checks and focused tests in the sandbox.
5. If verification fails, use the new evidence for the next repair attempt.
