# Запуск: powershell -ExecutionPolicy Bypass -File dump_code.ps1
# Результат: project_dump.txt в корне проекта

$outFile = "project_dump.txt"
$extensions = @("*.py", "*.yml", "*.yaml", "*.toml", "*.cfg", "*.ini", "*.txt", "*.conf", "*.mako", "*.env*", "*.sh", "Dockerfile*")
$excludeDirs = @(".git", "__pycache__", ".venv", "venv", "node_modules", ".mypy_cache", ".pytest_cache", "*.egg-info", ".tox", "dist", "build", "htmlcov")

# Получаем список файлов из git (учитывает .gitignore)
$gitFiles = git ls-files --cached --others --exclude-standard 2>$null

if ($gitFiles) {
    # git доступен — используем его список
    $files = $gitFiles | Where-Object {
        $f = $_
        ($extensions | ForEach-Object {
            $pattern = $_.Replace("*", "")
            if ($_ -match '^\*\.') { $f -like $_ }
            elseif ($_ -match 'Dockerfile') { $f -like "*Dockerfile*" }
            else { $false }
        }) -contains $true
    }
} else {
    # git недоступен — ручная фильтрация
    $excludePattern = ($excludeDirs | ForEach-Object { [regex]::Escape($_) }) -join '|'
    $files = @()
    foreach ($ext in $extensions) {
        $found = Get-ChildItem -Path . -Filter $ext -Recurse -File -ErrorAction SilentlyContinue |
            Where-Object { $_.FullName -notmatch $excludePattern }
        $files += $found | ForEach-Object {
            $_.FullName.Substring((Get-Location).Path.Length + 1)
        }
    }
}

# Очищаем выходной файл
"" | Set-Content $outFile -Encoding UTF8

$separator = "=" * 80

foreach ($file in ($files | Sort-Object)) {
    if (-not (Test-Path $file)) { continue }
    # Пропускаем бинарные и пустые
    $size = (Get-Item $file -ErrorAction SilentlyContinue).Length
    if ($size -eq 0 -or $size -gt 500000) { continue }

    Add-Content $outFile $separator -Encoding UTF8
    Add-Content $outFile "FILE: $file" -Encoding UTF8
    Add-Content $outFile $separator -Encoding UTF8
    Get-Content $file -Raw -Encoding UTF8 | Add-Content $outFile -Encoding UTF8
    Add-Content $outFile "`n" -Encoding UTF8
}

Write-Host "Done! Output: $outFile"
Write-Host "Files collected: $(($files | Measure-Object).Count)"