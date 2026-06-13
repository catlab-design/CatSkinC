param(
    [Parameter(ValueFromRemainingArguments = $true)]
    [string[]]$GradleArgs
)

$requiredJavaMajor = 25

function Get-JavaMajor {
    param(
        [string]$JavaExe
    )

    if (-not (Test-Path $JavaExe)) {
        return $null
    }

    $line = & $JavaExe -version 2>&1 | Select-Object -First 1
    if ($line -match 'version "([0-9]+)') {
        return [int]$Matches[1]
    }

    return $null
}

$projectDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$wrapper = Join-Path $projectDir 'gradlew.bat'

$selectedJavaHome = $null

# Check JAVA_HOME first
if ($env:JAVA_HOME) {
    $currentJavaExe = Join-Path $env:JAVA_HOME 'bin\java.exe'
    $currentMajor = Get-JavaMajor -JavaExe $currentJavaExe
    if ($currentMajor -eq $requiredJavaMajor) {
        $selectedJavaHome = $env:JAVA_HOME
    }
}

# Search in ~/.jdks
if (-not $selectedJavaHome) {
    $jdksRoot = Join-Path $env:USERPROFILE '.jdks'
    if (Test-Path $jdksRoot) {
        $selected = Get-ChildItem -Path $jdksRoot -Directory |
            Where-Object { $_.Name -notlike '.*' } |
            ForEach-Object {
                $javaExe = Join-Path $_.FullName 'bin\java.exe'
                $major = Get-JavaMajor -JavaExe $javaExe
                if ($major -eq $requiredJavaMajor) {
                    [pscustomobject]@{
                        Home = $_.FullName
                        Major = $major
                        Name = $_.Name
                    }
                }
            } |
            Sort-Object Major, Name -Descending |
            Select-Object -First 1

        if ($selected) {
            $selectedJavaHome = $selected.Home
        }
    }
}

if (-not $selectedJavaHome) {
    throw "Java $requiredJavaMajor was not found. Set JAVA_HOME to a JDK $requiredJavaMajor installation or install one under $env:USERPROFILE\.jdks."
}

Write-Host "Using Java $requiredJavaMajor from: $selectedJavaHome" -ForegroundColor Green

$env:JAVA_HOME = $selectedJavaHome
$env:Path = "$selectedJavaHome\bin;$env:Path"

& $wrapper @GradleArgs
exit $LASTEXITCODE
