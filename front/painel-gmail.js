import { $,api,element,podeAdministrar,carregarSessao } from './painel-base.js';
let conta=null,ocupado=false,sessaoProtegida=false;
const nomes={NAO_CONFIGURADA:'Integração Google ainda não configurada',NAO_CONECTADA:'Não conectada',CONECTADA:'Conectada / autorizada',RENOVACAO_INDISPONIVEL:'Renovação indisponível',RECONEXAO_NECESSARIA:'Reconexão necessária',INDISPONIVEL:'Cofre Google indisponível'};
function controles(){
  $('gmail-conectar').disabled=ocupado||!sessaoProtegida||!podeAdministrar()||!conta?.configurado;
  $('gmail-desconectar').disabled=ocupado||!sessaoProtegida||!podeAdministrar()||!conta?.conta;
  $('gmail-recarregar').disabled=ocupado||!podeAdministrar();
  $('config-gmail').setAttribute('aria-busy',String(ocupado));
}
async function carregar(){
  if(ocupado||!podeAdministrar()){controles();return;}
  ocupado=true;controles();
  try{
    const sessao=await carregarSessao();
    sessaoProtegida=sessao.usuario!=='DESENVOLVIMENTO_LOCAL'&&Boolean(sessao.csrf)&&Boolean(sessao.csrfHeader);
    if(!sessaoProtegida){
      conta=null;$('gmail-estado').textContent='Conectar Google exige login no ambiente protegido';
      $('gmail-detalhes').replaceChildren(element('dt','Ambiente atual'),element('dd','Desenvolvimento local, sem login individual e sem CSRF. Abra a URL HTTPS de homologação protegida e entre como ADMIN; o papel local de demonstração não autoriza conectar Google.'));
      return;
    }
    conta=await api('/api/configuracoes/gmail');
    $('gmail-estado').textContent=nomes[conta.estado]||'Estado não confirmado';
    const linhas=[['Identidade da conta',conta.conta||'Nenhuma conta autorizada'],['Permissão de envio',conta.permissaoEnvio?'gmail.send concedido na autorização validada':'Envio não autorizado'],['Transporte',`${conta.selecionado?'Gmail API selecionada':'SMTP manual selecionado — conexão Gmail indisponível'} · ${conta.transporteAtivado?'opt-in real ativo':'opt-in real desativado'}`],['Modo de execução',conta.modo==='SIMULADO'?'SIMULADO — conexão não ativa envio real':'REAL — confirmação manual obrigatória'],['Última tentativa documental',conta.ultimaTentativa||'Nenhuma tentativa'],['Recebimento','Não comprovado por conexão ou aceitação do provedor']];
    $('gmail-detalhes').replaceChildren(...linhas.flatMap(([k,v])=>[element('dt',k),element('dd',v)]));
    if(!conta.configurado){
      const lista=element('ul');for(const p of conta.pendencias||[])lista.append(element('li',p));
      const orientacao=element('dd');orientacao.append(element('p',conta.aviso),lista);
      $('gmail-detalhes').append(element('dt','Orientação administrativa'),orientacao);
    }
    $('gmail-conectar').textContent=conta.conta?'Trocar conta / reconectar':'Conectar com Google';
    $('gmail-desconectar').hidden=!conta.conta;
    $('config-remetente-manual').hidden=conta.selecionado;
    $('campos-configuracoes').dataset.gmailApi=String(conta.selecionado);
    $('config-remetente').readOnly=conta.selecionado;
  }catch(error){conta=null;$('gmail-estado').textContent='Não foi possível consultar a conta. '+error.message;$('gmail-detalhes').replaceChildren();}
  finally{ocupado=false;controles();}
}
export function iniciarGmail(){
  const url=new URL(location.href),resultado=url.searchParams.get('gmail');
  if(resultado){
    const mensagens={CONECTADA:'Conta conectada. Nenhuma mensagem foi enviada. Gere nova conferência na Central.',CONSENTIMENTO_NEGADO:'Consentimento negado. A conta anterior foi preservada.',EXPIRADA:'Conexão expirada. Inicie novamente.',STATE_INVALIDO:'Retorno inválido ou já utilizado. Inicie novamente nesta sessão ADMIN.',SESSAO_INVALIDA:'Sessão expirada. Entre como ADMIN e inicie novamente.',ESCOPO_INSUFICIENTE:'Permissão de envio ou identificação não concedida. Autorize os dois escopos solicitados.',RENOVACAO_AUSENTE:'Google não emitiu refresh token. Conta anterior preservada; repita o consentimento. Consulte o guia antes de revogar acesso.',CONTA_ALTERADA:'Outra alteração de conta ocorreu. Confira a conta atual antes de repetir.',IDENTIDADE_INVALIDA:'Não foi possível validar a identidade do remetente.',AUTORIZACAO_INVALIDA:'Código, PKCE ou tokens inválidos, ou provedor indisponível. Conta anterior preservada; inicie nova conexão.',INCOMPLETA:'Autorização incompleta. Inicie novamente.'};
    $('gmail-retorno').textContent=mensagens[resultado]||'Retorno de autorização não reconhecido. Consulte a conta atual.';
    url.searchParams.delete('gmail');history.replaceState(null,'',url.pathname+url.search+url.hash);
  }
  $('gmail-recarregar').addEventListener('click',carregar);
  $('gmail-conectar').addEventListener('click',async()=>{
    if(ocupado||!sessaoProtegida||!podeAdministrar()||!conta?.configurado)return;
    if(conta.conta&&!confirm('Autorizar outra conta ou reconectar? A conta atual continua disponível até a conclusão. A troca exigirá nova conferência antes de enviar.'))return;
    ocupado=true;controles();
    try{const d=await api('/api/configuracoes/gmail/conectar',{method:'POST'});location.assign(d.url);}
    catch(error){$('gmail-retorno').textContent=error.message;ocupado=false;controles();}
  });
  $('gmail-desconectar').addEventListener('click',async()=>{
    if(ocupado||!sessaoProtegida||!podeAdministrar()||!conta?.conta||!confirm('Desconectar a conta compartilhada? O uso local e as conferências pendentes serão invalidados; históricos serão preservados.'))return;
    ocupado=true;controles();
    try{const d=await api('/api/configuracoes/gmail/desconectar',{method:'POST'});$('gmail-retorno').textContent=d.mensagem;document.dispatchEvent(new Event('configuracoes-atualizadas'));}
    catch(error){$('gmail-retorno').textContent='Resultado da desconexão não confirmado. Atualize a conexão antes de nova ação. '+error.message;}
    finally{ocupado=false;await carregar();}
  });
  document.addEventListener('sessao-expirada',controles);
  document.addEventListener('configuracoes-atualizadas',carregar);
  return carregar();
}
