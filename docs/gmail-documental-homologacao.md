# Gmail documental — homologação protegida e ativação explícita

## Estado da entrega

O diagnóstico informado era correto para `iniciar-central-isolada.cjs`: demonstração
sem login, forçando SIMULADO e sem conta externa. Esse iniciador permanece separado.
Para homologação existe agora **`scripts/iniciar-homologacao-gmail.ps1`**.

**Código e caminho de ativação preparados; Gmail externo ainda não homologado.**
Nenhum e-mail externo foi enviado. Foram usados somente diretórios/bancos novos,
arquivos sintéticos e destinatários `example.invalid` nos testes.

### Correção comprovada no backend

Conferências documentais anteriores vinculavam remetente, destinatário, mensagem e
arquivos, mas não identificavam host, usuário, método e versão da autorização SMTP.
Uma conferência REAL poderia sobreviver a uma troca desses parâmetros no reinício.

Novas conferências usam **snapshot 3**, com assinatura de configuração **sem segredos**.
Host/porta/usuário/remetente/autenticação, modo, opt-in, segurança e
`rotabrasil.email.smtp.credencial-versao` integram a assinatura. Não há senha, token,
hash de senha/token ou caminho de credencial nela. Mudar identidade/autorização exige
nova conferência. O identificador da credencial é não secreto e obrigatório para
prontidão REAL. Renovar o access token da mesma autorização não muda essa versão.

Históricos de snapshots 1/2 continuam legíveis; uma conferência antiga sem assinatura
precisa ser refeita. Recuperar a MESMA tentativa já persistida continua idempotente,
inclusive após mudança de configuração. Nenhuma migração publicada foi alterada;
esquema continua V15 e não foi necessário criar coluna nova.

### Estados visíveis separados

`/api/envio-documental` informa separadamente:

- `remetenteSalvo`: preferência salva por ADMIN, não autenticação;
- `remetenteSmtpConfigurado`: remetente externo usado pelo transporte;
- `credencialDisponivel`: presença local/validade declarada do arquivo OAuth2,
  **não** validação no Google;
- `transporteAtivado`, `modo`, `sessaoProtegida`;
- `confirmacaoRealDisponivel`: prontidão local, não prova de entrega;
- `autenticacaoVerificada` e `autenticacaoComprovadaEm`: somente evidência histórica
  de ACEITO_SMTP REAL com a mesma assinatura e versão de autorização. Não atribui
  simulações, registros antigos ou outra conta à configuração atual; não garante
  validade futura, recebimento ou leitura. Consultar o diagnóstico não chama SMTP.

A Central e Configurações mostram essas diferenças. Prévia/conferência continua
possível sem SMTP e sem chamar transporte. Confirmação preserva papéis, CSRF,
integridade, reserva de tentativa antes do transporte, clique duplo, concorrência,
histórico e os estados SIMULADO, ACEITO_SMTP, FALHA e DESCONHECIDO.

## Artefato validado — não precisa repetir o build

```text
C:/Users/florb/AppData/Local/Temp/opencode/rotabrasil-gmail-protegido-jar/rota-brasil-0.0.1-SNAPSHOT.jar
```

Compilação: **2026-11-13T23:02:08Z**. Contrato da interface:
`documental-conferencia-v2` (independente da versão 3 do snapshot persistido).
SHA-256:

```text
244e13f43bf62ee7cfa307a2e258a6816b81f4012da55f44194b3af5ed4f55c0
```

O JAR foi realmente iniciado pelo novo PowerShell, no perfil `operacao`, com HTTPS,
autenticação, papéis/CSRF, cookies Secure/HttpOnly, Flyway desligado e validação V15.
O teste exercitou credencial ausente, presente em configtree e modo REAL com opt-ins
explícitos e credencial **fictícia**: preparação/conferência funcionaram, nenhuma
tentativa de SMTP foi criada. Confirmação é validada em contexto HTTPS com double,
nunca contra o Google nesta tarefa.

