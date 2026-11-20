# Gmail OAuth2 documental — Configurações e Central de envios (V16)

## Homologação protegida pronta — correção de 16/11/2026

A porta **8092** é demonstração sem autenticação: `DESENVOLVIMENTO_LOCAL`, papel
ADMIN local e CSRF vazio não constituem login real. A interface atual detecta esse
estado e mostra **“Conectar Google exige login no ambiente protegido”**, sem consultar
a conta Google nem sugerir sessão expirada. Não utilizar essa porta para OAuth.

O inicializador protegido existente foi ampliado para `GMAIL_API`, mantendo o padrão
SMTP compatível. A instância abaixo já foi preparada e iniciada com o JAR atualizado,
banco sintético V16 separado, autenticação, CSRF, HTTPS, cookies Secure/HttpOnly/Lax,
proxy headers desabilitados, Flyway desligado na execução e envio SIMULADO:

**Login:** `https://localhost:8445/rota/login`

**Usuário:** `admin-homologacao`

A senha é aleatória e está somente no arquivo privado externo
`C:/Users/florb/AppData/Local/Temp/opencode/rotabrasil-google-protegido-v2-homologacao/segredos/acesso-admin-local.txt`.
Abra esse arquivo localmente; não envie seu conteúdo pelo chat. O hash BCrypt é
carregado pelo bootstrap normal, e o login foi validado de fato. Não há senha padrão.

### Comando PowerShell completo da instância correta

Execute na raiz do checkout, em PowerShell. A instância já está ligada: o comando
recusa porta ocupada, sem parar outros processos. Use-o ao reiniciar esta homologação.

```powershell
$Jar = 'C:/Users/florb/AppData/Local/Temp/opencode/rotabrasil-google-protegido-v2-build/rota-brasil-0.0.1-SNAPSHOT.jar'
$Homolog = 'C:/Users/florb/AppData/Local/Temp/opencode/rotabrasil-google-protegido-v2-homologacao'
powershell.exe -NoProfile -ExecutionPolicy RemoteSigned -File scripts/iniciar-homologacao-gmail.ps1 -Acao Iniciar -Jar $Jar -Diretorio $Homolog -Porta 8445 -Transporte GMAIL_API -Contexto /rota -SegundoPlano
notepad.exe "$Homolog/segredos/acesso-admin-local.txt"
```

`servidor-em-execucao.json`, dentro da homologação, registra PID Java, início, hash
do JAR e caminhos dos logs privados. Para parar **somente esta instância**, confira
esse registro e a porta antes de encerrar seu PID. Remova `-SegundoPlano` se quiser
executar no terminal, com Ctrl+C para encerrar. O script também preserva o modo SMTP.

O certificado é local e não foi instalado silenciosamente no Windows. Para confiar
somente no certificado público desta homologação, confira-o e importe-o no usuário
que usará o navegador (não importe a chave privada `.p12`):

```powershell
$Cert = Get-PfxCertificate -FilePath "$Homolog/config/servidor-publico.cer"
$Cert | Select-Object Subject, Thumbprint, NotAfter
Import-Certificate -FilePath "$Homolog/config/servidor-publico.cer" -CertStoreLocation Cert:\CurrentUser\Root
```

### O que ainda falta no Google Cloud

O cofre/chave e a URL local já estão provisionados. Não foram inventadas credenciais:

1. Crie/configure o projeto, habilite Gmail API e a tela de consentimento conforme
   o passo a passo da seção 1. Cadastre a conta responsável como usuário de teste
   quando aplicável. Os escopos são somente `gmail.send` e `userinfo.email`.
2. Crie cliente OAuth **Aplicação da Web** e registre exatamente:
   **`https://localhost:8445/rota/api/configuracoes/gmail/callback`**.
3. Preencha **localmente** `rotabrasil.gmail.client-id` em
   `$Homolog/config/documental.properties` com o client ID desse cliente Web.
4. Grave **localmente** o client secret no arquivo privado
   `$Homolog/segredos/rotabrasil.gmail.client-secret`, que está vazio. Não coloque o
   segredo em argumentos de processo, arquivo versionado ou chat.
