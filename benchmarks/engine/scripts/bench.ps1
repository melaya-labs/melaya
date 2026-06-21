# bench.ps1 — Windows runner for the state_ticker bench.
#
# What this script does that `cargo bench --bench state_ticker` doesn't:
#   1. Sets the Windows power plan to "High performance" for the duration
#      of the run, then restores the previous plan. (Requires admin OR
#      the user has High performance available; falls through silently
#      otherwise — we don't elevate for you.)
#   2. Pins the bench process to a single logical CPU via ProcessorAffinity.
#   3. Echoes the headline p50/p95/p99 from results/summary.json after.
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
Write-Host " Melaya engine bench — state_ticker_ns"
Write-Host (" host : Windows {0} / {1}" -f [System.Environment]::OSVersion.Version, $env:PROCESSOR_ARCHITECTURE)
Write-Host (" repo : {0}" -f $BenchDir)
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
    Write-Host " running cargo bench (this takes ~30 s including build)"
    Write-Host "──────────────────────────────────────────────────────────────────"

    if ($NoPin) {
        & cargo bench --bench state_ticker
    } else {
        # Start cargo, then set the launched cargo's affinity after spawn.
        # Cargo itself isn't on the hot path — the spawned bench binary is —
        # so we walk the child tree once it appears.
        $proc = Start-Process -FilePath 'cargo' `
                              -ArgumentList 'bench','--bench','state_ticker' `
                              -PassThru -NoNewWindow
        try {
            $mask = [int][Math]::Pow(2, $PinCore)
            # Wait briefly so the child criterion bench binary spawns.
            Start-Sleep -Milliseconds 500
            $children = Get-CimInstance Win32_Process -Filter "ParentProcessId=$($proc.Id)"
            foreach ($c in $children) {
                try {
                    $p = Get-Process -Id $c.ProcessId -ErrorAction SilentlyContinue
                    if ($p) {
                        $p.ProcessorAffinity = [IntPtr]$mask
                        Write-Host (" - pinned PID {0} ({1}) to CPU {2}" -f $c.ProcessId, $c.Name, $PinCore)
                    }
                } catch { }
            }
            $proc.WaitForExit()
        } catch {
            Write-Host " - pinning best-effort failed ($_) — bench is still running"
            $proc.WaitForExit()
        }
    }

    # ── Echo headline ──
    $summary = Join-Path $BenchDir 'results\summary.json'
    if (Test-Path $summary) {
        Write-Host ""
        Write-Host "──────────────────────────────────────────────────────────────────"
        Write-Host " results/summary.json (headline)"
        Write-Host "──────────────────────────────────────────────────────────────────"
        $j = Get-Content $summary -Raw | ConvertFrom-Json
        Write-Host ("  p50 = {0,5} ns" -f $j.p50_ns)
        Write-Host ("  p95 = {0,5} ns" -f $j.p95_ns)
        Write-Host ("  p99 = {0,5} ns" -f $j.p99_ns)
        Write-Host ("  max = {0,5} ns" -f $j.max_ns)
        Write-Host ("  env: {0}" -f $j.env.cpu_model)
    }

    Write-Host ""
    Write-Host " Done. Files written:"
    Write-Host ("   {0}\results\state_ticker_ns.csv" -f $BenchDir)
    Write-Host ("   {0}\results\summary.json" -f $BenchDir)
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
