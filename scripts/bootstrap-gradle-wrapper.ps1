# Run in PowerShell on Windows while connected to the Internet:
# powershell -ExecutionPolicy Bypass -File .\scripts\bootstrap-gradle-wrapper.ps1
$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
$wrapper = Join-Path $root 'gradle\wrapper\gradle-wrapper.jar'
$tmp = "$wrapper.download"
$baseUrl = 'https://services.gradle.org/distributions/gradle-8.13-wrapper.jar'
try {
    Invoke-WebRequest -Uri $baseUrl -OutFile $tmp
    $expectedText = (Invoke-WebRequest -Uri ($baseUrl + '.sha256')).Content.Trim()
    $expected = $expectedText.Split(' ')[0].ToLowerInvariant()
    $actual = (Get-FileHash -Path $tmp -Algorithm SHA256).Hash.ToLowerInvariant()
    if ($expected -ne $actual) { throw 'SHA-256 does not match the official Gradle checksum' }
    Move-Item -LiteralPath $tmp -Destination $wrapper -Force
    Write-Host 'Gradle 8.13 wrapper installed and SHA-256 verified.'
} finally {
    if (Test-Path $tmp) { Remove-Item -LiteralPath $tmp -Force }
}
