package br.com.rotabrasil.service.email;

import br.com.rotabrasil.exception.ConflitoRecursoException;
import com.fasterxml.jackson.databind.*;
import jakarta.annotation.PreDestroy;
import jakarta.servlet.http.HttpSession;
import org.springframework.core.env.Environment;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;
import org.springframework.stereotype.Service;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.*;
import java.time.*;
import java.util.*;
import java.util.function.Supplier;

/** Autorização compartilhada da instalação, independente da autenticação dos operadores. */
@Service
public class ContaGmailDocumental {
    static final String SEND="https://www.googleapis.com/auth/gmail.send";
    static final String EMAIL="https://www.googleapis.com/auth/userinfo.email";
    static final String PENDENTE="rotabrasil.gmail.pendente";
    private static final class Pendente {
        final String state,verifier,ator,versao; final Instant expira;
        Pendente(String state,String verifier,String ator,String versao){this.state=state;this.verifier=verifier;this.ator=ator;this.versao=versao;expira=Instant.now().plusSeconds(600);}
        @Override public String toString(){return "ConexaoGoogle[omitida]";}
    }
    public record Estado(boolean selecionado,boolean configurado,String conta,String estado,boolean permissaoEnvio,
                         String autorizacaoVersao,String modo,boolean transporteAtivado,String ultimaTentativa,
                         String recebimento,String aviso,List<String> pendencias) {}
    private final Environment env;private final ObjectMapper json;private final GoogleDocumentalClient google;
    private CofreGmail cofre;private CofreGmail.Conta conta;
    private Instant proximaRenovacao=Instant.EPOCH;
    private boolean carregado;
    public ContaGmailDocumental(Environment env,ObjectMapper json,GoogleDocumentalClient google){this.env=env;this.json=json;this.google=google;}
    public boolean selecionado(){return "GMAIL_API".equals(env.getProperty("rotabrasil.email.transporte","SMTP"));}
    private String prop(String nome){return env.getProperty("rotabrasil.gmail."+nome,"");}
    public synchronized <T> T exclusivo(Supplier<T> acao){return acao.get();}
    private void carregar(){
        validarConfiguracao();
        if(carregado)return;
        cofre=new CofreGmail(Path.of(prop("cofre-arquivo")),Path.of(prop("chave-arquivo")),json);
        try{conta=cofre.ler();carregado=true;}
        catch(RuntimeException ex){try{cofre.close();}catch(Exception ignored){}cofre=null;throw ex;}
    }
    private void validarConfiguracao(){
        var faltas=pendenciasConfiguracao();
        if(!faltas.isEmpty())throw new ConflitoRecursoException("Integração Google ainda não configurada. "+String.join(" ",faltas));
    }
    /** Diagnóstico de presença/contrato, nunca inclui valores de credenciais ou caminhos privados. */
    private List<String> pendenciasConfiguracao(){
        var faltas=new ArrayList<String>();
        if(!selecionado())faltas.add("Selecione externamente rotabrasil.email.transporte=GMAIL_API.");
        if(!env.getProperty("rotabrasil.seguranca.ativa",Boolean.class,false))faltas.add("Conectar Google exige login no ambiente protegido, com ADMIN autenticado e CSRF.");
        for(String nome:List.of("client-id","client-secret","url-publica","redirect-uri","cofre-arquivo","chave-arquivo"))
            if(prop(nome).isBlank())faltas.add("Configure externamente rotabrasil.gmail."+nome+".");
        if(!prop("url-publica").isBlank()){
        try {
            URI publica=URI.create(prop("url-publica"));
            if(!"https".equals(publica.getScheme())||publica.getHost()==null||publica.getRawQuery()!=null||publica.getFragment()!=null||publica.getUserInfo()!=null
                    ||prop("url-publica").endsWith("/")||!Set.of("","/rota").contains(publica.getPath())
                    ||!prop("redirect-uri").isBlank()&&!prop("redirect-uri").equals(prop("url-publica")+"/api/configuracoes/gmail/callback"))throw new IllegalArgumentException();
        }catch(Exception ex){faltas.add("Confira URL pública HTTPS (raiz ou /rota) e redirect URI exato terminado em /api/configuracoes/gmail/callback.");}
        }
        return List.copyOf(faltas);
    }
    private boolean conectada(){return conta!=null&&conta.id!=null;}
    public synchronized Estado consultar(){
        boolean configurado=false;String aviso="A conexão permite envio somente após conferência e confirmação manual. Aceite da API não comprova recebimento ou leitura.";
        var faltas=pendenciasConfiguracao();
        if(!faltas.isEmpty())aviso="Integração Google ainda não configurada. Solicite ao ADMIN o provisionamento externo dos requisitos listados. Sua sessão não foi encerrada.";
        else try{carregar();configurado=true;}catch(RuntimeException ex){aviso="Cofre Google indisponível. Confira chave, arquivos privados, permissões e exclusividade do processo. Sua sessão não foi encerrada.";}
        boolean presente=configurado&&conectada();
        String estado=!faltas.isEmpty()?"NAO_CONFIGURADA":!configurado?"INDISPONIVEL":!presente?"NAO_CONECTADA":!prop("client-id").equals(conta.cliente)?"RECONEXAO_NECESSARIA":conta.estado;
        return new Estado(selecionado(),configurado,presente?conta.email:null,estado,presente&&!"RECONEXAO_NECESSARIA".equals(estado),configurado?versao():"",env.getProperty("rotabrasil.email.modo","SIMULADO"),env.getProperty("rotabrasil.gmail.envio-ativado",Boolean.class,false),null,"NAO_COMPROVADO",aviso,faltas);
    }
    public synchronized String remetente(){var e=consultar();return e.conta();}
    public synchronized boolean disponivelParaTentativa(){var e=consultar();return e.permissaoEnvio()&&("CONECTADA".equals(e.estado())||"RENOVACAO_INDISPONIVEL".equals(e.estado())&&!Instant.now().isBefore(proximaRenovacao));}
    synchronized void exigirReconexao(){if(conectada()){conta.estado="RECONEXAO_NECESSARIA";cofre.salvar(conta);}}
    public synchronized String assinatura(){var e=consultar();return "GMAIL_API|"+e.conta()+"|"+e.autorizacaoVersao()+"|"+prop("client-id")+"|"+e.modo()+"|"+e.transporteAtivado();}
    private String versao(){return conta==null?"":Objects.toString(conta.versao,"");}
    public synchronized String iniciar(HttpSession sessao,String ator){
        carregar();
        String state=aleatorio(), verifier=aleatorio();
        var pendente=new Pendente(state,verifier,ator,versao());
        synchronized(sessao){sessao.setAttribute(PENDENTE,pendente);}
        String challenge;
        try{challenge=Base64.getUrlEncoder().withoutPadding().encodeToString(MessageDigest.getInstance("SHA-256").digest(verifier.getBytes(StandardCharsets.US_ASCII)));}
        catch(NoSuchAlgorithmException ex){throw new IllegalStateException("SHA-256 indisponível");}
        return OAuth2AuthorizationRequest.authorizationCode().authorizationUri("https://accounts.google.com/o/oauth2/v2/auth")
                .clientId(prop("client-id")).redirectUri(prop("redirect-uri")).scopes(Set.of(SEND,EMAIL)).state(state)
                .additionalParameters(Map.of("access_type","offline","prompt","consent select_account","include_granted_scopes","false","code_challenge",challenge,"code_challenge_method","S256"))
                .build().getAuthorizationRequestUri();
    }
    public String concluir(HttpSession sessao,String ator,String state,String code,String error){
        Pendente p;
        if(sessao==null)return "SESSAO_INVALIDA";
        synchronized(sessao){p=(Pendente)sessao.getAttribute(PENDENTE);sessao.removeAttribute(PENDENTE);}
        if(p==null||state==null||!MessageDigest.isEqual(p.state.getBytes(StandardCharsets.UTF_8),state.getBytes(StandardCharsets.UTF_8))||!p.ator.equals(ator))return "STATE_INVALIDO";
        if(!p.expira.isAfter(Instant.now()))return "EXPIRADA";
        if(error!=null)return "CONSENTIMENTO_NEGADO";
        if(code==null||code.isBlank()||code.length()>8192)return "INCOMPLETA";
        synchronized(this){
            try {
                carregar();if(!p.versao.equals(versao()))return "CONTA_ALTERADA";
                JsonNode tokens=google.tokens(Map.of("grant_type","authorization_code","client_id",prop("client-id"),"client_secret",prop("client-secret"),"redirect_uri",prop("redirect-uri"),"code",code,"code_verifier",p.verifier));
                validarTokens(tokens,true);
                JsonNode identidade=google.identidade(tokens.path("access_token").asText());
                String id=identidade.path("id").asText(),email=identidade.path("email").asText();
                if(id.isBlank()||!identidade.path("verified_email").asBoolean(false))return "IDENTIDADE_INVALIDA";
                br.com.rotabrasil.service.ConfiguracaoOperacaoService.validarEmail(email);
                String refresh=tokens.path("refresh_token").asText();
                // Não reutilizar autorização de outra conta/cliente ou que já exige reconexão.
                if(refresh.isBlank()&&conectada()&&id.equals(conta.id)&&prop("client-id").equals(conta.cliente)&&"CONECTADA".equals(conta.estado))refresh=conta.refresh;
                if(refresh==null||refresh.isBlank())return "RENOVACAO_AUSENTE";
                var nova=new CofreGmail.Conta();nova.id=id;nova.email=email;nova.cliente=prop("client-id");nova.versao=UUID.randomUUID().toString();nova.refresh=refresh;nova.access=tokens.path("access_token").asText();nova.expira=Instant.now().plusSeconds(tokens.path("expires_in").asLong());nova.estado="CONECTADA";
                cofre.salvar(nova);conta=nova;proximaRenovacao=Instant.EPOCH;
                return "CONECTADA";
            }catch(EscopoInsuficiente ex){return "ESCOPO_INSUFICIENTE";}
            catch(RuntimeException ex){return "AUTORIZACAO_INVALIDA";}
        }
    }
    private static final class EscopoInsuficiente extends RuntimeException {}
    private void validarTokens(JsonNode t,boolean inicial){
        if(t.path("access_token").asText().isBlank()||!"Bearer".equalsIgnoreCase(t.path("token_type").asText())||t.path("expires_in").asLong()<=60||t.path("expires_in").asLong()>86400)throw new GoogleDocumentalClient.Falha(false);
        // Na renovação a ausência de scope significa os mesmos escopos (RFC 6749 §6).
        if(inicial||t.has("scope")){
            var scopes=new HashSet<>(Arrays.asList(t.path("scope").asText().split("\\s+")));
            if(!scopes.contains(SEND)||!(scopes.contains(EMAIL)||scopes.contains("email")))throw new EscopoInsuficiente();
        }
    }
    synchronized String accessToken(){
        carregar();
        if(!conectada()||!prop("client-id").equals(conta.cliente)||"RECONEXAO_NECESSARIA".equals(conta.estado))throw new ConflitoRecursoException("Reconecte a conta Gmail");
        if(conta.expira!=null&&conta.expira.isAfter(Instant.now().plusSeconds(60)))return conta.access;
        if(Instant.now().isBefore(proximaRenovacao))throw new ConflitoRecursoException("Renovação indisponível; aguarde antes de nova ação manual");
        try {
            if(conta.refresh==null||conta.refresh.isBlank())throw new GoogleDocumentalClient.Falha(true);
            var t=google.tokens(Map.of("grant_type","refresh_token","client_id",prop("client-id"),"client_secret",prop("client-secret"),"refresh_token",conta.refresh));
            validarTokens(t,false);
            // Mantém refresh válido se não houver rotação. Persiste a rotação antes do uso.
            var nova=json.convertValue(conta,CofreGmail.Conta.class);
            nova.access=t.path("access_token").asText();nova.expira=Instant.now().plusSeconds(t.path("expires_in").asLong());
            if(!t.path("refresh_token").asText().isBlank())nova.refresh=t.path("refresh_token").asText();
            nova.estado="CONECTADA";cofre.salvar(nova);conta=nova;return conta.access;
        }catch(RuntimeException ex){
            conta.estado=ex instanceof GoogleDocumentalClient.Falha f&&f.invalidGrant||ex instanceof EscopoInsuficiente?"RECONEXAO_NECESSARIA":"RENOVACAO_INDISPONIVEL";
            proximaRenovacao=Instant.now().plusSeconds(60);cofre.salvar(conta);
            throw new ConflitoRecursoException("Renovação Gmail indisponível; consulte Configurações e reconecte quando necessário");
        }
    }
    public synchronized boolean desconectar(){
        carregar();String token=conectada()?conta.refresh:null;
        var vazia=new CofreGmail.Conta();vazia.versao=UUID.randomUUID().toString();vazia.estado="NAO_CONECTADA";
        // Tombstone durável antes da chamada remota: invalida uso e conferências locais.
        conta=vazia;proximaRenovacao=Instant.EPOCH;cofre.salvar(vazia);
        return token==null||google.revogar(token);
    }
    public String retorno(){validarConfiguracao();return prop("url-publica")+"/painel";}
    private static String aleatorio(){byte[] b=new byte[32];new SecureRandom().nextBytes(b);return Base64.getUrlEncoder().withoutPadding().encodeToString(b);}
    @PreDestroy synchronized void fechar(){if(cofre!=null)try{cofre.close();}catch(Exception ignored){}}
}
