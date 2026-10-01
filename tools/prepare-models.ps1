$ErrorActionPreference = 'Stop'
$modelRoot = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..\app\src\main\assets\models'))
New-Item -ItemType Directory -Force -Path $modelRoot | Out-Null
$models = @(
    @{ Name='qwen-tokenizer-12hz-Q4_K_M.gguf'; Size=254974752; Hash='cf3788b4d50aaa665fb6e57c170396aae03a3555fea52d2b5d0cda902d658039' },
    @{ Name='qwen-talker-0.6b-base-Q4_K_M.gguf'; Size=628905056; Hash='4b468ec7b1f62b90ef4ca316c0aa57deadfd54b2cf9651703ea753cedaf04226' }
)
foreach ($model in $models) {
    $modelTarget = Join-Path $modelRoot $model.Name
    if ((Test-Path -LiteralPath $modelTarget) -and (Get-Item -LiteralPath $modelTarget).Length -eq $model.Size -and (Get-FileHash -LiteralPath $modelTarget -Algorithm SHA256).Hash -eq $model.Hash) {
        Write-Output "$($model.Name): verified"
        continue
    }
    $modelPartial = "$modelTarget.download"
    & curl.exe --fail --location --retry 3 --connect-timeout 20 --max-time 600 --continue-at - --output $modelPartial "https://huggingface.co/Serveurperso/Qwen3-TTS-GGUF/resolve/b7ee2e8c7459c3bea99da23e3d178125a7d1713c/$($model.Name)"
    if ($LASTEXITCODE -ne 0) { throw "Download failed for $($model.Name)" }
    if ((Get-Item -LiteralPath $modelPartial).Length -ne $model.Size -or (Get-FileHash -LiteralPath $modelPartial -Algorithm SHA256).Hash -ne $model.Hash) { throw "Model hash/size mismatch: $($model.Name)" }
    Move-Item -LiteralPath $modelPartial -Destination $modelTarget -Force
    Write-Output "$($model.Name): downloaded and verified"
}