## 1. Preparar uma homologação NOVA

Execute no PowerShell, na raiz do repositório, com Java 25, `keytool` e Node para
os testes. O backend/iniciador não exige Node. Use a conta Windows que executará Java.

```powershell
$Jar = 'C:/Users/florb/AppData/Local/Temp/opencode/rotabrasil-gmail-protegido-jar/rota-brasil-0.0.1-SNAPSHOT.jar'
(Get-FileHash -Algorithm SHA256 -LiteralPath $Jar).Hash
$Pai = Join-Path $env:LOCALAPPDATA 'RotaBrasilHomologacoes'
New-Item -ItemType Directory -Force -Path $Pai | Out-Null
$Homolog = Join-Path $Pai 'gmail-01'
powershell.exe -NoProfile -ExecutionPolicy RemoteSigned -File scripts/iniciar-homologacao-gmail.ps1 -Acao Preparar -Jar $Jar -Diretorio $Homolog -Porta 8444 -Administrador admin-homologacao
```

`RemoteSigned` limita-se ao processo que executa o script local revisado; não altera
a política global. Políticas corporativas que proíbam execução devem ser tratadas
pelo administrador da máquina, sem contornar sua política.

A preparação recusa QUALQUER diretório já existente e local dentro do checkout.
A CLI offline cria somente essa base nova, aplica V1–V15 e não abre servidor,
Spring ou SMTP. Não há migração de banco operacional. São criados:

```text
gmail-01/
  banco/                 H2 novo, WRITE_DELAY=0
  acervo/                documentos importados somente desta homologação
  exportacao/            cópias sintéticas
  config/
    runtime.properties   parâmetros locais não secretos
    documental.properties modelo documental editável externamente
    servidor.p12         chave TLS local protegida pela ACL
    servidor-publico.cer certificado público, sem chave privada
  segredos/              arquivos configtree, ACL restrita à conta Windows criadora
  sinteticos/cte-sintetico.xml
  identidade.json        SHA do JAR, esquema e tipo da instância; sem credenciais
```

A ACL é restrita antes de criar segredos. O iniciador não para processos, recusa
portas 8080/8081 e portas ocupadas, fixa loopback e vincula a instância ao SHA do JAR.
Não cria túnel, serviço Windows, deploy ou acesso à internet para o painel.

## 2. Primeiro administrador BCrypt externo

No mesmo terminal interativo, gere o hash **direto no arquivo protegido**. A senha
é solicitada sem eco, nunca como argumento nem em script. Escolha uma senha local
própria; não informe senha/hash no chat.

```powershell
java '-Dloader.main=br.com.rotabrasil.manutencao.GerarHashSenha' -cp $Jar org.springframework.boot.loader.launch.PropertiesLauncher --arquivo "$Homolog/segredos/rotabrasil.seguranca.admin-hash"
```

Exige 12–72 bytes UTF-8 e arquivo novo. O primeiro ADMIN é criado pelo bootstrap
existente com esse BCrypt; o login usa a senha digitada, não o hash. Alterar o
arquivo depois não redefine uma conta já criada: use a administração protegida
para redefinição. O arquivo não integra banco comum de configurações nem Git.

## 3. Configurar a conta documental, sem ativar REAL por engano

```powershell
notepad.exe "$Homolog/config/documental.properties"
```

Preencha localmente `rotabrasil.email.smtp.usuario` e
`rotabrasil.email.smtp.remetente` com a conta Google autorizada. Google Workspace
com domínio próprio é aceito; não há restrição a `@gmail.com`.

Modelos versionados **sem segredos**:

- `config/modelos/gmail-documental.properties.example`: BASIC/senha de aplicativo;
- `config/modelos/gmail-documental-oauth2.properties.example`: alternativa XOAUTH2.

