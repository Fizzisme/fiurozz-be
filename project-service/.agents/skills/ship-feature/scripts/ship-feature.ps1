[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)]
    [ValidateNotNullOrEmpty()]
    [string]$Feature,

    [Parameter(Mandatory = $true)]
    [ValidateNotNullOrEmpty()]
    [string]$CommitMessage,

    [ValidateNotNullOrEmpty()]
    [string]$BaseBranch = "develop",

    [switch]$IncludeAll
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

function Assert-LastExitCode {
    param([Parameter(Mandatory = $true)][string]$Step)
    if ($LASTEXITCODE -ne 0) { throw "$Step failed with exit code $LASTEXITCODE." }
}

function Convert-ToSlug {
    param([Parameter(Mandatory = $true)][string]$Value)
    $slug = ($Value.Trim().ToLowerInvariant() -replace "[^a-z0-9]+", "-").Trim("-")
    if ([string]::IsNullOrWhiteSpace($slug)) { throw "Feature name cannot be converted to a valid branch slug." }
    return $slug
}

function Assert-NoForbiddenFiles {
    param([Parameter(Mandatory = $true)][string[]]$Paths)
    $forbiddenPatterns = @(
        '(^|/)\.env($|\.)', '\.(pem|key|p12|pfx)$', '(^|/)id_rsa($|\.)',
        '(^|/)\.idea/', '(^|/)target/', '(^|/)node_modules/', '\.log$', '(^|/)\.DS_Store$'
    )
    $blocked = @()
    foreach ($path in $Paths) {
        $normalized = $path -replace '\\', '/'
        if ($forbiddenPatterns.Where({ $normalized -match $_ }).Count -gt 0) { $blocked += $path }
    }
    if ($blocked.Count -gt 0) {
        throw "Refusing to stage suspicious or generated files:`n$($blocked | Sort-Object -Unique | Out-String)"
    }
}

$projectServiceRoot = (Resolve-Path (Join-Path $PSScriptRoot "..\..\..\..")).Path
$repositoryRoot = Split-Path -Parent $projectServiceRoot
$templatePath = Join-Path $PSScriptRoot "..\templates\pull_request_template.md"
$slug = Convert-ToSlug $Feature

Push-Location $repositoryRoot
try {
    git rev-parse --is-inside-work-tree | Out-Null
    Assert-LastExitCode "Git repository check"
    if (-not (Get-Command gh -ErrorAction SilentlyContinue)) { throw "GitHub CLI 'gh' is not installed or not in PATH." }
    gh auth status
    Assert-LastExitCode "GitHub authentication check"

    $remoteUrl = (git remote get-url origin).Trim()
    Assert-LastExitCode "Reading origin remote"
    if ([string]::IsNullOrWhiteSpace($remoteUrl)) { throw "The repository has no usable origin remote." }
    $conflicts = @(git diff --name-only --diff-filter=U)
    Assert-LastExitCode "Conflict check"
    if ($conflicts.Count -gt 0) { throw "Resolve merge conflicts before shipping." }

    $currentBranch = (git branch --show-current).Trim()
    Assert-LastExitCode "Reading current branch"
    if ([string]::IsNullOrWhiteSpace($currentBranch)) { throw "Detached HEAD is not supported." }
    if ($currentBranch -in @("develop", "main", "master", $BaseBranch)) {
        $currentBranch = "feature/project-service/$slug"
        git switch -c $currentBranch
        Assert-LastExitCode "Creating feature branch"
    }

    $changedFiles = @(git status --porcelain=v1 | ForEach-Object { if ($_.Length -ge 4) { $_.Substring(3).Trim() } })
    Assert-LastExitCode "Reading Git status"
    if ($changedFiles.Count -eq 0) { throw "There are no working-tree changes to ship." }
    Assert-NoForbiddenFiles -Paths $changedFiles
    if (-not $IncludeAll) {
        throw "Review the diff, then rerun with -IncludeAll to authorize staging all listed changes."
    }

    git diff --check
    Assert-LastExitCode "Diff validation"
    docker compose up -d product-db
    Assert-LastExitCode "Starting PostgreSQL"
    Push-Location $projectServiceRoot
    try {
        .\mvnw.cmd test
        Assert-LastExitCode "Maven tests"
    } finally { Pop-Location }
    docker compose up -d --build project-service
    Assert-LastExitCode "Starting Docker preview"
    $health = Invoke-WebRequest -Uri "http://localhost:8082/actuator/health" -UseBasicParsing -TimeoutSec 20
    if ($health.StatusCode -ne 200) { throw "Project-service health check returned HTTP $($health.StatusCode)." }

    git add --all
    Assert-LastExitCode "Staging changes"
    $stagedFiles = @(git diff --cached --name-only)
    Assert-LastExitCode "Reading staged files"
    Assert-NoForbiddenFiles -Paths $stagedFiles
    git diff --cached --check
    Assert-LastExitCode "Staged diff validation"
    git commit -m $CommitMessage
    Assert-LastExitCode "Creating commit"
    $commitHash = (git rev-parse --short HEAD).Trim()
    git push -u origin $currentBranch
    Assert-LastExitCode "Pushing branch"

    $prUrl = (& gh pr list --head $currentBranch --base $BaseBranch --state open --json url --jq '.[0].url').Trim()
    if ([string]::IsNullOrWhiteSpace($prUrl)) {
        $tempBody = Join-Path ([System.IO.Path]::GetTempPath()) "project-service-pr-$slug-$PID.md"
        try {
            $body = [System.IO.File]::ReadAllText($templatePath).Replace("{{COMMIT_MESSAGE}}", $CommitMessage)
            [System.IO.File]::WriteAllText($tempBody, $body, [System.Text.UTF8Encoding]::new($false))
            $prUrl = (& gh pr create --draft --base $BaseBranch --head $currentBranch --title $CommitMessage --body-file $tempBody).Trim()
            Assert-LastExitCode "Creating draft pull request"
        } finally {
            Remove-Item $tempBody -Force -ErrorAction SilentlyContinue
        }
    }

    Write-Host "Ship completed."
    Write-Host "Branch : $currentBranch"
    Write-Host "Commit : $commitHash $CommitMessage"
    Write-Host "Health : http://localhost:8082/actuator/health"
    Write-Host "PR     : $prUrl"
} finally {
    Pop-Location
}
