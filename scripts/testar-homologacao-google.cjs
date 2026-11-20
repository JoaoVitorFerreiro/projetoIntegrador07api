/* Smoke test do JAR de homologação já iniciado: credenciais Google AUSENTES, zero rede Google. */
'use strict';
const fs=require('node:fs'),path=require('node:path'),crypto=require('node:crypto'),assert=require('node:assert/strict');
const {spawn}=require('node:child_process');
const [url,root,out,worker]=process.argv.slice(2);
async function main(){
  if(!/^https:\/\/localhost:\d+(?:\/rota)?$/.test(url||'')||!root||!out)throw Error('Uso: node scripts/testar-homologacao-google.cjs URL_HTTPS_LOCAL RAIZ_SINTETICA SAIDA_EXTERNA');
  const identidade=JSON.parse(fs.readFileSync(path.join(root,'identidade.json'),'utf8'));
  assert.equal(identidade.tipo,'HOMOLOGACAO_SINTETICA_PROTEGIDA');assert.equal(identidade.transporte,'GMAIL_API');
  assert.equal(fs.readFileSync(path.join(root,'.rotabrasil-homologacao'),'utf8').trim(),'NOVA_BASE_SINTETICA_V16');
  const certificado=path.join(root,'config/servidor-publico.cer');
  if(!worker){
    fs.mkdirSync(out,{recursive:true});
    const https=require('node:https'),ca=fs.readFileSync(certificado);let status=0;
    for(let n=0;n<90;n++){
      status=await new Promise(resolve=>{const q=https.get(url+'/api/sessao',{ca,timeout:2000},r=>{r.resume();resolve(r.statusCode);});q.on('error',()=>resolve(0));q.on('timeout',()=>{q.destroy();resolve(0);});});
      if(status===401)break;if(status!==0)throw Error('A URL não exige login protegido');await new Promise(r=>setTimeout(r,1000));
    }
    assert.equal(status,401,'A instância protegida deve responder 401 antes do login');
    const p=spawn(process.execPath,[__filename,url,root,out,'worker'],{env:{...process.env,NODE_EXTRA_CA_CERTS:certificado},stdio:'inherit'});
    process.exitCode=await new Promise(r=>p.once('exit',r));return;
  }
  const {chromium,expect:baseExpect}=require('playwright/test'),expect=baseExpect.configure({timeout:20000});
  const cert=new crypto.X509Certificate(fs.readFileSync(certificado)),pin=crypto.createHash('sha256').update(cert.publicKey.export({format:'der',type:'spki'})).digest('base64');
  const senha=fs.readFileSync(path.join(root,'segredos/acesso-admin-local.txt'),'utf8');
  const runtime=fs.readFileSync(path.join(root,'config/runtime.properties'),'utf8'),usuario=runtime.match(/^rotabrasil.seguranca.admin-usuario=(.+)$/m)?.[1].trim();assert(usuario);
  const browser=await chromium.launch({channel:'chrome',headless:true,args:['--ignore-certificate-errors-spki-list='+pin]});
  const page=await browser.newPage({viewport:{width:1440,height:1000},locale:'pt-BR'}),errors=[];
  page.on('pageerror',e=>errors.push(e.message));
  await page.route('**/*',async route=>{if(new URL(route.request().url()).origin!==new URL(url).origin)throw Error('Rede externa não permitida');await route.continue();});
  try{
    assert.equal((await page.request.get(url+'/api/sessao')).status(),401);
    assert.equal((await page.request.get(url+'/api/configuracoes/gmail')).status(),401);
    await page.goto(url+'/login');await page.locator('[name=username]').fill(usuario);await page.locator('[name=password]').fill(senha);await page.locator('button[type=submit]').click();await page.waitForURL(/\/painel(?:[#?].*)?$/);
    await page.locator('#abertura').waitFor({state:'hidden'});
    await page.evaluate(()=>{location.hash='configuracoes';});
    await expect(page.locator('#gmail-estado')).toHaveText('Integração Google ainda não configurada');
    await expect(page.locator('#gmail-detalhes')).toContainText('rotabrasil.gmail.client-id');await expect(page.locator('#gmail-detalhes')).toContainText('rotabrasil.gmail.client-secret');
    await expect(page.locator('#gmail-conectar')).toBeDisabled();await expect(page.locator('#sessao-expirada')).toHaveCount(0);
    const s=await(await page.request.get(url+'/api/sessao')).json();assert.equal(s.usuario,usuario);assert(s.csrf);assert(s.papeis.includes('ROLE_ADMIN'));assert.equal(s.modoEmail,'SIMULADO');
    assert((await page.context().cookies()).some(c=>c.name==='JSESSIONID'&&c.secure&&c.httpOnly&&c.sameSite==='Lax'));
    const d=await(await page.request.get(url+'/api/configuracoes/gmail')).json();assert.equal(d.estado,'NAO_CONFIGURADA');assert.equal(d.selecionado,true);
    assert.equal((await page.request.post(url+'/api/configuracoes/gmail/conectar')).status(),403);
    assert.equal((await page.request.post(url+'/api/configuracoes/gmail/conectar',{headers:{[s.csrfHeader]:s.csrf}})).status(),409);
    assert.equal((await page.request.get(url+'/api/sessao')).status(),200);
    await page.screenshot({path:path.join(out,'google-nao-configurada-admin-claro.png'),fullPage:true});
    await page.locator('#alternar-tema').click();await page.setViewportSize({width:390,height:844});await page.screenshot({path:path.join(out,'google-nao-configurada-admin-escuro-celular.png'),fullPage:true});
    assert(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth));
    const r=await page.request.post(url+'/api/processamento/xml',{headers:{[s.csrfHeader]:s.csrf},multipart:{arquivo:{name:'cte-sintetico.xml',mimeType:'application/xml',buffer:fs.readFileSync(path.join(root,'sinteticos/cte-sintetico.xml'))}}});assert.equal(r.status(),200);
    const trip=String((await r.json()).viagemId);
    assert.equal((await(await page.request.get(url+`/api/viagens/${trip}/tentativas-email`)).json()).length,0);
    const versao=await(await page.request.get(url+'/api/versao')).json();assert(versao.estaticosSha256['painel-gmail.js']);
    assert.deepEqual(errors,[]);
    fs.writeFileSync(path.join(out,'resultado.json'),JSON.stringify({url,usuario,csrfPresente:true,modoEmail:s.modoEmail,diagnostico:d,versao,errors,envioExterno:false,googleAutorizado:false},null,2));
    console.log('JAR protegido validado: login ADMIN real, CSRF, consulta Google 200 com configuração ausente, início 409 sem expirar sessão, escrita sem CSRF 403; zero envios e zero chamadas Google.');
  }finally{await browser.close();}
}
main().catch(e=>{console.error(e.message);process.exitCode=1;});
