/* Executado somente pela fixture GmailDocumentalHttpTest: OAuth/API locais, dados fictícios. */
'use strict';
const {chromium,expect:baseExpect}=require('playwright/test');
const expect=baseExpect.configure({timeout:20000});
const fs=require('node:fs'),path=require('node:path'),assert=require('node:assert/strict'),crypto=require('node:crypto');
const [url,out,trip,certificado]=process.argv.slice(2);let browser,page;const errors=[],checks=[];
async function main(){
  if(!url?.startsWith('https://127.0.0.1:')||!url.endsWith('/rota')||process.env.ROTABRASIL_TESTE_GOOGLE_CONTROLADO!=='1')throw Error('Requer fixture Java controlada em loopback');
  fs.mkdirSync(out,{recursive:true});
  const cert=new crypto.X509Certificate(fs.readFileSync(certificado)),pin=crypto.createHash('sha256').update(cert.publicKey.export({format:'der',type:'spki'})).digest('base64');
  browser=await chromium.launch({channel:'chrome',headless:true,args:['--ignore-certificate-errors-spki-list='+pin]});
  page=await browser.newPage({viewport:{width:1440,height:1000},locale:'pt-BR'});page.on('pageerror',e=>errors.push(e.message));
  // Intercepta a navegação Google ANTES da rede; qualquer outra origem externa é abortada.
  await page.route('**/*',async route=>{
    const u=new URL(route.request().url());
    if(u.origin===new URL(url).origin)return route.continue();
    if(u.origin==='https://accounts.google.com'){
      const s=await(await page.request.get(url+'/api/sessao')).json();
      assert.equal((await page.request.post(url+'/__fixture/google',{headers:{[s.csrfHeader]:s.csrf},data:{url:u.href}})).status(),200);
      const retorno=new URL(u.searchParams.get('redirect_uri'));retorno.searchParams.set('state',u.searchParams.get('state'));retorno.searchParams.set('code','codigo-sintetico');
      return route.fulfill({contentType:'text/html',body:`<!doctype html><html lang="pt-BR"><meta charset="UTF-8"><title>OAuth controlado</title><h1>Servidor OAuth fictício — nenhum acesso ao Google</h1><p>Conta sintética envio@example.invalid. Somente envio e identificação.</p><a href="${retorno.href.replaceAll('&','&amp;')}">Autorizar conta fictícia</a></html>`});
    }
    throw Error('Origem externa não permitida: '+u.origin);
  });
  const shot=async name=>{await page.locator('#config-gmail').scrollIntoViewIfNeeded();await page.screenshot({path:path.join(out,name+'.png'),fullPage:true});};
  const navegar=async hash=>{await page.evaluate(h=>location.hash=h,hash);await expect(page.locator('main')).toHaveAttribute('data-pagina-atual',hash==='documentos'?'viagens':hash);};
  async function login(nome){await page.goto(url+'/login');await page.locator('[name=username]').fill(nome);await page.locator('[name=password]').fill('SenhaSomenteFixture12!');await page.locator('button[type=submit]').click();await page.locator('#abertura').waitFor({state:'hidden'});}
  async function fixture(data){const s=await(await page.request.get(url+'/api/sessao')).json();assert.equal((await page.request.post(url+'/__fixture/google',{headers:{[s.csrfHeader]:s.csrf},data})).status(),200);}
  await login('admin');await navegar('configuracoes');await expect(page.locator('#gmail-estado')).toHaveText('Não conectada');await expect(page.locator('#config-remetente-manual')).toBeHidden();
  await shot('01-nao-conectada-claro-desktop');await page.locator('#alternar-tema').click();await shot('02-nao-conectada-escuro-desktop');
  await page.locator('#gmail-conectar').focus();await expect(page.locator('#gmail-conectar')).toBeFocused();await page.keyboard.press('Enter');
  await expect(page.getByRole('heading',{name:'Servidor OAuth fictício — nenhum acesso ao Google'})).toBeVisible();await page.screenshot({path:path.join(out,'03-autorizacao-controlada.png'),fullPage:true});
  await page.getByRole('link',{name:'Autorizar conta fictícia'}).click();await page.locator('#abertura').waitFor({state:'hidden'});
  await expect(page.locator('#gmail-estado')).toHaveText('Conectada / autorizada');await expect(page.locator('#gmail-detalhes')).toContainText('envio@example.invalid');await expect(page.locator('#gmail-retorno')).toContainText('Nenhuma mensagem foi enviada');
  assert((await page.context().cookies()).some(c=>c.name==='JSESSIONID'&&c.secure&&c.httpOnly&&c.sameSite==='Lax'));
  await shot('04-conectada-escuro-desktop');await page.locator('#alternar-tema').click();await shot('05-conectada-claro-desktop');
  checks.push('HTTPS /rota, callback cross-site com mesma sessão ADMIN, PKCE no servidor controlado, ação por teclado; nenhuma autorização Google real');
  await page.setViewportSize({width:390,height:844});await shot('06-conectada-claro-celular');await page.locator('#alternar-tema').click();await shot('07-conectada-escuro-celular');
  assert(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth));
  await fixture({estado:'RENOVACAO_INDISPONIVEL'});await page.locator('#gmail-recarregar').click();await expect(page.locator('#gmail-estado')).toHaveText('Renovação indisponível');await shot('08-renovacao-indisponivel');
  await fixture({estado:'RECONEXAO_NECESSARIA'});await page.locator('#gmail-recarregar').click();await expect(page.locator('#gmail-estado')).toHaveText('Reconexão necessária');await shot('09-reconexao-necessaria');
  await fixture({estado:'CONECTADA'});await page.locator('#gmail-recarregar').click();
  await page.setViewportSize({width:1440,height:1000});
  await navegar('documentos');await page.locator('#viagem-selecionada').selectOption(trip);await page.locator('#lista-documentos input').check();await navegar('email');
  await page.locator('#destinatario').fill('cliente@example.invalid');await page.locator('#gerar-previa').click();await expect(page.locator('#resultado-previa')).toContainText('envio@example.invalid');await page.locator('#conferi-envio').check();
  let perdeu=false,posts=0;await page.route('**/tentativas-email',async route=>{if(route.request().method()==='POST'){posts++;if(!perdeu){perdeu=true;await route.fetch();await route.abort();return;}}await route.continue();});
  await page.locator('#simular-envio').dblclick({timeout:1500}).catch(()=>{});await expect(page.locator('#recuperar-confirmacao')).toBeVisible();assert.equal(posts,1);
  await page.locator('#recuperar-confirmacao').click();await expect(page.locator('#resultado-tentativa')).toContainText('ACEITO_API');
  const tentativas=await(await page.request.get(url+`/api/viagens/${trip}/tentativas-email`)).json();assert.equal(tentativas.length,1);
  await page.screenshot({path:path.join(out,'10-central-aceito-api-controlada.png'),fullPage:true});checks.push('Central com MIME/API controlada, clique duplo e resposta HTTP perdida, recuperação da mesma tentativa sem reenvio');
  await navegar('configuracoes');page.once('dialog',d=>d.accept());await page.locator('#gmail-desconectar').click();await expect(page.locator('#gmail-estado')).toHaveText('Não conectada');await shot('11-desconectada-historico-preservado');
  const html=await page.content(),api=JSON.stringify(await(await page.request.get(url+'/api/configuracoes/gmail')).json());
  for(const valor of ['ACCESS_SINTETICO_PRIVADO','REFRESH_SINTETICO_PRIVADO','SECRET_SINTETICO_PRIVADO']){assert(!html.includes(valor));assert(!api.includes(valor));}
  checks.push('Ambos os temas, desktop e celular, sem overflow; estados de renovação/reconexão, desconexão, tokens ausentes de HTML/API');
  let consultasLocais=0;
  await page.route('**/api/configuracoes/gmail',async route=>{consultasLocais++;await route.continue();});
  await page.route('**/api/sessao',async route=>{
    const resposta=await route.fetch(),sessao=await resposta.json();
    await route.fulfill({response:resposta,json:{...sessao,usuario:'DESENVOLVIMENTO_LOCAL',papeis:['ROLE_ADMIN'],csrf:''}});
  });
  await page.reload();await page.locator('#abertura').waitFor({state:'hidden'});
  await expect(page.locator('#gmail-estado')).toHaveText('Conectar Google exige login no ambiente protegido');
  await expect(page.locator('#gmail-conectar')).toBeDisabled();await expect(page.locator('#sessao-expirada')).toHaveCount(0);assert.equal(consultasLocais,0);
  await shot('12-desenvolvimento-local-exige-login');
  checks.push('Sessão de desenvolvimento controlada: papel ADMIN local e CSRF vazio não geram consulta Google nem falsa expiração; orientação de login protegido');
  assert.deepEqual(errors,[]);fs.writeFileSync(path.join(out,'resultado.json'),JSON.stringify({checks,errors,envioExterno:false,googleReal:false},null,2));console.log(JSON.stringify({checks,errors},null,2));
}
main().catch(async e=>{console.error(e);console.error({errors});if(page)await page.screenshot({path:path.join(out,'falha.png'),fullPage:true}).catch(()=>{});process.exitCode=1;}).finally(async()=>{if(browser)await browser.close();});
