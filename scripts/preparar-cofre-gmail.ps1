param(
    [Parameter(Mandatory=$true)][string]$DiretorioChave,
    [Parameter(Mandatory=$true)][string]$DiretorioCofre,
    [Parameter(Mandatory=$true)][string]$ContaServico
)
$ErrorActionPreference='Stop'
$repo=[IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..')).TrimEnd('\')
$chave=[IO.Path]::GetFullPath($DiretorioChave).TrimEnd('\')
$cofre=[IO.Path]::GetFullPath($DiretorioCofre).TrimEnd('\')
if(-not [IO.Path]::IsPathRooted($DiretorioChave) -or -not [IO.Path]::IsPathRooted($DiretorioCofre)) { throw 'Use caminhos absolutos.' }
foreach($p in @($chave,$cofre)) {
    if($p -eq $repo -or $p.StartsWith($repo+'\',[StringComparison]::OrdinalIgnoreCase) -or (Test-Path -LiteralPath $p)) { throw 'Use dois diretórios NOVOS fora do checkout.' }
    $pai=Split-Path -Parent $p
    if(-not (Test-Path -LiteralPath $pai -PathType Container) -or ((Get-Item -LiteralPath $pai).Attributes -band [IO.FileAttributes]::ReparsePoint)) { throw 'O pai deve existir e não pode ser redirecionado.' }
}
if($chave -eq $cofre -or $chave.StartsWith($cofre+'\',[StringComparison]::OrdinalIgnoreCase) -or $cofre.StartsWith($chave+'\',[StringComparison]::OrdinalIgnoreCase)) { throw 'Chave e cofre precisam de diretórios separados.' }
$atual=[Security.Principal.WindowsIdentity]::GetCurrent().User
$servico=(New-Object Security.Principal.NTAccount($ContaServico)).Translate([Security.Principal.SecurityIdentifier])
function CriarRestrito([string]$p,[string]$direito) {
    New-Item -ItemType Directory -Path $p | Out-Null
    $acl=New-Object Security.AccessControl.DirectorySecurity
    $acl.SetOwner($atual);$acl.SetAccessRuleProtection($true,$false)
    $acl.AddAccessRule((New-Object Security.AccessControl.FileSystemAccessRule($atual,'FullControl','ContainerInherit,ObjectInherit','None','Allow')))
    if($servico -ne $atual) { $acl.AddAccessRule((New-Object Security.AccessControl.FileSystemAccessRule($servico,$direito,'ContainerInherit,ObjectInherit','None','Allow'))) }
    Set-Acl -LiteralPath $p -AclObject $acl
}
CriarRestrito $chave 'ReadAndExecute'
CriarRestrito $cofre 'Modify'
$bytes=New-Object byte[] 32
$rng=[Security.Cryptography.RandomNumberGenerator]::Create()
try { $rng.GetBytes($bytes);[IO.File]::WriteAllBytes((Join-Path $chave 'aes256.key'),$bytes) }
finally { [Array]::Clear($bytes,0,$bytes.Length);$rng.Dispose() }
Write-Host 'Chave AES-256 provisionada sem exibir seu conteúdo. Cofre vazio e ACLs restritas preparados. Nenhuma conexão ou envio executado.'
