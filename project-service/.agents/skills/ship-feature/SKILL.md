---
name: ship-feature
description: "Implement or finish a project-service feature and safely ship it through Git: create or reuse a feature branch, run Maven tests, preview Docker, review the diff, commit, push to origin, and open a draft GitHub pull request. Use when the user explicitly asks Codex to code a feature and then commit, push, publish, or create a PR."
---

# Ship Project Feature

Use this workflow only in the Fiurozz repository and only when the user explicitly authorizes publishing.

1. Read applicable `AGENTS.md`, inspect the specification, current branch, `git status`, and the complete diff.
2. Work on `feature/project-service/<feature-name>`. Do not commit, push, or merge directly on `develop`, `main`, or `master`.
3. Implement only the accepted scope and add or update tests. Do not modify generated files, IDE files, logs, or secrets.
4. Run `.\mvnw.cmd test` from `project-service` and review `git diff --check`, `git diff`, and `git status --short`.
5. Start the Docker preview with `docker compose up -d --build project-service` and verify `http://localhost:8082/actuator/health` returns HTTP 200.
6. Before staging, stop if the diff has unrelated work or suspicious files. Never force-push and never merge the PR.
7. Run `scripts/ship-feature.ps1` only after the user says to ship, commit, push, or create a PR. Pass `-IncludeAll` only after reviewing every changed file. Use `-BaseBranch` for a dependent PR.
8. Report the branch, commit hash, test result, health-check result, and draft PR URL.

Read `README-INSTALL.md` for the first-use setup. Copy `references/AGENTS-snippet.md` into the repository's `AGENTS.md` to make this workflow durable. Use `templates/pull_request_template.md` for the draft PR body.