5. Reinicie somente esta homologação com o comando acima, entre como ADMIN, abra
   Configurações e clique **Conectar com Google**. O responsável realiza o consentimento.

O redirect URI já está definido no arquivo externo; sua **inscrição no cliente Google
Cloud** ainda depende do administrador. Client ID e client secret estão ausentes.
Nesse estado, o painel mostra **“Integração Google ainda não configurada”**, lista as
propriedades ausentes e mantém a sessão válida. A consulta da conta retorna **200**;
início explícito com CSRF retorna **409**, sem CSRF **403**, e anônimo **401**.
Após conectar, o envio permanece SIMULADO. A ativação REAL e eventual envio externo
continuam sendo uma etapa futura autorizada, com nova conferência e confirmação manual.

Para reproduzir uma base **NOVA**, use outro diretório final inexistente:

```powershell
powershell.exe -NoProfile -ExecutionPolicy RemoteSigned -File scripts/iniciar-homologacao-gmail.ps1 -Acao Preparar -Jar $Jar -Diretorio 'C:/PAI_EXISTENTE/nova-homologacao' -Porta 8445 -Administrador admin-homologacao -Transporte GMAIL_API -Contexto /rota -GerarAcessoLocal
```

Esse modo gera acesso aleatório apenas em homologação marcada como sintética, não
sobrescreve credenciais e cria chave AES fora do diretório de dados/backup comum:
o diretório irmão terminado em `-chaves-google`. O cofre fica em `estado-google/`.

### Validação desta correção

- **456 JUnit: 455 aprovados, 1 ignorado (symlink Windows), zero falhas/erros**.
  Inclui 19 testes OAuth/cofre, 12 HTTP Gmail com navegador e 2 de geração de acesso
  aleatório; preservada a regressão completa e o transporte SMTP controlado.
- Navegador controlado: autorização, callback, conta identificada, Central, renovação,
  desconexão, permissões, CSRF, resposta perdida, modo local sem consulta proibida.
- **JAR real iniciado pelo PowerShell ampliado**: login ADMIN com a senha aleatória
  externa, cookie Lax/Secure/HttpOnly, CSRF não vazio, consulta Google 200 com
  `NAO_CONFIGURADA`, início 409 com CSRF e 403 sem CSRF, sessão ainda 200 após a recusa.
- Interface clara/escura e celular com credenciais ausentes, sem falsa expiração;
  XML sintético importado nessa base e zero tentativas de envio.
- Nenhum envio externo, autorização real Google, exposição de serviço ou acesso a
  banco operacional. O JAR escuta somente em loopback. A demonstração 8092 não foi alterada.

Evidências externas em `C:/Users/florb/AppData/Local/Temp/opencode/`:
`rotabrasil-google-protegido-v2-test.log`, `rotabrasil-google-protegido-v2-package.log`,
`rotabrasil-google-protegido-v2-controlado/` (inclui captura de desenvolvimento local)
e `rotabrasil-google-protegido-v2-jar-ui/resultado.json` com capturas da configuração
ausente no JAR protegido. O teste do JAR é reproduzível por
`scripts/testar-homologacao-google.cjs`; não chama Google e não imprime senha/CSRF.

## O que foi implementado

**ADMIN → Configurações → Conectar com Google → consentimento → retorno → conta
compartilhada conectada → Central → conferir → confirmar manualmente.** Nenhuma
mensagem é enviada pela conexão, pelo callback, pela consulta de estado ou pela
renovação de tokens. Login RotaBrasil, operadores, papéis e CSRF continuam separados.
OPERADOR pode conferir e confirmar; CONSULTA não pode alterar nem confirmar.

O canal é o DOCUMENTAL da Central, selecionado externamente com
`rotabrasil.email.transporte=GMAIL_API`. O padrão compatível continua `SMTP`;
o SMTP manual aparece como configuração avançada separada. Não existe fallback.
`rotabrasil.teste-email.gmail.*` e `rotabrasil.notificacoes.*` **não configuram este canal**.
Guias SMTP anteriores preservam os procedimentos e resultados históricos de suas versões.
O iniciador `iniciar-homologacao-gmail.ps1` suporta SMTP e, explicitamente com
`-Transporte GMAIL_API`, a conexão OAuth documental protegida descrita acima.

