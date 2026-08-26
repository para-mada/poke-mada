[CmdletBinding()]
param(
    [ValidatePattern('^[0-9]+(?:\.[0-9]+){1,3}$')]
    [string]$Version = '1.0.0',

    [ValidateSet('exe', 'app-image')]
    [string]$Type = 'exe',

    [switch]$BootstrapWix,

    [switch]$SkipTests
)

$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest

if ([System.Environment]::OSVersion.Platform -ne [System.PlatformID]::Win32NT) {
    throw 'El paquete de Windows debe construirse desde Windows.'
}

$projectDirectory = Split-Path -Parent $PSScriptRoot
$targetDirectory = Join-Path $projectDirectory 'target'
$runtimeDirectory = Join-Path $targetDirectory 'app'
$packagingDirectory = Join-Path $targetDirectory 'packaging'
$outputDirectory = Join-Path $targetDirectory 'installer'
$buildToolsDirectory = Join-Path $targetDirectory 'build-tools'
$portableWixDirectory = Join-Path $buildToolsDirectory 'wix314'
$iconPng = Join-Path $projectDirectory 'src\main\resources\net\paramada\pokemada\assets\master-v-emblem.png'
$iconIco = Join-Path $packagingDirectory 'MasterVTournament.ico'

function Test-Jdk21([string]$jdkCandidate) {
    $releaseFile = Join-Path $jdkCandidate 'release'
    $java = Join-Path $jdkCandidate 'bin\java.exe'
    $jpackage = Join-Path $jdkCandidate 'bin\jpackage.exe'
    if (-not (Test-Path -LiteralPath $releaseFile) -or
        -not (Test-Path -LiteralPath $java) -or
        -not (Test-Path -LiteralPath $jpackage)) {
        return $false
    }
    return [bool](Select-String -LiteralPath $releaseFile -Pattern '^JAVA_VERSION="21(?:\.|\")' -Quiet)
}

function Resolve-Jdk21 {
    $homes = [System.Collections.Generic.List[string]]::new()
    if ($env:JAVA_HOME) { $homes.Add($env:JAVA_HOME) }

    $jpackageCommand = Get-Command 'jpackage.exe' -ErrorAction SilentlyContinue
    if ($jpackageCommand) {
        $homes.Add((Split-Path -Parent (Split-Path -Parent $jpackageCommand.Source)))
    }

    $roots = @(
        (Join-Path $env:USERPROFILE '.jdks'),
        (Join-Path $env:ProgramFiles 'Java'),
        (Join-Path $env:ProgramFiles 'Microsoft'),
        (Join-Path $env:ProgramFiles 'Eclipse Adoptium')
    ) | Where-Object { $_ -and (Test-Path -LiteralPath $_) }

    foreach ($root in $roots) {
        Get-ChildItem -LiteralPath $root -Directory -ErrorAction SilentlyContinue |
            ForEach-Object { $homes.Add($_.FullName) }
    }

    foreach ($jdkCandidate in ($homes | Select-Object -Unique)) {
        if (Test-Jdk21 $jdkCandidate) {
            return $jdkCandidate
        }
    }

    throw 'No se encontró un JDK 21 completo con jpackage. Configura JAVA_HOME o añade el JDK desde IntelliJ.'
}

function Resolve-Wix3 {
    $candle = Get-Command 'candle.exe' -ErrorAction SilentlyContinue
    $light = Get-Command 'light.exe' -ErrorAction SilentlyContinue
    if ($candle -and $light) { return }

    $roots = @(
        $portableWixDirectory,
        (Join-Path ${env:ProgramFiles(x86)} 'WiX Toolset v3.14\bin'),
        (Join-Path ${env:ProgramFiles(x86)} 'WiX Toolset v3.11\bin'),
        (Join-Path $env:ProgramFiles 'WiX Toolset v3.14\bin'),
        (Join-Path $env:ProgramFiles 'WiX Toolset v3.11\bin')
    ) | Where-Object { $_ -and (Test-Path -LiteralPath (Join-Path $_ 'candle.exe')) }

    if (-not $roots -and $BootstrapWix) {
        $wixArchive = Join-Path $buildToolsDirectory 'wix314-binaries.zip'
        $wixDownload = 'https://github.com/wixtoolset/wix3/releases/download/wix3141rtm/wix314-binaries.zip'
        New-Item -ItemType Directory -Force -Path $buildToolsDirectory | Out-Null
        Write-Host 'Descargando WiX Toolset 3.14 portable...' -ForegroundColor Cyan
        Invoke-WebRequest -Uri $wixDownload -OutFile $wixArchive
        if (Test-Path -LiteralPath $portableWixDirectory) {
            Remove-Item -LiteralPath $portableWixDirectory -Recurse -Force
        }
        Expand-Archive -LiteralPath $wixArchive -DestinationPath $portableWixDirectory
        Remove-Item -LiteralPath $wixArchive -Force
        if (Test-Path -LiteralPath (Join-Path $portableWixDirectory 'candle.exe')) {
            $roots = @($portableWixDirectory)
        }
    }

    if (-not $roots) {
        throw @'
WiX Toolset 3.x no está instalado. Solo es necesario en la máquina que construye el instalador.
Ejecuta de nuevo con -BootstrapWix para descargar WiX 3.14 portable dentro de target\build-tools,
o instala WiX 3.11/3.14 y abre una terminal nueva.
Mientras tanto puedes generar una carpeta ejecutable con: .\scripts\package-windows.ps1 -Type app-image
'@
    }
    $env:PATH = "$($roots[0]);$env:PATH"
}

