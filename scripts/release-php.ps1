# Publish the Melaya PHP SDK to Packagist by mirroring packages/sdk-php into the
# standalone melaya-labs/melaya-php repo (the one Packagist watches). The monorepo
# stays untouched. Run once per release:
#
#     .\scripts\release-php.ps1 -Version 0.1.1
#
# One-time setup (before the first run):
#   1. Create an EMPTY public repo: https://github.com/organizations/melaya-labs/repositories/new
#        name: melaya-php   (no README, no license, no .gitignore)
#   2. After the first run below, submit it once to Packagist:
#        https://packagist.org/packages/submit  ->  https://github.com/melaya-labs/melaya-php
#      then install the Packagist GitHub app on that repo so future tags auto-sync.
param([Parameter(Mandatory)][string]$Version)
$ErrorActionPreference = "Stop"

$src   = (Resolve-Path "$PSScriptRoot\..\packages\sdk-php").Path
$clone = Join-Path $env:TEMP "melaya-php"

if (-not (Test-Path "$clone\.git")) {
    git clone "https://github.com/melaya-labs/melaya-php.git" $clone
}
git -C $clone fetch origin --tags --quiet
# start from the remote main (or an empty tree on first run)
git -C $clone checkout -B main 2>$null
git -C $clone reset --hard origin/main 2>$null

# replace the working tree (keep .git) with the current SDK source
Get-ChildItem $clone -Force | Where-Object { $_.Name -ne ".git" } | Remove-Item -Recurse -Force
Copy-Item "$src\*" $clone -Recurse -Force
if (Test-Path "$clone\vendor") { Remove-Item "$clone\vendor" -Recurse -Force }

git -C $clone add -A
if (git -C $clone status --porcelain) { git -C $clone commit -q -m "release $Version" }
git -C $clone tag "v$Version"
git -C $clone push -u origin main
git -C $clone push origin "v$Version"
Write-Host "`nmelaya/sdk v$Version pushed to melaya-php -> Packagist will sync via the webhook." -ForegroundColor Green