### Contratos e garantias

- Authorization Code no backend, PKCE S256 e state de 256 bits, uso único, 10 minutos,
  guardado na sessão ADMIN iniciadora. Outra sessão/ator, expiração, reutilização ou
  mudança concorrente de autorização impedem a conclusão. Reiniciar o backend perde
  os fluxos pendentes e exige iniciar novamente.
- Somente `https://www.googleapis.com/auth/gmail.send` e
  `https://www.googleapis.com/auth/userinfo.email`. O segundo identifica o remetente
  no endpoint Google `oauth2/v2/userinfo`: ID estável, endereço e `verified_email=true`.
  Não se usa OpenID Connect nem ID token; portanto não há nonce OIDC. Nenhum escopo de
  leitura da caixa, contatos, Drive, perfil amplo ou `https://mail.google.com/` é pedido.
- Confere o `scope` efetivamente devolvido pela troca, `token_type`, access token e
  validade. Permissão de envio não é inferida de identidade/login Google.
- `access_type=offline`, `prompt=consent select_account`, sem agregar escopos de outras
  integrações. Google pode não devolver refresh token em autorizações subsequentes:
  só se reaproveita o token anterior da **mesma identidade e cliente**, ainda conectado.
  Conta diferente sem refresh token não substitui a existente. O painel informa a falta.
- Redirect URI fixo deve ser exatamente `url-publica + /api/configuracoes/gmail/callback`.
  URL pública explícita, HTTPS, sem query, fragmento ou usuário; contextos suportados
  nesta versão: raiz e `/rota`. Host/Forwarded recebidos não escolhem o redirect.
- POST de início e desconexão exigem ADMIN e CSRF; callback GET exige ADMIN, sessão,
  state e troca com o verifier PKCE no backend. CSRF não foi desativado para OAuth.
  Cookies Secure/HttpOnly e SameSite=Lax permitem o retorno GET de outro site;
  POSTs continuam exigindo CSRF. Com SameSite=Strict o navegador perde a sessão no
  retorno Google: não troque por `None` nem desligue CSRF para contornar isso.
- `conta`, `permissaoEnvio`, estado de renovação, transporte selecionado/opt-in,
  SIMULADO/REAL, última tentativa e recebimento são apresentados separadamente.
  O endereço não é editável no fluxo API; apenas a identidade principal autorizada é
  remetente. Aliases não têm suporte. PUT do remetente manual é recusado nesse modo.

## 1. Preparar Google Cloud

Faça estes passos localmente no Console Google com o responsável pela instalação.
Não compartilhe client secret, tokens ou arquivos de credencial pelo chat.