Os dois começam com `rotabrasil.email.modo=SIMULADO`, opt-in falso e segurança ativa.
O inicializador protegido fixa **smtp.gmail.com:587**. STARTTLS obrigatório,
verificação da identidade do servidor SMTP e timeouts são do adaptador existente.

| Propriedade aceita pelo Java | Origem neste procedimento |
|---|---|
| `rotabrasil.email.modo` / `rotabrasil.email.smtp.ativado` | Modelo externo + opção explícita `-AtivarReal` |
| `rotabrasil.email.smtp.host` / `rotabrasil.email.smtp.porta` | Modelo e iniciador Gmail: `smtp.gmail.com:587` |
| `rotabrasil.email.smtp.usuario` / `rotabrasil.email.smtp.remetente` | `config/documental.properties`, preenchidos localmente |
| `rotabrasil.email.smtp.autenticacao` | `BASIC` ou `XOAUTH2` no arquivo externo |
| `rotabrasil.email.smtp.senha` | `segredos/rotabrasil.email.smtp.senha`, importado pelo configtree |
| `rotabrasil.email.smtp.oauth2.token-arquivo` | Caminho externo definido em `documental.properties` |
| `rotabrasil.email.smtp.credencial-versao` | Identificador não secreto em arquivo configtree; muda ao substituir autorização |
| `rotabrasil.email.smtp.timeout-ms` | Arquivo externo, padrão 10000 |
| `rotabrasil.seguranca.ativa` | Imposta como `true` pelo iniciador protegido |
| `rotabrasil.seguranca.admin-hash` | Arquivo configtree gerado interativamente pela CLI |
| `server.ssl.key-store-password` | Arquivo configtree aleatório criado na preparação |

### BASIC: senha de aplicativo, nunca senha normal

É necessário que a conta Google permita senha de aplicativo (normalmente com
verificação em duas etapas); políticas Workspace/Proteção Avançada podem impedi-la.
Se permitida, crie a senha no Google por procedimento autorizado e forneça-a **somente
no prompt local**:

```powershell
powershell.exe -NoProfile -ExecutionPolicy RemoteSigned -File scripts/definir-senha-aplicativo-homologacao.ps1 -Diretorio $Homolog
```

O helper recebe `SecureString`, exige os 16 caracteres sem espaços de apresentação,
grava somente `segredos/rotabrasil.email.smtp.senha` e renova o identificador não
secreto `segredos/rotabrasil.email.smtp.credencial-versao`. Não chama SMTP e não
confirma que o Google aceitará a credencial. Não usar a senha normal, mesmo que tenha
16 caracteres. A confirmação do tipo/permissão da conta depende do Google.

**Não acrescente `rotabrasil.email.smtp.senha=` ao arquivo documental:** um valor
vazio nesse arquivo pode sobrescrever o segredo importado. O valor vem do configtree.

### XOAUTH2: alternativa Google, sem renovador inventado

Se senha de aplicativo não for permitida, copie o modelo OAuth2 para o arquivo externo:

```powershell
Copy-Item config/modelos/gmail-documental-oauth2.properties.example "$Homolog/config/documental.properties"
notepad.exe "$Homolog/config/documental.properties"
```

Preencha conta/remetente e `rotabrasil.email.smtp.oauth2.token-arquivo` com caminho
absoluto usando `/` para o JSON protegido. O utilitário Google autorizado deve obter
consentimento para SMTP/escopo `https://mail.google.com/` e escrever `access_token`
e `expires_at` (ISO-8601 UTC), com mais de 60 segundos de validade. `gmail.send`
da Gmail API não substitui esse contrato SMTP. Acesso, consentimento, renovação e
reconexão são externos: **não estão implementados pelo painel**.

O JSON é limitado a 32 KiB e pode informar `authorization_state`/`renewal_managed`.
Token ausente, inválido, expirado ou autorização pendente/reconexão bloqueiam o
transporte, sem fallback para BASIC. Restrinja o arquivo à conta Java, fora do Git,
acervo e backup documental. Não cole token em formulário ou argumento.

