# Project-service workflow

## Starting an API feature

When the user says `start a new project-service API` or clearly asks to begin a new project-service feature:

1. Identify the API name from the catalog API documentation before editing code.
2. Run `git status --short` and `git branch --show-current` from the repository root.
3. If unrelated changes are present, stop and ask the user how to handle them. Do not overwrite or stage them.
4. If the current branch is `develop`, `main`, or `master`, create and switch to:

   ```text
   feature/project-service/<short-api-name>
   ```

5. If already on a feature branch, keep that branch unless the user asks to change it.
6. Implement only the accepted API scope using the existing command, handler, JPA adapter, controller, and test patterns.
7. Run the relevant Maven tests before reporting completion.

## Shipping a feature

Only when the user explicitly says `ship it`, `commit`, `push`, or `create a PR`:

1. Review `git diff --check`, `git diff`, and `git status --short`.
2. Confirm no secrets, generated files, IDE files, logs, or unrelated changes will be staged.
3. Use `.agents/skills/ship-feature/scripts/ship-feature.ps1` for tests, Docker preview, commit, push, and a draft PR.
4. Never force-push, merge a PR, or push directly to `develop`, `main`, or `master`.
