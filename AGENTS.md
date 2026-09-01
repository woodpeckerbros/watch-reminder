# AGENTS.md

- This is the WatchReminder Android project. Always answer the user in Hebrew.
- At the start of a new session, read `AGENTS.md` first, then only `QUICK RESUME` at the top of `PROJECT_STATUS.md`.
- Read the full `PROJECT_STATUS.md` only when the task requires detail. Do not read `PROJECT_HISTORY.md` unless historical context is specifically relevant.
- Do not move or restructure folders without approval.
- Do not upgrade Gradle, Android Gradle Plugin, SDK versions, dependencies, or package names without approval.
- Prefer small, reversible, task-focused changes. Do not make unrelated changes.
- After a meaningful task, update `PROJECT_STATUS.md`, updating QUICK RESUME first and keeping it under approximately 40 lines.
- Move completed or historical information into `PROJECT_HISTORY.md` instead of allowing `PROJECT_STATUS.md` to grow indefinitely.
- After every meaningful change, create a clear commit and push to Git when the destination is approved; preserve unrelated worktree changes.
- Verify relevant changes by building and, when possible, running on the user's watch and phone.