function Convert-PngToIco([string]$source, [string]$destination) {
    Add-Type -AssemblyName System.Drawing
    $input = [System.Drawing.Image]::FromFile($source)
    try {
        $bitmap = New-Object System.Drawing.Bitmap 256, 256
        try {
            $graphics = [System.Drawing.Graphics]::FromImage($bitmap)
            try {
                $graphics.Clear([System.Drawing.Color]::Transparent)
                $graphics.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::HighQualityBicubic
                $graphics.DrawImage($input, 0, 0, 256, 256)
            } finally { $graphics.Dispose() }
            $handle = $bitmap.GetHicon()
            $icon = [System.Drawing.Icon]::FromHandle($handle)
            try {
                $stream = [System.IO.File]::Create($destination)
                try { $icon.Save($stream) } finally { $stream.Dispose() }
            } finally { $icon.Dispose() }
        } finally { $bitmap.Dispose() }
    } finally { $input.Dispose() }
}

$jdkHome = Resolve-Jdk21
$java = Join-Path $jdkHome 'bin\java.exe'
$jpackage = Join-Path $jdkHome 'bin\jpackage.exe'
$env:JAVA_HOME = $jdkHome
Write-Host "JDK de empaquetado: $jdkHome" -ForegroundColor DarkGray

$mavenWrapper = Join-Path $projectDirectory 'mvnw.cmd'
if (-not (Test-Path -LiteralPath $mavenWrapper)) { throw 'No se encontró mvnw.cmd.' }

Push-Location $projectDirectory
try {
    $mavenGoals = @('clean')
    if (-not $SkipTests) { $mavenGoals += 'test' }
    $mavenGoals += 'javafx:jlink'
    & $mavenWrapper @mavenGoals
    if ($LASTEXITCODE -ne 0) { throw "Maven terminó con código $LASTEXITCODE." }

    New-Item -ItemType Directory -Force -Path $packagingDirectory, $outputDirectory | Out-Null
    Convert-PngToIco $iconPng $iconIco

    if ($Type -eq 'exe') { Resolve-Wix3 }

    $arguments = @(
        '--type', $Type,
        '--name', 'MasterVTournament',
        '--app-version', $Version,
        '--dest', $outputDirectory,
        '--runtime-image', $runtimeDirectory,
        '--module', 'net.paramada.pokemada/net.paramada.pokemada.Launcher',
        '--icon', $iconIco,
        '--vendor', 'ParaMada',
        '--description', 'Aplicación oficial de Master V Tournament para Pokémon Sun y Moon'
    )

    if ($Type -eq 'exe') {
        $arguments += @(
            '--win-per-user-install',
            '--install-dir', 'Master V Tournament',
            '--win-menu',
            '--win-menu-group', 'Master V Tournament',
            '--win-shortcut',
            '--win-upgrade-uuid', '8f287807-622a-4ba6-ae82-5c240568807e'
        )
    }

    & $jpackage @arguments
    if ($LASTEXITCODE -ne 0) { throw "jpackage terminó con código $LASTEXITCODE." }

    Write-Host "Paquete generado en: $outputDirectory" -ForegroundColor Green
    if ($Type -eq 'exe') {
        Write-Host 'Instalación y caché: %LOCALAPPDATA%\PokeMada' -ForegroundColor Green
    }
} finally {
    Pop-Location
}
