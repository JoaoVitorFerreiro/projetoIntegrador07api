[CmdletBinding()]
param([Parameter(Mandatory=$true)][string]$Diretorio)
$ErrorActionPreference='Stop'
$root=[IO.Path]::GetFullPath($Diretorio)
if(-not (Test-Path -LiteralPath (Join-Path $root '.rotabrasil-homologacao') -PathType Leaf)) { throw 'Use somente a homologação preparada pelo iniciador protegido.' }
$seguro=Read-Host 'Senha de APLICATIVO Google autorizada, 16 caracteres (não a senha normal)' -AsSecureString
$bstr=[IntPtr]::Zero
$temporario=$null
try {
    $bstr=[Runtime.InteropServices.Marshal]::SecureStringToBSTR($seguro)
    $texto=[Runtime.InteropServices.Marshal]::PtrToStringBSTR($bstr)
    if($texto -notmatch '^[A-Za-z0-9]{16}$') { throw 'Formato esperado: 16 caracteres sem espaços. Isso não verifica autenticação no Google.' }
    $utf8=New-Object Text.UTF8Encoding($false)
    $destino=Join-Path $root 'segredos/rotabrasil.email.smtp.senha'
    $temporario=$destino+'.novo-'+[Guid]::NewGuid().ToString('N')
    [IO.File]::WriteAllText($temporario,$texto,$utf8);Move-Item -LiteralPath $temporario -Destination $destino -Force
    [IO.File]::WriteAllText((Join-Path $root 'segredos/rotabrasil.email.smtp.credencial-versao'),[Guid]::NewGuid().ToString('N'),$utf8)
    Write-Host 'Credencial gravada somente no diretório externo protegido. Reinicie a homologação e gere nova conferência. Nenhum teste SMTP foi executado.'
} finally { if($temporario -and (Test-Path -LiteralPath $temporario)) { Remove-Item -LiteralPath $temporario };if($bstr -ne [IntPtr]::Zero) { [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($bstr) };$texto=$null;$seguro.Dispose() }
