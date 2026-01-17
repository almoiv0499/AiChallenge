# PowerShell скрипт для проверки работы деплоя
# Использование: .\scripts\test-deployment.ps1 [URL]

param(
    [string]$Url = "http://localhost:8080"
)

Write-Host "🧪 Тестирование деплоя на $Url" -ForegroundColor Yellow
Write-Host ""

# Функция для проверки endpoint
function Test-Endpoint {
    param(
        [string]$Endpoint,
        [string]$Description
    )
    
    Write-Host -NoNewline "Проверка $Description... "
    
    try {
        $response = Invoke-WebRequest -Uri "$Url$Endpoint" -UseBasicParsing -ErrorAction Stop
        if ($response.StatusCode -eq 200) {
            Write-Host "✅ OK" -ForegroundColor Green
            $content = $response.Content | ConvertFrom-Json
            Write-Host "   Ответ: $($content | ConvertTo-Json -Compress)" -ForegroundColor Gray
            return $true
        } else {
            Write-Host "❌ FAILED (HTTP $($response.StatusCode))" -ForegroundColor Red
            return $false
        }
    } catch {
        Write-Host "❌ FAILED ($($_.Exception.Message))" -ForegroundColor Red
        return $false
    }
}

# Проверка health endpoint
Test-Endpoint -Endpoint "/api/health" -Description "Health Check"

# Проверка тестового endpoint деплоя
Test-Endpoint -Endpoint "/api/deployment/test" -Description "Deployment Test"

# Проверка версии
Test-Endpoint -Endpoint "/api/version" -Description "Version Info"

Write-Host ""
Write-Host "✅ Все проверки завершены!" -ForegroundColor Green
