# bench.ps1 — Windows runner for the framework bench suite.
#
# What this script does that bare `pytest` doesn't:
#   1. Switches the Windows power plan to "High performance" for the
#      duration of the run, then restores the previous plan. (Falls
#      through silently if elevation isn't available.)
#   2. Pins the spawned python process to a single logical CPU via
#      ProcessorAffinity.
#   3. Echoes a one-screen summary of each bench's headline numbers
#      from per-metric summary.json files after the run.
#
# Usage:
#   .\scripts\bench.ps1                  # default: pin to CPU 3
#   .\scripts\bench.ps1 -PinCore 0       # pick a specific core
#   .\scripts\bench.ps1 -NoPin           # disable pinning
#   .\scripts\bench.ps1 -NoHighPerf      # don't touch the power plan

[CmdletBinding()]
param(
    [int]   $PinCore     = 3,
    [switch]$NoPin,
    [switch]$NoHighPerf
)

$ErrorActionPreference = 'Stop'

$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$BenchDir  = Resolve-Path (Join-Path $ScriptDir '..')

Write-Host "──────────────────────────────────────────────────────────────────"
Write-Host " Melaya framework bench — agentic runner suite"
Write-Host (" host  : Windows {0} / {1}" -f [System.Environment]::OSVersion.Version, $env:PROCESSOR_ARCHITECTURE)
Write-Host (" repo  : {0}" -f $BenchDir)
Write-Host (" python: {0}" -f (& python --version 2>&1))
Write-Host "──────────────────────────────────────────────────────────────────"

# ── Power plan: try to switch to High performance, remember old ──
$OriginalPlan = $null
if (-not $NoHighPerf) {
    try {
        $active = (powercfg /getactivescheme) -replace '.*GUID: ([\da-fA-F-]+).*', '$1'
        $OriginalPlan = $active.Trim()
        $highPerf = '8c5e7fda-e8bf-4a96-9a85-a6e23a8c635c'  # Built-in "High performance"
        Write-Host " - switching power plan to High performance ($highPerf)"
        powercfg /setactive $highPerf 2>&1 | Out-Null
    } catch {
        Write-Host " - couldn't switch power plan (skipping): $_"
    }
}

try {
    Push-Location $BenchDir

    Write-Host ""
    Write-Host "──────────────────────────────────────────────────────────────────"
    Write-Host " installing bench package (editable)"
    Write-Host "──────────────────────────────────────────────────────────────────"
    & python -m pip install -e . | Out-Null

    Write-Host ""
    Write-Host "──────────────────────────────────────────────────────────────────"
    Write-Host " running pytest (5 benches, capped at ~5 min total)"
    Write-Host "──────────────────────────────────────────────────────────────────"

    if ($NoPin) {
        & python -m pytest "$BenchDir\benches" -s
    } else {
        $proc = Start-Process -FilePath 'python' `
                              -ArgumentList '-m','pytest', "$BenchDir\benches", '-s' `
                              -PassThru -NoNewWindow
        try {
            $mask = [int][Math]::Pow(2, $PinCore)
            Start-Sleep -Milliseconds 500
            try {
                $proc.ProcessorAffinity = [IntPtr]$mask
                Write-Host (" - pinned PID {0} to CPU {1}" -f $proc.Id, $PinCore)
            } catch {
                Write-Host " - pinning best-effort failed ($_) — bench is still running"
            }
            $proc.WaitForExit()
        } catch {
            $proc.WaitForExit()
        }
    }

    # ── Echo headlines per metric ──
    Write-Host ""
    Write-Host "──────────────────────────────────────────────────────────────────"
    Write-Host " results/ summary"
    Write-Host "──────────────────────────────────────────────────────────────────"
    Get-ChildItem -Path "$BenchDir\results" -Recurse -Filter 'summary.json' | ForEach-Object {
        $j = Get-Content $_.FullName -Raw | ConvertFrom-Json
        Write-Host ("  {0,-32} p50={1,8:N2} µs  p99={2,8:N2} µs  n={3}" `
            -f $j.metric, $j.p50_us, $j.p99_us, $j.n)
    }

    Write-Host ""
    Write-Host (" Files written under:  {0}\results\<metric>\" -f $BenchDir)
    Write-Host ""
    Write-Host " If your hardware tier isn't represented in the README's"
    Write-Host " expectations table, open a PR with your summary.json."

} finally {
    Pop-Location
    if ($OriginalPlan) {
        try {
            powercfg /setactive $OriginalPlan 2>&1 | Out-Null
            Write-Host " - restored original power plan ($OriginalPlan)"
        } catch { }
    }
}