Ao substituir conta/credencial/consentimento, grave uma nova versão não secreta:

```powershell
[IO.File]::WriteAllText("$Homolog/segredos/rotabrasil.email.smtp.credencial-versao", [Guid]::NewGuid().ToString('N'), (New-Object Text.UTF8Encoding($false)))
```

Renovação rotineira do mesmo access token não exige mudar a versão. O arquivo OAuth2
é relido no uso; propriedades e senha de aplicativo exigem reinício do processo.

## 4. Confiar no TLS local e iniciar protegido

O certificado local vale 30 dias e não é instalado silenciosamente. Confira seu
thumbprint no computador onde foi gerado e, se autorizado, confie **somente nesse
certificado público** no usuário Windows que abre o navegador:

```powershell
$Cert = Get-PfxCertificate -FilePath "$Homolog/config/servidor-publico.cer"
$Cert | Select-Object Subject, Thumbprint, NotAfter
Import-Certificate -FilePath "$Homolog/config/servidor-publico.cer" -CertStoreLocation Cert:\CurrentUser\Root
powershell.exe -NoProfile -ExecutionPolicy RemoteSigned -File scripts/iniciar-homologacao-gmail.ps1 -Acao Iniciar -Jar $Jar -Diretorio $Homolog -Porta 8444
```

Esse início mantém **proteção completa** e apenas não autoriza SMTP ainda. Não é o
iniciador de demonstração. Abra **https://localhost:8444/login** e entre com o ADMIN
externo. ADMIN pode salvar o remetente autorizado em Configurações; OPERADOR pode
preparar/confirmar quando autorizado; CONSULTA não altera nem confirma.

No navegador autenticado, abra em abas da **mesma porta/origem**:

- `https://localhost:8444/api/versao`: compilação acima, contrato e hashes;
- `https://localhost:8444/api/envio-documental`: provedor GMAIL, sessãoProtegida=true,
  presença da credencial e bloqueios reais. Resposta 401 sem login é esperada.

Importe pelo painel somente `$Homolog/sinteticos/cte-sintetico.xml`, selecione o
documento, informe destinatário explicitamente e gere a conferência. Sem credencial,
a preparação funciona e o diagnóstico a identifica como indisponível. Não envie
endereços `example.invalid` ao Google; eles são exclusivos dos testes controlados.

## 5. Ativação REAL futura — nenhuma mensagem automática

Somente após autorização externa, credencial/consentimento provisionados, remetente
salvo alinhado ao SMTP e sessão protegida, edite **localmente** estas duas linhas:

```properties
rotabrasil.email.modo=REAL
rotabrasil.email.smtp.ativado=true
```

Pare somente a homologação com Ctrl+C. Reinicie explicitamente:

```powershell
powershell.exe -NoProfile -ExecutionPolicy RemoteSigned -File scripts/iniciar-homologacao-gmail.ps1 -Acao Iniciar -Jar $Jar -Diretorio $Homolog -Porta 8444 -AtivarReal
```

Sem a opção e sem os dois opt-ins no arquivo, não há ativação REAL. Salvar o remetente
não muda esses valores. O comando continua com perfil operacao, TLS, autenticação,
CSRF, H2 Console desligado, Flyway desligado e `ddl-auto=validate`, todos impostos
pelo iniciador. O arquivo configtree é importado explicitamente pelo Java; nenhum
segredo é passado em argumentos, frontend, logs ou propriedades comuns no banco.

Se o início REAL for recusado por credencial/token ausente, inválido ou expirado,
não altere TLS/login/CSRF: inicie novamente **sem `-AtivarReal`** para consultar os
bloqueios na mesma homologação protegida, provisione/renove a autorização externamente
e reinicie explicitamente depois. O backend não faz conexão de teste na inicialização.

