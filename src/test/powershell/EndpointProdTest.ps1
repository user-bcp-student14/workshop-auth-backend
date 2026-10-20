param(
    [Parameter(Mandatory = $true)]
    [string] $JavaExecutable
)

$ErrorActionPreference = 'Stop'
$root = (Resolve-Path (Join-Path $PSScriptRoot '..\..\..')).Path
$temporary = Join-Path $root ('target\endpoint-prod-' + [guid]::NewGuid().ToString('N'))
$null = New-Item -ItemType Directory -Path $temporary
$listener = [System.Net.Sockets.TcpListener]::new([System.Net.IPAddress]::Loopback, 0)
$listener.Start()
$port = $listener.LocalEndpoint.Port
$listener.Stop()
$base = "http://127.0.0.1:$port"
$process = $null
$variables = @{
    AUTH_TOKEN_PRIVATE_KEY_PATH = (Join-Path $temporary 'key.pem')
    AUTH_TOKEN_ISSUER = 'prod-test-issuer'
    AUTH_TOKEN_AUDIENCE = 'prod-test-audience'
    AUTH_CORS_ORIGINS = 'https://client.example.test'
    AUTH_DB_URL = ('jdbc:sqlite:' + (Join-Path $temporary 'test.db'))
    AUTH_PASSWORD_COST = '4'
    AUTH_BOOTSTRAP_USERNAME = 'prod-test-user'
    AUTH_BOOTSTRAP_PASSWORD = 'Prod-test-only!'
}
$previous = @{}

function Assert-Response {
    param([string] $Name, [string] $Path, [string] $Method, [string] $Body,
          [string] $ContentType, [string] $Accept, [int] $Status, [bool] $EmptyBody)
    $response = Invoke-WebRequest ($base + $Path) -Method $Method -Body $Body `
        -ContentType $ContentType -Headers @{ Accept = $Accept } -SkipHttpErrorCheck
    if ([int]$response.StatusCode -ne $Status) {
        throw "$Name expected HTTP $Status, got $($response.StatusCode)"
    }
    if ($EmptyBody -and $response.RawContentLength -ne 0) {
        throw "$Name expected an empty production response"
    }
    Write-Output "PASS $Name HTTP $Status"
}

try {
    $rsa = [System.Security.Cryptography.RSA]::Create(2048)
    try {
        [System.IO.File]::WriteAllText($variables.AUTH_TOKEN_PRIVATE_KEY_PATH, $rsa.ExportPkcs8PrivateKeyPem())
    } finally {
        $rsa.Dispose()
    }
    foreach ($name in $variables.Keys) {
        $previous[$name] = [Environment]::GetEnvironmentVariable($name, 'Process')
        [Environment]::SetEnvironmentVariable($name, $variables[$name], 'Process')
    }
    $process = Start-Process -FilePath $JavaExecutable -WorkingDirectory $root `
        -ArgumentList @("-Dquarkus.http.port=$port", '-Dquarkus.http.host=127.0.0.1',
                        '-jar', 'target\quarkus-app\quarkus-run.jar') `
        -RedirectStandardOutput (Join-Path $temporary 'stdout.log') `
        -RedirectStandardError (Join-Path $temporary 'stderr.log') -PassThru -NoNewWindow
    $ready = $false
    for ($attempt = 0; $attempt -lt 60; $attempt++) {
        if ($process.HasExited) { throw 'Production fixture exited before becoming ready' }
        try {
            $health = Invoke-WebRequest "$base/q/health" -TimeoutSec 1 -SkipHttpErrorCheck
            $ready = [int]$health.StatusCode -eq 200
        } catch [System.Net.Http.HttpRequestException] {
            $ready = $false
        } catch [System.Threading.Tasks.TaskCanceledException] {
            $ready = $false
        }
        if ($ready) { break }
        Start-Sleep -Milliseconds 500
    }
    if (-not $ready) { throw 'Production fixture did not become ready' }
    Assert-Response 'malformed JSON' '/auth/login' 'POST' '{' 'application/json' 'application/json' 400 $true
    Assert-Response 'object username' '/auth/login' 'POST' '{"username":{},"password":"test"}' 'application/json' 'application/json' 400 $true
    Assert-Response 'unsupported method' '/auth/login' 'GET' '' 'application/json' 'application/json' 405 $true
    Assert-Response 'unacceptable response' '/auth/login' 'POST' '{}' 'application/json' 'text/plain' 406 $true
    Assert-Response 'unsupported content' '/auth/login' 'POST' 'test' 'text/plain' 'application/json' 415 $true
    Write-Output 'RESULT: 5 passed, 0 failed (production HTTP bodies)'
} finally {
    if ($null -ne $process -and -not $process.HasExited) {
        Stop-Process -Id $process.Id
        $process.WaitForExit()
    }
    foreach ($name in $previous.Keys) {
        [Environment]::SetEnvironmentVariable($name, $previous[$name], 'Process')
    }
    foreach ($file in (Get-ChildItem -LiteralPath $temporary -File)) {
        Remove-Item -LiteralPath $file.FullName
    }
    Remove-Item -LiteralPath $temporary
}
