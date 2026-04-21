# dump-mobile.ps1
# Использование: .\dump-mobile.ps1 [-OutputFile "dump.txt"] [-ProjectRoot "."]

param(
    [string]$OutputFile = "mobile-dump.txt",
    [string]$ProjectRoot = "."
)

$ProjectRoot = Resolve-Path $ProjectRoot

# --- Расширения файлов, которые нас интересуют ---
$includeExtensions = @(
    ".kt", ".kts",          # Kotlin
    ".java",                 # Java (если есть)
    ".swift",                # iOS
    ".xml",                  # AndroidManifest, layouts, resources
    ".toml",                 # gradle version catalog
    ".properties",           # gradle.properties
    ".pro",                  # proguard
    ".plist",                # iOS Info.plist
    ".pbxproj",              # Xcode project (только структура, может быть большим)
    ".sq",                   # SQLDelight
    ".json"                  # package.json, google-services.json structure
)

# --- Имена файлов, которые берём независимо от расширения ---
$includeFileNames = @(
    "settings.gradle.kts", "settings.gradle",
    "build.gradle.kts", "build.gradle",
    "gradle.properties",
    "local.properties",
    "libs.versions.toml",
    "AndroidManifest.xml",
    "proguard-rules.pro",
    "Podfile",
    "Gemfile",
    ".gitignore"
)

# --- Папки, которые ПОЛНОСТЬЮ исключаем ---
$excludeDirs = @(
    ".gradle", ".idea", ".git",
    "build", "Build",
    ".kotlin",
    "caches",
    "transforms",
    "generated",
    "intermediates",
    "tmp", "temp",
    "node_modules",
    ".cxx",
    "xcuserdata",
    "DerivedData",
    "Pods",
    ".konan",
    "kotlin-js-store",
    "reports",
    "outputs",
    "__pycache__"
)

# --- Конкретные файлы, которые пропускаем ---
$excludeFilePatterns = @(
    "*.hprof",
    "*.class",
    "*.dex",
    "*.apk",
    "*.aab",
    "*.aar",
    "*.jar",
    "*.so",
    "*.dylib",
    "*.png", "*.jpg", "*.jpeg", "*.gif", "*.webp", "*.svg", "*.ico",
    "*.ttf", "*.otf", "*.woff", "*.woff2",
    "*.mp3", "*.mp4", "*.wav",
    "*.zip", "*.tar", "*.gz",
    "*.keystore", "*.jks",
    "*.p12", "*.pem", "*.cer",
    "*.DS_Store",
    "gradlew", "gradlew.bat",
    "*.lock"
)

# --- Максимальный размер файла (100 KB) ---
$maxFileSizeKB = 100

# ============================================================

function Test-ExcludedDir {
    param([string]$Path)
    foreach ($dir in $excludeDirs) {
        if ($Path -match "([\\/])$([regex]::Escape($dir))([\\/]|$)") {
            return $true
        }
    }
    return $false
}

function Test-ExcludedFile {
    param([string]$FileName)
    foreach ($pattern in $excludeFilePatterns) {
        if ($FileName -like $pattern) {
            return $true
        }
    }
    return $false
}

function Test-IncludedFile {
    param([System.IO.FileInfo]$File)
    $ext = $File.Extension.ToLower()
    $name = $File.Name

    if ($includeFileNames -contains $name) { return $true }
    if ($includeExtensions -contains $ext) { return $true }
    return $false
}

# ============================================================

Write-Host "Scanning: $ProjectRoot" -ForegroundColor Cyan
Write-Host "Output:   $OutputFile" -ForegroundColor Cyan

$output = [System.Text.StringBuilder]::new()
$null = $output.AppendLine("=" * 70)
$null = $output.AppendLine("MOBILE PROJECT DUMP")
$null = $output.AppendLine("Generated: $(Get-Date -Format 'yyyy-MM-dd HH:mm:ss')")
$null = $output.AppendLine("Root: $ProjectRoot")
$null = $output.AppendLine("=" * 70)

# --- 1. Дерево структуры ---
$null = $output.AppendLine("")
$null = $output.AppendLine("### DIRECTORY STRUCTURE ###")
$null = $output.AppendLine("")

$allDirs = Get-ChildItem -Path $ProjectRoot -Recurse -Directory -Force -ErrorAction SilentlyContinue |
    Where-Object { -not (Test-ExcludedDir $_.FullName) } |
    Sort-Object FullName

foreach ($dir in $allDirs) {
    $rel = $dir.FullName.Substring($ProjectRoot.Path.Length).TrimStart('\', '/')
    $depth = ($rel -split '[\\/]').Count
    $indent = "  " * $depth
    $null = $output.AppendLine("$indent$($dir.Name)/")
}

# --- 2. Содержимое файлов ---
$null = $output.AppendLine("")
$null = $output.AppendLine("### FILE CONTENTS ###")

$files = Get-ChildItem -Path $ProjectRoot -Recurse -File -Force -ErrorAction SilentlyContinue |
    Where-Object {
        -not (Test-ExcludedDir $_.FullName) -and
        -not (Test-ExcludedFile $_.Name) -and
        (Test-IncludedFile $_) -and
        ($_.Length / 1KB -le $maxFileSizeKB)
    } |
    Sort-Object FullName

$fileCount = 0
foreach ($file in $files) {
    $rel = $file.FullName.Substring($ProjectRoot.Path.Length).TrimStart('\', '/')
    $null = $output.AppendLine("")
    $null = $output.AppendLine("-" * 70)
    $null = $output.AppendLine("FILE: $rel")
    $null = $output.AppendLine("SIZE: $([math]::Round($file.Length / 1KB, 1)) KB")
    $null = $output.AppendLine("-" * 70)

    try {
        $content = Get-Content -Path $file.FullName -Raw -ErrorAction Stop
        $null = $output.AppendLine($content)
    } catch {
        $null = $output.AppendLine("[ERROR reading file: $_]")
    }
    $fileCount++
}

$null = $output.AppendLine("")
$null = $output.AppendLine("=" * 70)
$null = $output.AppendLine("Total files dumped: $fileCount")
$null = $output.AppendLine("=" * 70)

# --- Запись ---
$output.ToString() | Out-File -FilePath $OutputFile -Encoding UTF8

Write-Host ""
Write-Host "Done! $fileCount files dumped to $OutputFile" -ForegroundColor Green
Write-Host "File size: $([math]::Round((Get-Item $OutputFile).Length / 1KB, 1)) KB" -ForegroundColor Green