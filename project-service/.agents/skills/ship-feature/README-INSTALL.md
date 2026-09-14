# Install ship-feature for project-service

Prerequisites:

- Docker Desktop is running.
- The repository has an `origin` remote.
- GitHub CLI is installed and authenticated with `gh auth login`.

Run the script from `project-service` only after reviewing `git status` and the diff:

```powershell
powershell -File .\.agents\skills\ship-feature\scripts\ship-feature.ps1 `
  -Feature "delete-project" `
  -CommitMessage "feat(project-service): add soft delete project API" `
  -BaseBranch "develop" `
  -IncludeAll
```

For a dependent pull request, use the earlier feature branch as the base:

```powershell
-BaseBranch "feature/product-service/update-project"
```

The script runs tests, starts a Docker preview, verifies the health endpoint on port 8082, commits, pushes the current feature branch, and creates a draft PR. It refuses to ship from protected branches and rejects typical secret or generated files.
