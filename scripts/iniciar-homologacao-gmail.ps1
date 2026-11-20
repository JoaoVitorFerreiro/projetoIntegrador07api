[CmdletBinding()]
param(
    [Parameter(Mandatory=$true)][ValidateSet('Preparar','Iniciar')][string]$Acao,
    [Parameter(Mandatory=$true)][string]$Jar,
    [Parameter(Mandatory=$true)][string]$Diretorio,
    [ValidateRange(1024,65535)][int]$Porta=8444,
    [ValidatePattern('^[A-Za-z0-9._-]{3,100}$')][string]$Administrador='admin-homologacao',
    [ValidateSet('SMTP','GMAIL_API')][string]$Transporte='SMTP',
    [ValidateSet('','/rota')][string]$Contexto='',
    [switch]$GerarAcessoLocal,
    [switch]$SegundoPlano,
    [switch]$AtivarReal
)
$ErrorActionPreference='Stop'
$repo=[IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..')).TrimEnd('\','/')
$jarPath=(Resolve-Path -LiteralPath $Jar).Path
$root=[IO.Path]::GetFullPath($Diretorio).TrimEnd('\','/')
if(-not [IO.Path]::IsPathRooted($Diretorio) -or $root.Contains(';') -or $root.Equals($repo,[StringComparison]::OrdinalIgnoreCase) -or $root.StartsWith($repo+[IO.Path]::DirectorySeparatorChar,[StringComparison]::OrdinalIgnoreCase)) { throw 'Use diretório absoluto de homologação fora do repositório.' }
if($Porta -in @(8080,8081)) { throw 'Use porta distinta das instalações existentes, por exemplo 8444.' }
$utf8=New-Object Text.UTF8Encoding($false)
function Gravar([string]$arquivo,[string]$texto) { [IO.File]::WriteAllText($arquivo,$texto,$utf8) }
function CaminhoJava([string]$p) { return $p.Replace('\','/') }
if($Acao -eq 'Preparar') {
    if($SegundoPlano) { throw 'SegundoPlano é exclusivo de Iniciar.' }
    if($AtivarReal) { throw 'Preparar não ativa REAL.' }
    if(Test-Path -LiteralPath $root) { throw 'Diretório já existe. A preparação somente aceita diretório NOVO.' }
    if($Transporte -eq 'GMAIL_API' -and (Test-Path -LiteralPath ($root+'-chaves-google'))) { throw 'Diretório de chave já existe; escolha uma homologação NOVA.' }
    if(-not (Test-Path -LiteralPath (Split-Path $root -Parent) -PathType Container)) { throw 'Crie primeiro a pasta pai destinada à homologação.' }
    & java '-Dloader.main=br.com.rotabrasil.manutencao.PrepararHomologacao' -cp $jarPath org.springframework.boot.loader.launch.PropertiesLauncher --diretorio $root
    if($LASTEXITCODE -ne 0) { throw 'Preparação offline recusada.' }
    # Restringir antes de criar qualquer segredo; Java será executado por esta mesma conta.
    $sid=[Security.Principal.WindowsIdentity]::GetCurrent().User
    $acl=New-Object Security.AccessControl.DirectorySecurity
    $acl.SetOwner($sid);$acl.SetAccessRuleProtection($true,$false)
    $regra=New-Object Security.AccessControl.FileSystemAccessRule($sid,'FullControl','ContainerInherit,ObjectInherit','None','Allow')
    $acl.AddAccessRule($regra);Set-Acl -LiteralPath $root -AclObject $acl
    # Pastas foram criadas pela CLI antes da ACL: aplicar herança restrita explicitamente.
    Get-ChildItem -LiteralPath $root -Directory | ForEach-Object { $a=Get-Acl -LiteralPath $_.FullName;$a.SetAccessRuleProtection($false,$false);Set-Acl -LiteralPath $_.FullName -AclObject $a }
    $bytes=New-Object byte[] 32;$rng=[Security.Cryptography.RandomNumberGenerator]::Create();$rng.GetBytes($bytes);$rng.Dispose()
    $tls=[Convert]::ToBase64String($bytes)
    Gravar (Join-Path $root 'segredos/server.ssl.key-store-password') $tls
    $anterior=$env:ROTABRASIL_HOMOLOGACAO_TLS
    try {
        $env:ROTABRASIL_HOMOLOGACAO_TLS=$tls
        & keytool -genkeypair -alias homologacao -keyalg RSA -keysize 2048 -storetype PKCS12 -keystore (Join-Path $root 'config/servidor.p12') -storepass:env ROTABRASIL_HOMOLOGACAO_TLS -keypass:env ROTABRASIL_HOMOLOGACAO_TLS -dname 'CN=localhost' -ext 'SAN=ip:127.0.0.1,dns:localhost' -validity 30 -noprompt
        if($LASTEXITCODE -ne 0) { throw 'Falha ao criar certificado local.' }
        & keytool -exportcert -rfc -alias homologacao -keystore (Join-Path $root 'config/servidor.p12') -storepass:env ROTABRASIL_HOMOLOGACAO_TLS -file (Join-Path $root 'config/servidor-publico.cer')
        if($LASTEXITCODE -ne 0) { throw 'Falha ao exportar certificado público.' }
    } finally { $env:ROTABRASIL_HOMOLOGACAO_TLS=$anterior;$tls=$null }
    $base=CaminhoJava $root
    if($Transporte -eq 'GMAIL_API') {
        & (Join-Path $PSScriptRoot 'preparar-cofre-gmail.ps1') -DiretorioChave ($root+'-chaves-google') -DiretorioCofre (Join-Path $root 'estado-google') -ContaServico ([Security.Principal.WindowsIdentity]::GetCurrent().Name)
        $chave=CaminhoJava ($root+'-chaves-google/aes256.key')
        $publica="https://localhost:$Porta$Contexto"
        Gravar (Join-Path $root 'config/documental.properties') "rotabrasil.email.transporte=GMAIL_API`nrotabrasil.email.modo=SIMULADO`nrotabrasil.gmail.envio-ativado=false`nrotabrasil.gmail.client-id=`nrotabrasil.gmail.url-publica=$publica`nrotabrasil.gmail.redirect-uri=$publica/api/configuracoes/gmail/callback`nrotabrasil.gmail.cofre-arquivo=$base/estado-google/conta.enc`nrotabrasil.gmail.chave-arquivo=$chave`n"
        # Arquivo vazio significa AUSENTE; não inventar client secret para habilitar o botão.
        Gravar (Join-Path $root 'segredos/rotabrasil.gmail.client-secret') ''
    } else { Copy-Item -LiteralPath (Join-Path $repo 'config/modelos/gmail-documental.properties.example') -Destination (Join-Path $root 'config/documental.properties') }
    Gravar (Join-Path $root 'config/runtime.properties') "rotabrasil.seguranca.admin-usuario=$Administrador`nserver.ssl.key-store=file:$base/config/servidor.p12`nserver.ssl.key-store-type=PKCS12`n"
    Gravar (Join-Path $root 'identidade.json') (@{schema=16;jarSha256=(Get-FileHash -Algorithm SHA256 -LiteralPath $jarPath).Hash;porta=$Porta;transporte=$Transporte;contexto=$Contexto;tipo='HOMOLOGACAO_SINTETICA_PROTEGIDA'}|ConvertTo-Json)
    if($GerarAcessoLocal) {
        & java '-Dloader.main=br.com.rotabrasil.manutencao.GerarHashSenha' -cp $jarPath org.springframework.boot.loader.launch.PropertiesLauncher --gerar-homologacao $root
        if($LASTEXITCODE -ne 0) { throw 'Não foi possível provisionar o acesso local.' }
    }
    Write-Host 'Base V16 nova preparada offline, ACL restrita e TLS local criado. Nenhum servidor ou e-mail iniciado.'
    if($GerarAcessoLocal) { Write-Host "Usuário: $Administrador . Abra localmente $root/segredos/acesso-admin-local.txt para consultar a senha aleatória. Não compartilhe seu conteúdo." }
    else { Write-Host 'Próximo passo: gerar segredos/rotabrasil.seguranca.admin-hash pela CLI GerarHashSenha --arquivo.' }
    if($Transporte -eq 'GMAIL_API') { Write-Host "Integração Google ainda não configurada: preencha client ID em config/documental.properties e client secret no arquivo privado. Cadastre o redirect URI exato: https://localhost:$Porta$Contexto/api/configuracoes/gmail/callback" }
    return
}
if($GerarAcessoLocal) { throw 'Geração de acesso somente é permitida em Preparar, com diretório NOVO.' }
if(-not (Test-Path -LiteralPath (Join-Path $root '.rotabrasil-homologacao') -PathType Leaf)) { throw 'Diretório não foi preparado como homologação nova.' }
if((Get-Content -LiteralPath (Join-Path $root '.rotabrasil-homologacao') -Raw).Trim() -ne 'NOVA_BASE_SINTETICA_V16') { throw 'Marcador de homologação inválido; este JAR exige uma base sintética V16 nova.' }
$identidade=Get-Content -LiteralPath (Join-Path $root 'identidade.json') -Raw|ConvertFrom-Json
$preparadoTransporte=if($identidade.transporte) { $identidade.transporte } else { 'SMTP' }
$preparadoContexto=if($identidade.contexto) { $identidade.contexto } else { '' }
if($preparadoTransporte -ne $Transporte -or $preparadoContexto -ne $Contexto -or $identidade.porta -ne $Porta) { throw 'Transporte, contexto ou porta diferem da homologação preparada. Use os parâmetros originais.' }
if($identidade.jarSha256 -ne (Get-FileHash -Algorithm SHA256 -LiteralPath $jarPath).Hash) { throw 'O JAR não corresponde ao artefato que preparou esta homologação. Prepare uma base nova com o JAR validado.' }
foreach($rel in @('banco/rotabrasil.mv.db','config/servidor.p12','config/runtime.properties','config/documental.properties','segredos/server.ssl.key-store-password','segredos/rotabrasil.seguranca.admin-hash')) { if(-not (Test-Path -LiteralPath (Join-Path $root $rel) -PathType Leaf)) { throw "Arquivo local necessário ausente: $rel" } }
Get-Item -LiteralPath $root | ForEach-Object { if($_.Attributes -band [IO.FileAttributes]::ReparsePoint) { throw 'Raiz redirecionada não permitida.' } }
if(Get-NetTCPConnection -State Listen -LocalPort $Porta -ErrorAction SilentlyContinue) { throw 'Porta já está ocupada; nenhum processo foi parado.' }
$doc=Get-Content -LiteralPath (Join-Path $root 'config/documental.properties') -Raw
$optin=if($Transporte -eq 'GMAIL_API') { 'rotabrasil\.gmail\.envio-ativado' } else { 'rotabrasil\.email\.smtp\.ativado' }
if($AtivarReal -and ($doc -notmatch '(?m)^rotabrasil\.email\.modo\s*=\s*REAL\s*$' -or $doc -notmatch ("(?m)^"+$optin+'\s*=\s*true\s*$'))) { throw 'Ativação REAL requer os dois opt-ins explícitos do transporte selecionado no arquivo documental.properties.' }
$base=CaminhoJava $root
$argsJava=@('-Xmx512m','-jar',$jarPath,'--spring.profiles.active=operacao','--spring.config.location=classpath:/application.properties',
    "--spring.config.additional-location=file:$base/config/runtime.properties,file:$base/config/documental.properties",
    "--spring.config.import=configtree:$base/segredos/",'--server.address=127.0.0.1',"--server.port=$Porta",'--server.ssl.enabled=true',
    "--spring.datasource.url=jdbc:h2:file:$base/banco/rotabrasil;IFEXISTS=TRUE;WRITE_DELAY=0",'--spring.datasource.username=sa','--spring.datasource.password=',
    '--spring.flyway.enabled=false','--spring.jpa.hibernate.ddl-auto=validate','--spring.h2.console.enabled=false','--rotabrasil.seguranca.ativa=true',
    '--rotabrasil.banco.exigir-restricoes=true','--spring.web.resources.static-locations=classpath:/static/',
    "--rotabrasil.email.transporte=$Transporte","--server.servlet.context-path=$Contexto",'--server.forward-headers-strategy=none',
    '--server.servlet.session.cookie.secure=true','--server.servlet.session.cookie.http-only=true',
    "--rotabrasil.arquivos.viagens=$base/acervo","--rotabrasil.exportacao.raiz=$base/exportacao",'--rotabrasil.notificacoes.modo=SIMULADO','--rotabrasil.teste-email.modo=SIMULADO')
if($Transporte -eq 'GMAIL_API') { $argsJava+=@('--server.servlet.session.cookie.same-site=lax') }
else { $argsJava+=@('--server.servlet.session.cookie.same-site=strict','--rotabrasil.email.smtp.host=smtp.gmail.com','--rotabrasil.email.smtp.porta=587') }
if($AtivarReal) { $argsJava+=@('--rotabrasil.email.modo=REAL',"--rotabrasil.email.smtp.ativado=$($Transporte -eq 'SMTP')","--rotabrasil.gmail.envio-ativado=$($Transporte -eq 'GMAIL_API')") }
else { $argsJava+=@('--rotabrasil.email.modo=SIMULADO','--rotabrasil.email.smtp.ativado=false','--rotabrasil.gmail.envio-ativado=false') }
Write-Host "Login protegido em https://localhost:$Porta$Contexto/login . Confira o certificado público local antes de confiar nele."
Write-Host 'Nenhuma mensagem é enviada na inicialização. A confirmação documental depende de conferência e ação explícita na Central.'
Push-Location -LiteralPath $root
try {
    if($SegundoPlano) {
        $sufixo=(Get-Date -Format 'yyyyMMdd-HHmmss')+'-'+[Guid]::NewGuid().ToString('N').Substring(0,8)
        $stdout=Join-Path $root "config/servidor-$sufixo.stdout.log";$stderr=Join-Path $root "config/servidor-$sufixo.stderr.log"
        $citados=$argsJava | ForEach-Object { '"'+$_.Replace('"','\"')+'"' }
        $p=Start-Process -FilePath (Get-Command java).Source -ArgumentList $citados -WorkingDirectory $root -WindowStyle Hidden -RedirectStandardOutput $stdout -RedirectStandardError $stderr -PassThru
        Gravar (Join-Path $root 'servidor-em-execucao.json') (@{pidJava=$p.Id;iniciadoEm=$p.StartTime.ToUniversalTime().ToString('o');url="https://localhost:$Porta$Contexto";jarSha256=$identidade.jarSha256;stdout=$stdout;stderr=$stderr}|ConvertTo-Json)
        Write-Host "Processo Java iniciado em segundo plano: PID $($p.Id). Consulte servidor-em-execucao.json e os logs privados; aguarde o HTTPS antes de entrar."
    } else { & java @argsJava; if($LASTEXITCODE -ne 0) { throw 'Backend não iniciou/concluiu corretamente. Confira os requisitos sem publicar segredos.' } }
}
finally { Pop-Location }
