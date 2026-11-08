# SPDX-FileCopyrightText: Copyright (C) 2026 The VNCL Contributors
# SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
<#
.SYNOPSIS
Builds the VNCL launcher executable and applies the VNCL icon.

.DESCRIPTION
Runs the Gradle executable task, copies the result into dist/, then patches the
icon resources using scripts/set-exe-icon.ps1. The icon step is required because
the launcher exe is the upstream HMCLauncher stub with the jar appended.

Pass -MicrosoftAuthId to embed the Azure application (client) ID that enables
Microsoft account login.

.PARAMETER MicrosoftAuthId
Optional Azure application (client) ID written to assets/hmcl.properties.
#>
param(
    [string]$JavaHome = 'C:\Program Files\Java\jdk-21',
    [string]$GradleTask = ':HMCL:makeExecutables',
    [string]$MicrosoftAuthId = ''
)

$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
Push-Location $root
try {
    if (Test-Path $JavaHome) {
        $env:JAVA_HOME = $JavaHome
    }
    if ($MicrosoftAuthId) {
        $env:MICROSOFT_AUTH_ID = $MicrosoftAuthId
    }

    & .\gradlew.bat $GradleTask --no-daemon --console=plain
    if ($LASTEXITCODE -ne 0) {
        throw "Gradle task $GradleTask failed"
    }

    $builtExe = 'HMCL\build\libs\VNCL-0.1.SNAPSHOT.exe'
    $builtJar = 'HMCL\build\libs\VNCL-0.1.SNAPSHOT.jar'
    New-Item -ItemType Directory -Force -Path 'dist' | Out-Null
    Copy-Item $builtExe 'dist\VNCL.exe' -Force

    & 'scripts\set-exe-icon.ps1' -ExePath 'dist\VNCL.exe' -IcoPath 'image\vncl.ico' -JarPath $builtJar
    Write-Output 'dist\VNCL.exe is ready'
} finally {
    Pop-Location
}
