# SPDX-FileCopyrightText: Copyright (C) 2026 The VNCL Contributors
# SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
<#
.SYNOPSIS
Replaces the icon of a built VNCL launcher executable without losing its appended jar.

.DESCRIPTION
The VNCL exe is the upstream HMCLauncher stub with the launcher jar appended byte-for-byte
(see makeExecutables). Windows' resource update API drops such appended payloads, so this
script extracts the stub, patches its icon resources, and concatenates the stub with the
original jar again.

.PARAMETER ExePath
Path to the launcher executable to patch (in place).

.PARAMETER IcoPath
Path to a multi-size .ico file (see scripts/generate-icons.py).

.PARAMETER JarPath
Path to the jar that was appended to the executable. Defaults to the sibling .jar file.
#>
param(
    [Parameter(Mandatory = $true)][string]$ExePath,
    [Parameter(Mandatory = $true)][string]$IcoPath,
    [Parameter(Mandatory = $false)][string]$JarPath = ''
)

$ErrorActionPreference = 'Stop'

Add-Type -TypeDefinition @'
using System;
using System.Collections.Generic;
using System.ComponentModel;
using System.IO;
using System.Runtime.InteropServices;

public static class ExeIconPatcher
{
    private const int RT_ICON = 3;
    private const int RT_GROUP_ICON = 14;

    [DllImport("kernel32.dll", SetLastError = true, CharSet = CharSet.Unicode)]
    private static extern IntPtr BeginUpdateResourceW(string fileName, bool deleteExisting);

    [DllImport("kernel32.dll", SetLastError = true)]
    private static extern bool UpdateResourceW(IntPtr hUpdate, IntPtr type, IntPtr name, ushort language, byte[] data, uint cbData);

    [DllImport("kernel32.dll", SetLastError = true)]
    private static extern bool EndUpdateResourceW(IntPtr hUpdate, bool discard);

    [DllImport("kernel32.dll", SetLastError = true, CharSet = CharSet.Unicode)]
    private static extern IntPtr LoadLibraryExW(string fileName, IntPtr file, uint flags);

    [DllImport("kernel32.dll", SetLastError = true)]
    private static extern bool FreeLibrary(IntPtr module);

    private delegate bool EnumResNameProc(IntPtr hModule, IntPtr type, IntPtr name, IntPtr param);
    private delegate bool EnumResLangProc(IntPtr hModule, IntPtr type, IntPtr name, ushort language, IntPtr param);

    [DllImport("kernel32.dll", SetLastError = true)]
    private static extern bool EnumResourceNamesW(IntPtr hModule, IntPtr type, EnumResNameProc callback, IntPtr param);

    [DllImport("kernel32.dll", SetLastError = true)]
    private static extern bool EnumResourceLanguagesW(IntPtr hModule, IntPtr type, IntPtr name, EnumResLangProc callback, IntPtr param);

