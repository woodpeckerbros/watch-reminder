# AGENTS.md

- This is the WatchReminder Android project. Always answer the user in Hebrew.
- At the start of a new session, read `AGENTS.md` first, then only `QUICK RESUME` at the top of `PROJECT_STATUS.md`.
- Read the full `PROJECT_STATUS.md` only when the task requires detail. Do not read `PROJECT_HISTORY.md` unless historical context is specifically relevant.
- Do not move or restructure folders without approval.
- Do not upgrade Gradle, Android Gradle Plugin, SDK versions, dependencies, or package names without approval.
- Prefer small, reversible, task-focused changes. Do not make unrelated changes.
- After a meaningful task, update `PROJECT_STATUS.md`, updating QUICK RESUME first and keeping it under approximately 40 lines.
- Move completed or historical information into `PROJECT_HISTORY.md` instead of allowing `PROJECT_STATUS.md` to grow indefinitely.
- The user permanently authorizes clear commits and pushes to the project's configured Git remote; preserve unrelated worktree changes and never stage them accidentally.
- After every project change made for the user, create a focused commit and push it to the configured Git remote, while leaving unrelated worktree changes unstaged.
- Verify relevant changes by building and, when possible, running on the user's watch and phone.
- The following are shorthand commands: `נתח את לוגי הלילה`, `סיכום Smart Wake של הלילה`, and `Morning Smart Wake report`. For any of them: (1) read `docs/SMART_WAKE_MORNING_REPORT.md`; (2) prefer log files supplied in the current Codex conversation/task; (3) use workspace log discovery only as fallback when no relevant current-task file was supplied; (4) never silently replace a supplied file with an older workspace log; (5) do not modify application code, commit, push, or tune Smart Wake unless explicitly requested afterward. This instruction must be followed independently of prior chat history.
