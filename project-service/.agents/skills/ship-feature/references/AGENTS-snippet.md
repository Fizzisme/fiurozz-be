## Feature delivery

When the user explicitly asks to ship a completed project-service feature:

1. Work from `feature/project-service/<feature-name>`, never `develop`, `main`, or `master`.
2. Run the relevant Maven tests and the Docker health check on port 8082.
3. Review `git diff --check`, `git diff`, and `git status --short` before staging.
4. Do not stage secrets, private keys, IDE files, logs, build output, or unrelated changes.
5. Use a Conventional Commit message derived from the actual diff.
6. Push only the feature branch and create a draft PR targeting `develop`, unless the user specifies a dependent feature branch.
7. Never force-push or merge automatically.
8. Report the commit SHA, branch, test result, health-check result, and PR URL.