1. Crie ou escolha um projeto em [Google Cloud Console](https://console.cloud.google.com/).
   Separe homologação e produção, preferencialmente em projetos/clientes próprios.
2. Em **APIs e serviços → Biblioteca**, habilite **Gmail API** para esse projeto.
3. Em **Google Auth Platform**, preencha **Branding** (nome RotaBrasil da sua instalação,
   contato de suporte, contatos do desenvolvedor e URLs reais exigidas). Configure
   domínio autorizado, política de privacidade e página inicial conforme o público.
4. Em **Audience/Público**, selecione Interno somente se a organização Workspace e
   o público realmente permitirem. Para Externo em teste, cadastre em **Test users**
   o endereço real da conta compartilhada que será autorizada por seu responsável.
   Uma conta Workspace pode depender de liberação pelo administrador do domínio.
5. Em **Data Access/Acesso a dados**, declare exatamente `gmail.send` e
   `userinfo.email`. `gmail.send` é um escopo sensível: enviar não autoriza leitura.
6. Em **Clients → Create Client**, escolha **Web application/Aplicação da Web**.
   Este é um cliente confidencial de backend; não use cliente Desktop, service account,
   senha comum nem fluxo JavaScript implícito. Nenhuma origem JavaScript é necessária
   para este Authorization Code Flow.
7. Cadastre **Authorized redirect URIs** exatos, incluindo esquema, host, porta e
   caminho. Sem curingas, `#` ou barra final extra. Exemplos ilustrativos, sem domínio
   real ou aprovação presumidos:

   | Ambiente | URL pública ilustrativa | Redirect URI exato correspondente |
   |---|---|---|
   | Homologação raiz | `https://homolog.example.invalid` | `https://homolog.example.invalid/api/configuracoes/gmail/callback` |
   | Homologação `/rota` | `https://homolog.example.invalid/rota` | `https://homolog.example.invalid/rota/api/configuracoes/gmail/callback` |
   | Produção `/rota` | `https://operacao.example.invalid/rota` | `https://operacao.example.invalid/rota/api/configuracoes/gmail/callback` |
   | Homologação local HTTPS | `https://localhost:8444/rota` | `https://localhost:8444/rota/api/configuracoes/gmail/callback` |

   Substitua por URLs aprovadas e estáveis da instalação e registre-as no cliente
   correto. O Google deve aceitar a URI escolhida; use certificado confiável pelo
   navegador, sem ignorar erros TLS. Não dependa de hostname temporário trycloudflare.
8. Guarde o client ID e o client secret somente na configuração externa protegida.
   O segredo pode ser exibido uma única vez pelo Google; use o procedimento seguro
   do Console para armazenamento/rotação. Não invente valores para ativar a aplicação.
9. Confira o status **Testing/Production** e os requisitos Google antes de uso diário.
   Aplicativo Externo em teste com `gmail.send` normalmente tem refresh tokens com
   validade de **7 dias**; a exceção para apenas identidade básica não cobre envio Gmail.
   Contas/test users, limites de usuários não verificados, políticas Workspace e
   revogações podem bloquear o acesso. Publicação/verificação depende dos escopos,
   público e políticas atuais do Google; publicar não significa aprovação automática.
   A aplicação **não é declarada verificada pelo Google** por esta entrega.

## 2. Cofre e chave externos

Tokens não são gravados nas tabelas, backups documentais, snapshots nem históricos.
O cofre é um arquivo privado externo criptografado com **AES-256-GCM da JCA**, IV
aleatório de 96 bits por gravação, tag de 128 bits e contexto autenticado fixo.
Não há chave padrão nem implementação própria do algoritmo criptográfico. Tanto
access quanto refresh token e metadados privados ficam criptografados.

O arquivo de chave deve conter **exatamente 32 bytes aleatórios binários**, não uma
senha, texto hexadecimal ou Base64. Mantenha diretórios separados para cofre e chave,
fora do checkout, acervo, diretório estático, exportação e backup comum dos dados.

### Windows

Há um provisionador sem segredos em argumentos ou saída. Crie antes os dois diretórios
**pais**, em local externo aprovado; os diretórios finais devem ser NOVOS. Confira a
conta Windows efetivamente usada pelo Java. Exemplo ilustrativo, executar localmente:

```powershell
powershell.exe -NoProfile -ExecutionPolicy RemoteSigned -File scripts/preparar-cofre-gmail.ps1 -DiretorioChave C:/RotaBrasilChaves/gmail -DiretorioCofre C:/RotaBrasilEstadoPrivado/gmail -ContaServico 'DOMINIO\conta-do-java'
```

O script cria ACL sem herança ampla antes de gravar a chave: provisionador com
controle total, conta de serviço com leitura da chave e modificação do diretório de
cofre. Se forem a mesma conta, ela terá controle total para administrar a instalação.
Confira ACLs com `Get-Acl` sem mostrar o conteúdo dos arquivos. Pais não devem ser
reparse points/junções nem ter permissão de substituição por outros usuários.

### Linux

Provisione diretórios privados pertencentes à conta do serviço (0700), use fonte
criptográfica do SO para 32 bytes binários, arquivo chave 0400/0600 e cofre 0600.
Não imprima os bytes, não os passe em argumentos. A conta Java precisa ler a chave e
criar/substituir arquivos no diretório do cofre. Use filesystem local com renomeação
atômica e locks; não use compartilhamento sem essas garantias.

### Persistência, concorrência e recuperação

- A primeira consulta/conexão carrega e autentica o cofre; falta de chave, corrupção
  ou chave diferente bloqueiam o uso sem retornar detalhes privados à UI.
- Gravação usa arquivo temporário no mesmo diretório, `force` e renomeação atômica.
  Lock de arquivo mantido até encerrar o processo impede dois processos de usarem
  o mesmo cofre. Esta instalação é **uma instância backend por cofre**; não é uma
  solução de cluster. Métodos sincronizados serializam renovação, troca, desconexão
  e confirmação, incluindo a reserva de tentativa e o transporte.
- Renova no uso se faltarem 60 segundos de validade; somente uma renovação concorrente.
  Rotação, quando devolvida, é persistida; ausência de refresh token na renovação
  conserva o anterior. A versão não secreta da autorização não muda na renovação.
- `invalid_grant` e perda comprovada de autorização exigem reconexão, sem loop.
  Falha temporária marca renovação indisponível e aplica cooldown de 60 segundos;
  outra tentativa depende de ação manual. Nenhum scheduler envia mensagens.
- Faça backup **privado separado** do cofre, com a aplicação parada para consistência.
  A chave exige custódia e backup próprios, separados do banco, cofre e seus backups.
  Registre instalação/cliente/versão sem copiar tokens para inventários comuns.
- Para recuperar, restaure cofre e chave correspondentes com ACLs e o mesmo cliente
  OAuth. Nunca execute duas cópias restauradas com a mesma autorização. Confira a
  conta e gere nova conferência. Google pode ter revogado/expirado o token apesar
  da restauração; nesse caso, reconecte.
- Perda da chave impede recuperar tokens: não gere outra chave por cima esperando
  recuperar o arquivo. Revogue o acesso no Google, arquive/remova o cofre inutilizável
  pelo procedimento administrativo e provisione novos caminhos/chave para reconectar.
  Rotação de chave requer reconexão em cofre novo ou procedimento externo revisado;
  não há rotação automática da chave AES.

## 3. Configuração externa e implantação

Use [o modelo sem segredos](../config/modelos/gmail-api-documental.properties.example).
As propriedades próprias da conexão são:

| Nome | Conteúdo / exemplo não secreto |
|---|---|
| `rotabrasil.email.transporte` | `GMAIL_API` (ou `SMTP` avançado separado) |
| `rotabrasil.email.modo` | `SIMULADO` inicialmente |
| `rotabrasil.gmail.envio-ativado` | `false` inicialmente, opt-in independente da conexão |
| `rotabrasil.gmail.client-id` | client ID real do cliente Web, provisionado externamente |
| `rotabrasil.gmail.client-secret` | segredo em configtree externo restrito, nunca frontend/argumento |
| `rotabrasil.gmail.url-publica` | por exemplo `https://homolog.example.invalid/rota`, sem barra final |
| `rotabrasil.gmail.redirect-uri` | a URI exata cadastrada no Console, conforme tabela acima |
| `rotabrasil.gmail.cofre-arquivo` | caminho absoluto externo de `conta.enc` |
| `rotabrasil.gmail.chave-arquivo` | caminho absoluto externo da chave binária `aes256.key` |

Por exemplo, importe `configtree:C:/RotaBrasilSegredos/cliente-google/` em uma
configuração externa via `spring.config.import`. Nessa pasta, o arquivo protegido
`rotabrasil.gmail.client-secret` contém apenas o segredo real, criado pelo responsável
local. Não há valor de exemplo de segredo nem padrão. Restrinja igualmente configurações
externas, chave TLS e credenciais do banco/bootstrap existentes. Não ative logging HTTP
de headers/corpos nem DEBUG de OAuth/JDK para investigar tokens.

Mantenha perfil `operacao`, HTTPS, autenticação ativa, cookies Secure/HttpOnly/Lax,
H2 Console desligado, `spring.jpa.hibernate.ddl-auto=validate` e
`spring.flyway.enabled=false`. Para `/rota`, configure
`server.servlet.context-path=/rota`; na raiz deixe o contexto vazio. O frontend usa
as rotas de mesma origem e o callback retorna ao painel do contexto configurado.

**Proxy:** a URL OAuth não é construída a partir de headers. O padrão recomendado
é `server.forward-headers-strategy=none`, TLS no backend, ou proxy reverso com backend
inacessível diretamente. Se precisar interpretar headers do proxy, use configuração
nativa do Tomcat (`server.forward-headers-strategy=native`) e uma expressão exata em
`server.tomcat.remoteip.internal-proxies` apenas para os IPs do proxy autorizado.
O proxy deve remover Forwarded/X-Forwarded-* recebidos do cliente e escrever seus
próprios valores; não use confiança universal nem `framework` indiscriminadamente.
Preserve o caminho `/rota` ponta a ponta. Nos logs de acesso do proxy/backend,
registre apenas o path do callback, **sem query string, código ou state**; o logger
da aplicação já registra a rota-modelo sem esses parâmetros. Callback responde 303,
`no-store` e `Referrer-Policy: no-referrer`, sem HTML contendo tokens.

**Banco:** o JAR novo exige V16. Ela somente amplia o CHECK da situação de tentativa
documental para incluir `ACEITO_API`; não reinterpreta os `ACEITO_SMTP` antigos.
Migrações V1–V15 não foram alteradas. A verificação do esquema impede iniciar o JAR
em base V15. Para uma base sintética NOVA, use as fixtures/testes ou o preparador
offline existente com o JAR atual. Para banco operacional, preparar inventário,
backup verificado e autorização específica ANTES da manutenção offline existente;
não executar `clean`, `repair` ou `ddl-auto=update`. Nenhuma base operacional foi
acessada ou migrada nesta tarefa. O backup documental não inclui o cofre/chave.

## 4. Conectar e preparar envio (administrador)

1. Inicie com `SIMULADO` e opt-in falso. Entre normalmente no RotaBrasil como ADMIN.
2. Em **Configurações**, consulte Google — conta documental compartilhada. Se aparecer
   indisponível, resolva cliente/URLs/cofre/permissões externos antes de continuar.
3. Clique **Conectar com Google**, escolha a conta compartilhada de envio e confira
   os dois escopos na tela Google. Essa tela é operada pelo Google, não pelo RotaBrasil.
4. Depois do retorno, confira a conta identificada e a permissão `gmail.send`.
   SIMULADO continuará SIMULADO. Não existe campo para inventar outro remetente.
5. Na Central selecione explicitamente viagem/anexos e destinatário. Gere a conferência
   e confira endereço remetente, assunto, texto, nomes, tipos, tamanhos e SHA-256.
   Essa preparação não transporta nem cria tentativa de envio.
6. Ativação REAL futura exige alterar externamente **os dois** parâmetros
   `rotabrasil.email.modo=REAL` e `rotabrasil.gmail.envio-ativado=true` e reiniciar a
   instância própria. H2 em arquivo exige `WRITE_DELAY=0`. A UI não ativa esses opt-ins.
7. Depois de ativar, gere **nova** conferência, confira tudo e só confirme uma vez
   com autorização operacional específica. OPERADOR mantém a permissão existente
   para confirmar; não precisa ser ADMIN nem conectar sua própria conta Google.
8. `ACEITO_API` significa resposta Gmail API com ID de mensagem, não SMTP, entrega,
   leitura ou recebimento. Verificação de recebimento em caixa controlada é outra
   etapa, futura, com aprovação específica. Ela não foi executada nesta entrega.

### Trocar, desconectar e resultados incertos

**Trocar conta / reconectar** conserva a conta anterior até validar a nova autorização
e persistir o cofre. Falha/negação/incompletude preserva a anterior. Cada autorização
concluída recebe versão UUID não secreta nova: qualquer conferência anterior diverge
da assinatura e precisa ser refeita. Renovação rotineira não muda essa assinatura.
Não se revoga automaticamente a autorização antiga na troca, pois a revogação Google
pode abranger o projeto e invalidar o consentimento recém-obtido. Se mudou de pessoa/
conta, o ADMIN deve revisar os acessos da conta anterior no Google sem revogar a nova.

**Desconectar** invalida a conta local imediatamente e persiste um tombstone sem
tokens antes de tentar revogar via POST ao Google. Conferências pendentes ficam
inutilizáveis pela mudança de assinatura; permanecem auditáveis. Históricos e tentativas
persistidas são preservados e recuperáveis. A UI informa revogação remota não
confirmada quando houver falha: remova o acesso em **Conta Google → Segurança →
Conexões com apps e serviços de terceiros**. Tokens não são guardados em histórico.

Antes do transporte a tentativa já foi commitada como `DESCONHECIDO`. Há guarda de
clique duplo e concorrência por conferência, inclusive se a resposta HTTP ao navegador
se perder. Recuperar usa a MESMA conferência, retorna a tentativa e não reenvia.
Timeout, conexão interrompida ou 5xx do provedor são `DESCONHECIDO`; não se presume
idempotência da API nem se repete automaticamente. Recusas HTTP comprovadas são
`FALHA`. Outro envio deliberado exige decisão humana, nova conferência e vínculo
explícito à tentativa anterior. Mudar a conta não apaga esse histórico.

## 5. Testes e capturas

Os testes usam apenas H2 sintético, arquivos isolados, chave aleatória de fixture e
servidor HTTP OAuth/API em loopback. Os endpoints controlados existem somente no
classpath de testes; produção fixa os endpoints Google e não aceita overrides por
propriedade. O navegador intercepta a navegação de consentimento ANTES da rede;
não chama Google e não autoriza nenhuma conta real. A página de consentimento
capturada está explicitamente marcada como fictícia.

```powershell
# Executar dentro de back/; diretório de build externo e novo/aprovado.
mvn -B -Drotabrasil.build.directory=C:/TEMP_APROVADO/rotabrasil-google-oauth-build clean test
# Teste HTTPS de navegador, com Playwright/Chrome instalados externamente:
$env:NODE_PATH='C:/TEMP_APROVADO/node_modules'
mvn -B -Drotabrasil.build.directory=C:/TEMP_APROVADO/rotabrasil-google-oauth-build -Dtest=GmailDocumentalHttpTest -Drotabrasil.browser=true -Drotabrasil.browser.output=C:/TEMP_APROVADO/rotabrasil-google-oauth-ui test
```

O roteiro `scripts/testar-google-documental.cjs` só aceita a fixture local sinalizada
pelo teste Java. Confere temas claro/escuro, 1440×1000 e 390×844, teclado, HTTPS e
sessão ADMIN no callback `/rota`, estados de conta, ocultação do remetente arbitrário,
Central com API/MIME controlada, clique duplo e resposta perdida sem reenvio.
Chaves/certificados/capturas e relatórios gerados ficam fora do Git.

### Aceite executado em 15/11/2026

**452 testes JUnit: 451 aprovados, zero falhas/erros, 1 ignorado** (symlink Windows).
Inclui 18 testes OAuth/cofre/renovação, 11 testes HTTP Gmail (com o navegador),
migração V15→V16 com preservação de snapshot/aceite SMTP, e toda a regressão existente.
Os dois navegadores controlados passaram: Central SMTP legada e Central Gmail API.
O teste Gmail comprova MIME e SHA-256 do anexo transportado iguais à conferência,
tentativa persistida antes do POST ao provedor, resposta perdida, concorrência e
ausência de tokens/código nos logs, HTML e APIs. Nenhum operador foi criado ou
promovido ao conectar. Provisionador PowerShell também foi executado somente com
diretórios novos e chave aleatória de fixture externa; nenhum segredo foi exibido.

Comando integrado efetivamente executado em `back/`:

```text
NODE_PATH=C:/Users/florb/AppData/Local/Temp/opencode/node_modules
mvn -B -Drotabrasil.build.directory=C:/Users/florb/AppData/Local/Temp/opencode/rotabrasil-google-oauth-final-build -DargLine=-Xmx768m -Drotabrasil.browser=true -Drotabrasil.browser.output=C:/Users/florb/AppData/Local/Temp/opencode/rotabrasil-google-oauth-ui-final clean test
```

`package -DskipTests` passou depois do aceite. O JAR contém o frontend Google, o
adaptador API e a migração V16. Artefato **gerado, sem deploy ou alteração de instância**:

```text
C:/Users/florb/AppData/Local/Temp/opencode/rotabrasil-google-oauth-final-build/rota-brasil-0.0.1-SNAPSHOT.jar
SHA-256: 12dec06845a3bf4457f1c1f370a713f47daf247c30cb94afcf32ac55a386e076
```

Evidências externas, sem dados reais, em
`C:/Users/florb/AppData/Local/Temp/opencode/rotabrasil-google-oauth-ui-final/`:

| Captura | Conteúdo fictício |
|---|---|
| `01-nao-conectada-claro-desktop.png` / `02-nao-conectada-escuro-desktop.png` | Conta não conectada, ambos os temas |
| `03-autorizacao-controlada.png` | Consentimento explicitamente fictício interceptado, sem Google real |
| `04-conectada-escuro-desktop.png` / `05-conectada-claro-desktop.png` | Identidade, permissão, transporte, modo e recebimento separados |
| `06-conectada-claro-celular.png` / `07-conectada-escuro-celular.png` | 390×844, ambos os temas, sem overflow |
| `08-renovacao-indisponivel.png` | Falha temporária de renovação |
| `09-reconexao-necessaria.png` | Autorização exigindo reconexão |
| `10-central-aceito-api-controlada.png` | Central com tentativa API controlada recuperada sem reenvio |
| `11-desconectada-historico-preservado.png` | Desconexão local e histórico preservado |

`resultado.json` confirma zero erros JavaScript e nenhum envio externo/autorização
Google real; `resultado-real-double.json` registra a regressão SMTP. Logs externos:
`rotabrasil-google-oauth-aceite.log` e `rotabrasil-google-oauth-package.log` no mesmo
diretório temporário pai. As capturas não foram adicionadas ao Git.

### Arquivos principais da implementação

- Backend: `ContaGmailDocumentalController`, `ContaGmailDocumental`,
  `GoogleDocumentalClient`, `CofreGmail`, `TransporteGmailApi`.
- Integração: `EnvioManualService`, `ProntidaoEnvioDocumental`, `TransporteEmail`,
  seleção condicional de SMTP, diagnósticos/pendências e proteção de remetente manual
  em `ConfiguracaoOperacaoService`.
- Banco: `back/src/main/java/db/migration/V16__aceite_gmail_api.java` e verificação
  V16; preparador sintético e iniciador SMTP alinhados à nova versão do esquema.
- Interface: `front/painel-gmail.js`, `index.html`, Configurações, Central, estilos e
  hashes de recursos servidos pelo JAR.
- Configuração: modelo `config/modelos/gmail-api-documental.properties.example`,
  provisionador `scripts/preparar-cofre-gmail.ps1` e SameSite=Lax no perfil operação.
- Testes: `ContaGmailDocumentalTest`, `GoogleControlado`, `GmailDocumentalHttpTest`,
  `MigracaoGmailApiTest`, ajustes pontuais nos doubles/regressões existentes e
  `scripts/testar-google-documental.cjs`.

**Integração controlada não é homologação Google real**: permanecem pendentes cliente
Google, consentimento do responsável, políticas/verificação quando aplicáveis,
implantação autorizada e futura tentativa única com recebimento comprovado.

### Referências Google consultadas

- [OAuth2 para aplicações Web/backend](https://developers.google.com/identity/protocols/oauth2/web-server)
- [Escopos OAuth Google](https://developers.google.com/identity/protocols/oauth2/scopes)
- [Contrato oficial Userinfo v2 (discovery)](https://www.googleapis.com/discovery/v1/apis/oauth2/v2/rest)
- [Enviar mensagens MIME pela Gmail API](https://developers.google.com/workspace/gmail/api/guides/sending)
- [Escopos Gmail e classificação](https://developers.google.com/workspace/gmail/api/auth/scopes)
- [Expiração e revogação de refresh tokens](https://developers.google.com/identity/protocols/oauth2#expiration)
- [Verificação de escopos sensíveis](https://developers.google.com/identity/protocols/oauth2/production-readiness/sensitive-scope-verification)
