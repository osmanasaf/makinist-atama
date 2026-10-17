# Tümünü doğrular: backend testi, frontend tip/lint/build/test. İlk hatada durur.
# Henüz var olmayan kısım "atlandı" olarak geçilir. Testler kendi PostgreSQL kabını açar (Docker açık olmalı), DB-UP gerekmez.
# Kullanım: .\scripts\dogrula.ps1
# Süre (ölçüm 2026-10-15): backend ~4:40 (2 fork = 2 JVM + 2 PostgreSQL kabı; Docker açık olmalı, ~2 GB boş bellek), frontend adımları ~1 dk (test ~9 sn).
# Ölçek testleri (ayrı, seri, ~10 dk): backend\mvnw.cmd -f backend\pom.xml test -Pyavas. Tek fork için: ... test -DtestForkSayisi=1
$ErrorActionPreference = 'Stop'
$kok = Split-Path -Parent $PSScriptRoot

function Adim([string]$ad, [scriptblock]$komut) {
    Write-Host "==> $ad" -ForegroundColor Cyan
    $sayac = [Diagnostics.Stopwatch]::StartNew()
    # Yerel komutların stderr çıktısı (ör. Mockito uyarısı) hata sayılmasın; yalnız çıkış kodu belirleyici
    $onceki = $ErrorActionPreference
    $ErrorActionPreference = 'Continue'
    & $komut
    $kod = $LASTEXITCODE
    $ErrorActionPreference = $onceki
    Write-Host ("    {0}: {1:m\:ss}" -f $ad, $sayac.Elapsed)
    if ($kod -ne 0) {
        Write-Host "BAŞARISIZ: $ad (çıkış kodu $kod)" -ForegroundColor Red
        exit 1
    }
}

if (Test-Path -LiteralPath "$kok\backend\pom.xml") {
    Adim 'backend test' { & "$kok\backend\mvnw.cmd" -f "$kok\backend\pom.xml" test }
} else {
    Write-Host 'backend: atlandı (henüz yok)'
}

if (Test-Path -LiteralPath "$kok\frontend\package.json") {
    Adim 'frontend type-check' { npm --prefix "$kok\frontend" run type-check }
    # Salt denetim: oxlint + eslint + prettier --check; kaynağı yeniden YAZMAZ (düzeltmek için: npm --prefix frontend run lint:fix)
    Adim 'frontend lint (salt denetim)' { npm --prefix "$kok\frontend" run lint }
    Adim 'frontend build' { npm --prefix "$kok\frontend" run build }
    Adim 'frontend test' { npm --prefix "$kok\frontend" run test:unit -- --run }
} else {
    Write-Host 'frontend: atlandı (henüz yok)'
}

Write-Host 'Doğrulama tamam.' -ForegroundColor Green
