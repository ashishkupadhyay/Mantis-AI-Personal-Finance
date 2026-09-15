<#
.SYNOPSIS
  Starts one Android emulator with a free-RAM check and optional stall mitigations for Windows 11 + WHPX.

.DESCRIPTION
  Findings on the development machine (i5-13500H, Windows 11 26200, emulator 37.1):
    - The Google Play API 28/36 images boot in ~30 s with the AVD's own defaults (GPU auto, 4 vCPUs) — the
      Android Studio configuration — as long as ~3 GB of RAM is free. Stop Gradle daemons first (gradlew --stop).
    - The earlier "mitigations" (software GPU, 2 vCPUs, P-core affinity) were tried against WHPX stalls; on
      2026-09-13 they stalled the guest at 0 % CPU while the plain defaults booted. They remain available as
      switches (-Gpu swiftshader_indirect -Cores 2 -PinPCores) for experiments, but are off by default.
    - Two emulators + Gradle daemons exhaust RAM -> one instance, warn under 3 GB free.

.EXAMPLE
  .\tools\Start-Emulator.ps1 -Avd Medium_Phone
  .\tools\Start-Emulator.ps1 -Avd Pixel_9 -Gpu swiftshader_indirect -Cores 2 -PinPCores
#>
param(
    [Parameter(Mandatory = $true)] [string] $Avd,
    [int] $Cores = 0,                     # 0 = the AVD's own hw.cpu.ncore
    [ValidateSet('swiftshader_indirect', 'angle_indirect', 'host', 'guest', 'auto')] [string] $Gpu = 'auto',
    [int] $Memory = 0,                    # 0 = the AVD's own hw.ramSize
    [switch] $ColdBoot,
    [switch] $PinPCores,
    [string] $PCoreAffinityMask = '0xFF'   # logical CPUs 0-7 = the four P-cores (hyper-threaded) on an i5-13500H
)

$ErrorActionPreference = 'Stop'
$sdk = if ($env:ANDROID_HOME) { $env:ANDROID_HOME } else { Join-Path $env:LOCALAPPDATA 'Android\Sdk' }
$emu = Join-Path $sdk 'emulator\emulator.exe'
if (-not (Test-Path $emu)) { throw "emulator not found at $emu" }

# One instance at a time: a second running emulator is the fastest way to a frozen host.
$running = Get-Process | Where-Object { $_.ProcessName -match '^qemu-system' }
if ($running) {
    Write-Warning "An emulator is already running (PID $($running.Id -join ', ')). Close it first (or stop it with: Get-Process qemu-system* | Stop-Process)."
    return
}

# Stale locks from a crashed instance block the launch with 'Running multiple emulators with the same AVD'.
$avdDir = Join-Path $env:USERPROFILE ".android\avd\$Avd.avd"
if (-not (Test-Path $avdDir)) { throw "AVD '$Avd' not found under $avdDir" }
Get-ChildItem $avdDir -Filter '*.lock' -Force -ErrorAction SilentlyContinue |
    ForEach-Object { Remove-Item $_.FullName -Force -Recurse -Confirm:$false -ErrorAction SilentlyContinue }

$freeGb = [math]::Round((Get-CimInstance Win32_OperatingSystem).FreePhysicalMemory / 1MB, 1)
if ($freeGb -lt 3) { Write-Warning "Only $freeGb GB of RAM is free; the guest needs ~2 GB plus host overhead. Close Gradle daemons (gradlew --stop) or other apps first." }

$args = @('-avd', $Avd, '-gpu', $Gpu, '-no-snapshot-save')
if ($Cores -gt 0) { $args += @('-cores', $Cores) }
if ($Memory -gt 0) { $args += @('-memory', $Memory) }
if ($ColdBoot) { $args += '-no-snapshot-load' }

Write-Host "Starting $Avd  (gpu=$Gpu, cores=$(if ($Cores -gt 0) { $Cores } else { 'avd default' }), memory=$(if ($Memory -gt 0) { "${Memory}MB" } else { 'avd default' }), free RAM ${freeGb}GB)"
$p = Start-Process -FilePath $emu -ArgumentList $args -PassThru

# Wait for the qemu process (the emulator.exe launcher spawns it); optionally pin it to the P-cores.
$deadline = (Get-Date).AddSeconds(30)
$q = $null
while (-not $q -and (Get-Date) -lt $deadline) {
    Start-Sleep 2
    $q = Get-Process | Where-Object { $_.ProcessName -match '^qemu-system' }
}
if ($q) {
    if ($PinPCores) {
        foreach ($qp in $q) {
            $qp.ProcessorAffinity = [IntPtr]([Convert]::ToInt64($PCoreAffinityMask, 16))
            $qp.PriorityClass = 'AboveNormal'
        }
        Write-Host "Pinned qemu (PID $($q.Id -join ', ')) to CPU mask $PCoreAffinityMask, priority AboveNormal."
    } else {
        Write-Host "qemu running (PID $($q.Id -join ', '))."
    }
    Write-Host "Android Studio will list the device automatically; deploy with the 'app' run configuration."
} else {
    Write-Warning 'qemu process not found within 30 s; the emulator may have failed to start. Check the emulator window/log.'
}