    public static string Patch(string exePath, string icoPath)
    {
        byte[] ico = File.ReadAllBytes(icoPath);
        int count = BitConverter.ToUInt16(ico, 4);
        byte[][] images = new byte[count][];
        List<byte[]> entries = new List<byte[]>();

        for (int i = 0; i < count; i++)
        {
            int entry = 6 + i * 16;
            byte width = ico[entry];
            byte height = ico[entry + 1];
            ushort planes = BitConverter.ToUInt16(ico, entry + 4);
            ushort bits = BitConverter.ToUInt16(ico, entry + 6);
            uint size = BitConverter.ToUInt32(ico, entry + 8);
            uint offset = BitConverter.ToUInt32(ico, entry + 12);

            images[i] = new byte[size];
            Array.Copy(ico, offset, images[i], 0, size);

            List<byte> e = new List<byte>(14);
            e.Add(width);
            e.Add(height);
            e.Add(0);
            e.Add(0);
            e.AddRange(BitConverter.GetBytes(planes));
            e.AddRange(BitConverter.GetBytes(bits));
            e.AddRange(BitConverter.GetBytes(size));
            e.AddRange(BitConverter.GetBytes((ushort)(i + 1)));
            entries.Add(e.ToArray());
        }

        List<byte> group = new List<byte>();
        group.AddRange(BitConverter.GetBytes((ushort)0));
        group.AddRange(BitConverter.GetBytes((ushort)1));
        group.AddRange(BitConverter.GetBytes((ushort)count));
        foreach (byte[] entry in entries)
        {
            group.AddRange(entry);
        }
        byte[] groupData = group.ToArray();

        List<string> groups = new List<string>();
        IntPtr module = LoadLibraryExW(exePath, IntPtr.Zero, 0x2);
        if (module != IntPtr.Zero)
        {
            EnumResourceNamesW(module, (IntPtr)RT_GROUP_ICON, delegate(IntPtr h, IntPtr type, IntPtr name, IntPtr param)
            {
                int id = name.ToInt32();
                EnumResourceLanguagesW(module, (IntPtr)RT_GROUP_ICON, name, delegate(IntPtr h2, IntPtr t2, IntPtr n2, ushort language, IntPtr p2)
                {
                    groups.Add(id + ":" + language);
                    return true;
                }, IntPtr.Zero);
                return true;
            }, IntPtr.Zero);
            FreeLibrary(module);
        }
        if (groups.Count == 0)
        {
            groups.Add("1:0");
        }

        IntPtr update = BeginUpdateResourceW(exePath, false);
        if (update == IntPtr.Zero)
        {
            throw new Win32Exception(Marshal.GetLastWin32Error(), "BeginUpdateResource failed");
        }

        try
        {
            foreach (string groupKey in groups)
            {
                string[] parts = groupKey.Split(':');
                int groupId = int.Parse(parts[0]);
                ushort language = ushort.Parse(parts[1]);

                for (int i = 0; i < count; i++)
                {
                    if (!UpdateResourceW(update, (IntPtr)RT_ICON, (IntPtr)(i + 1), language, images[i], (uint)images[i].Length))
                    {
                        throw new Win32Exception(Marshal.GetLastWin32Error(), "UpdateResource(RT_ICON) failed");
                    }
                }
                if (!UpdateResourceW(update, (IntPtr)RT_GROUP_ICON, (IntPtr)groupId, language, groupData, (uint)groupData.Length))
                {
                    throw new Win32Exception(Marshal.GetLastWin32Error(), "UpdateResource(RT_GROUP_ICON) failed");
                }
            }
        }
        catch
        {
            EndUpdateResourceW(update, true);
            throw;
        }

        if (!EndUpdateResourceW(update, false))
        {
            throw new Win32Exception(Marshal.GetLastWin32Error(), "EndUpdateResource failed");
        }

        return string.Join(", ", groups);
    }
}
'@

$exe = (Resolve-Path -LiteralPath $ExePath).Path
$ico = (Resolve-Path -LiteralPath $IcoPath).Path
if ([string]::IsNullOrWhiteSpace($JarPath)) {
    $JarPath = [IO.Path]::ChangeExtension($exe, '.jar')
}
$jar = (Resolve-Path -LiteralPath $JarPath).Path

$exeBytes = [IO.File]::ReadAllBytes($exe)
$jarBytes = [IO.File]::ReadAllBytes($jar)
if ($exeBytes.Length -le $jarBytes.Length) {
    throw "Executable is smaller than the jar; it does not look like a VNCL launcher exe."
}

$stubLength = $exeBytes.Length - $jarBytes.Length
for ($i = 0; $i -lt $jarBytes.Length; $i++) {
    if ($exeBytes[$stubLength + $i] -ne $jarBytes[$i]) {
        throw "Appended jar does not match $jar; rebuild the executable first."
    }
}

$stub = New-Object byte[] $stubLength
[Array]::Copy($exeBytes, 0, $stub, 0, $stubLength)

$tempStub = [IO.Path]::GetTempFileName()
try {
    [IO.File]::WriteAllBytes($tempStub, $stub)
    $groups = [ExeIconPatcher]::Patch($tempStub, $ico)
    $patchedStub = [IO.File]::ReadAllBytes($tempStub)
} finally {
    Remove-Item -LiteralPath $tempStub -Force -ErrorAction SilentlyContinue
}

Copy-Item -LiteralPath $exe -Destination "$exe.bak" -Force

$output = New-Object byte[] ($patchedStub.Length + $jarBytes.Length)
[Array]::Copy($patchedStub, 0, $output, 0, $patchedStub.Length)
[Array]::Copy($jarBytes, 0, $output, $patchedStub.Length, $jarBytes.Length)
[IO.File]::WriteAllBytes($exe, $output)

Write-Output "Patched icon resources ($groups) into $exe (stub $($patchedStub.Length) bytes + jar $($jarBytes.Length) bytes)"