`rotabrasil.teste-email.gmail.*` e suas variáveis `ROTABRASIL_GMAIL_*` pertencem ao
teste administrativo. `rotabrasil.notificacoes.*` pertence ao motorista. **Nenhum
desses canais configura ou autentica a Central documental.**

### Uma única tentativa sintética, em tarefa futura autorizada

1. Use a base isolada acima e somente o XML explicitamente sintético fornecido.
2. Escolha uma caixa de e-mail controlada e autorizada, informando o endereço na
   Central localmente. Não extrair destinatário do XML nem inferir a partir do cliente.
3. Gere **nova conferência REAL**, depois da ativação. Apresente para aprovação
   específica os dados efetivamente exibidos: remetente, destinatário, assunto,
   texto, nome/tipo/tamanho/SHA-256 do único anexo sintético. Não substitua por descrição genérica.
4. Após essa aprovação, marque a conferência e clique **uma vez** em Confirmar envio.
5. Consulte a tentativa persistida. ACEITO_SMTP é aceite, não recebimento/leitura.
   Confira a caixa controlada (inclusive spam), a mensagem e o anexo, anotando a
   evidência de recebimento separadamente. Não declare homologação sem isso.
6. Resposta perdida: recupere a MESMA confirmação para consultar o resultado, sem
   novo transporte. DESCONHECIDO: não repetir automaticamente. Reenvio deliberado
   exige consulta humana e nova conferência vinculada à tentativa anterior.

## Validação executada nesta tarefa

- **422 testes JUnit, 421 aprovados, 1 ignorado (symlink Windows), zero falhas/erros**.
  Inclui TesteGmail, TransporteSmtp, prontidão, autorização, Central, assinatura da
  configuração, compatibilidade histórica e preparo offline exclusivo de base nova.
- Navegador REAL com **double**: HTTPS com certificado sintético fixado, login,
  cookie Secure/HttpOnly, CSRF, permissões, divergência de remetente e recuperação,
  conferência/confirmação, resposta perdida, clique duplo e desconhecido sem repetição.
  Nenhuma chamada ao JavaMailSender nesse contexto; dois acionamentos do double.
- `testar-homologacao-gmail.cjs` iniciou o **JAR de produção pelo PowerShell** em três
  fases: credencial ausente, credencial fictícia em configtree e REAL com opt-ins
  explícitos + credencial fictícia. Nesta última fase só preparou/conferiu e verificou
  a exigência do checkbox; **não clicou em Confirmar e houve zero tentativas SMTP**.
- O teste comparou logs com os valores sintéticos de senha/hash/TLS e comprovou que
  não foram registrados. Arquivos e ACL foram criados fora do checkout. Nenhuma
  confiança global do Windows foi alterada pelo teste: o cliente confiou somente no
  certificado público sintético da própria fixture, com HTTPS e SAN corretos.
- Após o último ajuste de texto de evidência histórica, reempacotamento e os três
  cenários do iniciador foram revalidados; os **6 testes** da Central HTTPS/double
  foram novamente aprovados. O SHA acima é o desse artefato final.
- JAR, horário e hashes em `rotabrasil-gmail-homologacao-ui/identidade.json` e
  `resultado-{ausente,presente,real-sem-envio}.json`. Capturas `protegida-*.png` na mesma pasta.
- Logs externos: `rotabrasil-gmail-protegido-test.log`,
  `rotabrasil-gmail-protegido-package.log`, `rotabrasil-gmail-tls-double/resultado-real-double.json`.
  Tudo sob `C:/Users/florb/AppData/Local/Temp/opencode/`.

Nenhum banco operacional foi acessado, migrado ou substituído, nenhum e-mail externo
foi enviado e nenhum serviço foi exposto à internet. Conta Google, autorização,
credencial válida, conectividade e recebimento efetivo continuam dependências externas.
