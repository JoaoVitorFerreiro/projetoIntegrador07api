/* Valida o inicializador REAL de homologação, HTTPS e configtree no JAR. Sem SMTP externo. */
'use strict';
const fs=require('node:fs'),path=require('node:path'),https=require('node:https'),net=require('node:net'),crypto=require('node:crypto'),assert=require('node:assert/strict');
const {spawn,execFileSync}=require('node:child_process');
const [jarArg,classesArg,outArg,workerMode,urlArg,rootArg]=process.argv.slice(2);
const senha='SenhaSomenteFixture12!';
const sleep=ms=>new Promise(r=>setTimeout(r,ms));
async function worker(){
  const {chromium,expect}=require('playwright/test'),root=rootArg,url=urlArg,out=outArg;
  const cert=new crypto.X509Certificate(fs.readFileSync(path.join(root,'config/servidor-publico.cer'))),pin=crypto.createHash('sha256').update(cert.publicKey.export({format:'der',type:'spki'})).digest('base64');
  const browser=await chromium.launch({channel:'chrome',headless:true,args:['--ignore-certificate-errors-spki-list='+pin]});
  const page=await browser.newPage({viewport:{width:1440,height:1000},locale:'pt-BR'}),errors=[],checks=[];page.on('pageerror',e=>errors.push(e.message));
  try{
    assert.equal((await page.request.get(url+'/api/versao')).status(),401);
    await page.goto(url+'/login');await page.locator('[name=username]').fill('admin-homologacao');await page.locator('[name=password]').fill(senha);await page.locator('button[type=submit]').click();await page.locator('#abertura').waitFor({state:'hidden'});
    assert((await page.context().cookies()).some(c=>c.name==='JSESSIONID'&&c.secure&&c.httpOnly));
    const real=workerMode==='real-sem-envio';
    let d=await(await page.request.get(url+'/api/envio-documental')).json();assert.equal(d.provedor,'GMAIL');assert.equal(d.sessaoProtegida,true);assert.equal(d.credencialDisponivel,workerMode!=='ausente');assert.equal(d.autenticacaoVerificada,false);assert.equal(d.modo,real?'REAL':'SIMULADO');assert.equal(d.transporteAtivado,real);
    const versao=await(await page.request.get(url+'/api/versao')).json();assert.equal(versao.centralEnviosContrato,'documental-conferencia-v2');
    async function nav(h){await page.evaluate(x=>{location.hash=x;},h);await expect(page.locator('main')).toHaveAttribute('data-pagina-atual',h==='documentos'?'viagens':h);}
    await nav('configuracoes');await expect(page.locator('#config-remetente')).toBeEnabled({timeout:20000});
    await page.locator('#config-remetente').fill('homologacao@example.invalid');await page.locator('#salvar-configuracoes').click();await expect(page.locator('#estado-configuracoes')).toContainText('Configuração salva');
    const s=await(await page.request.get(url+'/api/sessao')).json(),headers={[s.csrfHeader]:s.csrf};
    const xml=fs.readFileSync(path.join(root,'sinteticos/cte-sintetico.xml'));
    const r=await page.request.post(url+'/api/processamento/xml',{headers,multipart:{arquivo:{name:'cte-sintetico.xml',mimeType:'application/xml',buffer:xml}}});assert.equal(r.status(),200);const trip=String((await r.json()).viagemId);
    await nav('documentos');await page.locator('#atualizar').click();await expect(page.locator('#atualizar')).toBeEnabled();await page.locator('#viagem-selecionada').selectOption(trip);await page.locator('#lista-documentos input').check();await nav('email');
    await page.locator('#destinatario').click();await page.keyboard.type('destinatario@example.invalid');await page.locator('#gerar-previa').click();await expect(page.locator('#resultado-previa')).toContainText('homologacao@example.invalid');await expect(page.locator('#resultado-previa')).toContainText('cte-sintetico.xml');await expect(page.locator('#simular-envio')).toBeDisabled();
    d=await(await page.request.get(url+'/api/envio-documental')).json();assert.equal(d.remetenteSalvo,true);assert.equal(d.confirmacaoRealDisponivel,real);assert.equal(d.autenticacaoVerificada,false);
    if(real){await page.locator('#conferi-envio').check();await expect(page.locator('#simular-envio')).toBeEnabled();/* NÃO clicar: a confirmação é exercitada apenas no contexto Mockito. */}
    assert.equal((await page.request.put(url+'/api/configuracoes',{data:{}})).status(),403);
    await page.screenshot({path:path.join(out,'protegida-'+workerMode+'.png'),fullPage:true});
    checks.push('JAR iniciado pelo PowerShell: perfil operacao, HTTPS fixado ao certificado local, sessão Secure/HttpOnly, API anônima 401 e escrita sem CSRF 403');
    checks.push('Configuração documental externa e configtree chegam ao Java: credencialDisponivel='+d.credencialDisponivel+'; autenticação não promovida a verificada');
    checks.push('ADMIN salva remetente e prepara conferência persistida com anexo sintético; salvar endereço não muda o modo; zero tentativas. Modo desta fase: '+d.modo);
    assert.equal((await(await page.request.get(url+`/api/viagens/${trip}/tentativas-email`)).json()).length,0);
    assert.deepEqual(errors,[]);fs.writeFileSync(path.join(out,'resultado-'+workerMode+'.json'),JSON.stringify({checks,errors,versao,diagnostico:d,envioExterno:false},null,2));
  }catch(e){await page.screenshot({path:path.join(out,'falha-'+workerMode+'.png'),fullPage:true}).catch(()=>{});throw e;}finally{await browser.close();}
}
async function principal(){
  if(!jarArg||!classesArg||!outArg)throw Error('Uso: node scripts/testar-homologacao-gmail.cjs JAR TEST_CLASSES SAIDA_EXTERNA');
  const jar=path.resolve(jarArg),classes=path.resolve(classesArg),out=path.resolve(outArg),root=path.join(out,'instancia-'+crypto.randomUUID()),repo=path.resolve(__dirname,'..');fs.mkdirSync(out,{recursive:true});
  const srv=net.createServer();await new Promise(r=>srv.listen(0,'127.0.0.1',r));const porta=srv.address().port;await new Promise(r=>srv.close(r));
  const ps=['-NoProfile','-ExecutionPolicy','RemoteSigned','-File',path.join(repo,'scripts/iniciar-homologacao-gmail.ps1')],comum=['-Jar',jar,'-Diretorio',root,'-Porta',String(porta)];
  fs.writeFileSync(path.join(out,'preparacao.log'),execFileSync('powershell.exe',[...ps,'-Acao','Preparar',...comum],{encoding:'utf8',timeout:60000}));
  const hash=execFileSync('java',['-Dloader.path='+classes,'-Dloader.main=br.com.rotabrasil.HashFixtureHomologacao','-cp',jar,'org.springframework.boot.loader.launch.PropertiesLauncher'],{encoding:'utf8'}).trim();assert.match(hash,/^\$2[aby]\$/);
  fs.writeFileSync(path.join(root,'segredos/rotabrasil.seguranca.admin-hash'),hash);
  let doc=fs.readFileSync(path.join(root,'config/documental.properties'),'utf8').replace('rotabrasil.email.smtp.usuario=','rotabrasil.email.smtp.usuario=homologacao@example.invalid').replace('rotabrasil.email.smtp.remetente=','rotabrasil.email.smtp.remetente=homologacao@example.invalid');fs.writeFileSync(path.join(root,'config/documental.properties'),doc);
  const ca=fs.readFileSync(path.join(root,'config/servidor-publico.cer')),url='https://localhost:'+porta;let servidor;
  async function parar(){if(servidor&&servidor.exitCode===null){/* Encerrar somente a árvore iniciada pelo teste. */execFileSync('taskkill.exe',['/PID',String(servidor.pid),'/T','/F'],{stdio:'ignore'});await sleep(1200);}}
  try{
    const recusa=spawn('powershell.exe',[...ps,'-Acao','Iniciar',...comum,'-AtivarReal'],{stdio:'ignore'});assert.notEqual(await new Promise(r=>recusa.once('exit',r)),0,'Opt-ins ausentes devem impedir ativação REAL');
    for(const fase of ['ausente','presente','real-sem-envio']){
      if(fase==='presente'){
        const entrada=path.join(root,'config/entrada-sintetica.txt'),driver=path.join(root,'config/prompt-sintetico.ps1'),q=s=>s.replaceAll("'","''");
        fs.writeFileSync(entrada,'abcdefghijklmnop');
        // Mock só do prompt: o helper de produção recebe SecureString e grava os arquivos reais externos.
        fs.writeFileSync(driver,`function Read-Host { param([string]$Prompt,[switch]$AsSecureString) return (ConvertTo-SecureString ([IO.File]::ReadAllText('${q(entrada)}')) -AsPlainText -Force) }\n& '${q(path.join(repo,'scripts/definir-senha-aplicativo-homologacao.ps1'))}' -Diretorio '${q(root)}'\n`);
        const saida=execFileSync('powershell.exe',['-NoProfile','-ExecutionPolicy','RemoteSigned','-File',driver],{encoding:'utf8'});assert(!saida.includes('abcdefghijklmnop'));
        fs.unlinkSync(entrada);fs.unlinkSync(driver);
      }
      if(fase==='real-sem-envio')fs.writeFileSync(path.join(root,'config/documental.properties'),doc.replace('rotabrasil.email.modo=SIMULADO','rotabrasil.email.modo=REAL').replace('rotabrasil.email.smtp.ativado=false','rotabrasil.email.smtp.ativado=true'));
      const logPath=path.join(out,'servidor-'+fase+'.log'),fd=fs.openSync(logPath,'w');servidor=spawn('powershell.exe',[...ps,'-Acao','Iniciar',...comum,...(fase==='real-sem-envio'?['-AtivarReal']:[])],{stdio:['ignore',fd,fd]});
      let pronto=false;
      for(let n=0;n<900;n++){if(servidor.exitCode!==null)throw Error('Inicializador protegido encerrou: consulte log sintético');if(fs.readFileSync(logPath,'utf8').includes('Tomcat started on port '+porta)){
        const status=await new Promise(resolve=>{const q=https.get(url+'/api/sessao',{ca},r=>{r.resume();resolve(r.statusCode);});q.on('error',()=>resolve(0));});if(status===401){pronto=true;break;}}
        await sleep(100);
      }assert(pronto,'HTTPS protegido deve responder 401 antes de autenticar');
      const p=spawn(process.execPath,[__filename,jar,classes,out,fase,url,root],{env:{...process.env,NODE_EXTRA_CA_CERTS:path.join(root,'config/servidor-publico.cer')},stdio:'inherit'});
      const code=await new Promise(r=>p.once('exit',r));assert.equal(code,0);await parar();fs.closeSync(fd);
      const logs=fs.readFileSync(logPath,'utf8');for(const segredo of [hash,'abcdefghijklmnop',fs.readFileSync(path.join(root,'segredos/server.ssl.key-store-password'),'utf8')])assert(!logs.includes(segredo),'Segredos não podem aparecer no log');
    }
    fs.writeFileSync(path.join(out,'identidade.json'),JSON.stringify({jar,sha256:crypto.createHash('sha256').update(fs.readFileSync(jar)).digest('hex'),porta,configuracaoExternaComprovada:true,segredosNosLogs:false,envioExterno:false},null,2));
    console.log('Homologação protegida, configuração externa ausente/presente, recuperação e ausência de segredos nos logs: aprovados. Nenhum SMTP externo.');
  }finally{await parar();}
}
(workerMode?worker():principal()).catch(e=>{console.error(e);process.exitCode=1;});
