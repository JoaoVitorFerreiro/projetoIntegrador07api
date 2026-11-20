package br.com.rotabrasil.service.email;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.util.ReflectionTestUtils;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;

class ContaGmailDocumentalTest {
    @TempDir Path tmp;
    GoogleControlado google;ContaGmailDocumental conta;MockEnvironment env;
    ObjectMapper json=new ObjectMapper().findAndRegisterModules();
    MockHttpSession sessao;
    @BeforeEach void preparar()throws Exception{
        google=new GoogleControlado();sessao=new MockHttpSession();
        byte[] chave=new byte[32];new java.security.SecureRandom().nextBytes(chave);Files.write(tmp.resolve("chave"),chave);
        env=new MockEnvironment().withProperty("rotabrasil.email.transporte","GMAIL_API").withProperty("rotabrasil.seguranca.ativa","true");
        for(var e:Map.of("client-id","cliente-sintetico","client-secret","SECRET_SINTETICO_PRIVADO","url-publica","https://homolog.example.invalid/rota","redirect-uri","https://homolog.example.invalid/rota/api/configuracoes/gmail/callback","cofre-arquivo",tmp.resolve("conta.enc").toString(),"chave-arquivo",tmp.resolve("chave").toString()).entrySet())env.withProperty("rotabrasil.gmail."+e.getKey(),e.getValue());
        conta=new ContaGmailDocumental(env,json,google.client(json));
    }
    @AfterEach void fechar(){conta.fechar();google.close();}
    Map<String,String> iniciar(){return google.autorizar(conta.iniciar(sessao,"admin"));}
    String concluir(Map<String,String> p){return conta.concluir(sessao,"admin",p.get("state"),"codigo-sintetico",null);}
    void conectar(){assertThat(concluir(iniciar())).isEqualTo("CONECTADA");}
    CofreGmail.Conta privada(){return (CofreGmail.Conta)ReflectionTestUtils.getField(conta,"conta");}
    @Test void codeFlowPkceIdentidadeEscopoESeparacaoDoModo()throws Exception{
        var p=iniciar();assertThat(p).containsEntry("code_challenge_method","S256").containsEntry("access_type","offline").containsEntry("prompt","consent select_account").containsEntry("redirect_uri",env.getProperty("rotabrasil.gmail.redirect-uri"));
        assertThat(p.get("scope")).contains(ContaGmailDocumental.SEND,ContaGmailDocumental.EMAIL).doesNotContain("openid","mail.google.com","readonly");
        assertThat(concluir(p)).isEqualTo("CONECTADA");var e=conta.consultar();
        assertThat(e.conta()).isEqualTo("envio@example.invalid");assertThat(e.permissaoEnvio()).isTrue();assertThat(e.modo()).isEqualTo("SIMULADO");assertThat(e.transporteAtivado()).isFalse();assertThat(google.envios.get()).isZero();
        String dto=json.writeValueAsString(e);assertThat(dto).doesNotContain("ACCESS_SINTETICO","REFRESH_SINTETICO","SECRET_SINTETICO","code_verifier");
        assertThat(new String(Files.readAllBytes(tmp.resolve("conta.enc")),java.nio.charset.StandardCharsets.ISO_8859_1)).doesNotContain("REFRESH_SINTETICO","ACCESS_SINTETICO","envio@example.invalid");
    }
    @Test void stateIncorretoConsumidoEReutilizado(){var p=iniciar();assertThat(conta.concluir(sessao,"admin","invalido","code",null)).isEqualTo("STATE_INVALIDO");assertThat(concluir(p)).isEqualTo("STATE_INVALIDO");assertThat(google.trocas.get()).isZero();}
    @Test void configuracaoAusenteTemDiagnosticoProprioSemSimularExpiracaoDeSessao(){
        env.withProperty("rotabrasil.gmail.client-id","").withProperty("rotabrasil.gmail.client-secret","").withProperty("rotabrasil.gmail.redirect-uri","");
        var e=conta.consultar();assertThat(e.estado()).isEqualTo("NAO_CONFIGURADA");assertThat(e.configurado()).isFalse();assertThat(e.aviso()).contains("Integração Google ainda não configurada","Sua sessão não foi encerrada");
        assertThat(String.join(" ",e.pendencias())).contains("rotabrasil.gmail.client-id","rotabrasil.gmail.client-secret","rotabrasil.gmail.redirect-uri");
        assertThatThrownBy(()->conta.iniciar(sessao,"admin")).isInstanceOf(br.com.rotabrasil.exception.ConflitoRecursoException.class);
        assertThat(google.trocas.get()).isZero();
    }
    @Test void callbackValidoNaoPodeSerReutilizado(){var p=iniciar();assertThat(concluir(p)).isEqualTo("CONECTADA");assertThat(concluir(p)).isEqualTo("STATE_INVALIDO");assertThat(google.trocas.get()).isEqualTo(1);}
    @Test void sessaoDiferenteExpiradaEAtorDiferente(){var p=iniciar();assertThat(conta.concluir(new MockHttpSession(),"admin",p.get("state"),"code",null)).isEqualTo("STATE_INVALIDO");assertThat(conta.concluir(null,"admin",p.get("state"),"code",null)).isEqualTo("SESSAO_INVALIDA");assertThat(conta.concluir(sessao,"outro-admin",p.get("state"),"code",null)).isEqualTo("STATE_INVALIDO");assertThat(google.trocas.get()).isZero();}
    @Test void stateExpirado(){var p=iniciar();ReflectionTestUtils.setField(sessao.getAttribute(ContaGmailDocumental.PENDENTE),"expira",Instant.now().minusSeconds(1));assertThat(concluir(p)).isEqualTo("EXPIRADA");assertThat(google.trocas.get()).isZero();}
    @Test void pkceInvalidoRecusadoNoServidorControlado(){var p=iniciar();google.challenge="outro";assertThat(concluir(p)).isEqualTo("AUTORIZACAO_INVALIDA");assertThat(conta.consultar().conta()).isNull();}
    @Test void negadoIncompletoEEscopoInsuficiente(){var p=iniciar();assertThat(conta.concluir(sessao,"admin",p.get("state"),null,"access_denied")).isEqualTo("CONSENTIMENTO_NEGADO");p=iniciar();assertThat(conta.concluir(sessao,"admin",p.get("state"),null,null)).isEqualTo("INCOMPLETA");google.scope=ContaGmailDocumental.EMAIL;assertThat(concluir(iniciar())).isEqualTo("ESCOPO_INSUFICIENTE");}
    @Test void identidadeNaoVerificadaETokensAusentes(){google.identidadeVerificada=false;assertThat(concluir(iniciar())).isEqualTo("IDENTIDADE_INVALIDA");google.identidadeVerificada=true;google.tokenValido=false;assertThat(concluir(iniciar())).isEqualTo("AUTORIZACAO_INVALIDA");}
    @Test void refreshAusenteExigeConsentimentoSemSubstituirContaExistente(){google.refresh=false;assertThat(concluir(iniciar())).isEqualTo("RENOVACAO_AUSENTE");google.refresh=true;conectar();String assinatura=conta.assinatura();google.email="outra@example.invalid";google.id="outra-id";google.refresh=false;assertThat(concluir(iniciar())).isEqualTo("RENOVACAO_AUSENTE");assertThat(conta.assinatura()).isEqualTo(assinatura);}
    @Test void consentimentoMesmaContaSemNovoRefreshPreservaValido(){conectar();String v=conta.consultar().autorizacaoVersao();google.refresh=false;assertThat(concluir(iniciar())).isEqualTo("CONECTADA");assertThat(privada().refresh).isEqualTo("REFRESH_SINTETICO_PRIVADO");assertThat(conta.consultar().autorizacaoVersao()).isNotEqualTo(v);}
    @Test void renovacaoConcorrenteUnicaNaoInvalidaConferenciasENaoApagaRefresh()throws Exception{
        conectar();String assinatura=conta.assinatura();privada().expira=Instant.EPOCH;
        try(var pool=Executors.newFixedThreadPool(8)){var trabalhos=new ArrayList<Callable<String>>();for(int i=0;i<8;i++)trabalhos.add(conta::accessToken);for(var f:pool.invokeAll(trabalhos))assertThat(f.get()).isEqualTo("ACCESS_SINTETICO_PRIVADO");}
        assertThat(google.renovacoes.get()).isEqualTo(1);assertThat(privada().refresh).isEqualTo("REFRESH_SINTETICO_PRIVADO");assertThat(conta.assinatura()).isEqualTo(assinatura);
        google.rotacionar=true;privada().expira=Instant.EPOCH;conta.accessToken();assertThat(privada().refresh).isEqualTo("REFRESH_ROTACIONADO_PRIVADO");
    }
    @Test void invalidGrantExigeReconexaoSemLoop(){conectar();privada().expira=Instant.EPOCH;google.invalidGrant=true;assertThatThrownBy(conta::accessToken).hasMessageContaining("Renovação");assertThat(conta.consultar().estado()).isEqualTo("RECONEXAO_NECESSARIA");assertThatThrownBy(conta::accessToken).hasMessageContaining("Reconecte");assertThat(google.renovacoes.get()).isEqualTo(1);}
    @Test void falhaTemporariaTemCooldownENaoApagaRefresh(){conectar();privada().expira=Instant.EPOCH;google.tokenValido=false;assertThatThrownBy(conta::accessToken).hasMessageContaining("Renovação");assertThatThrownBy(conta::accessToken).hasMessageContaining("aguarde");assertThat(conta.consultar().estado()).isEqualTo("RENOVACAO_INDISPONIVEL");assertThat(privada().refresh).isEqualTo("REFRESH_SINTETICO_PRIVADO");assertThat(google.renovacoes.get()).isEqualTo(1);}
    @Test void desconexaoInvalidaLocalAntesDeRevogarEImpedeCallbackAntigo(){conectar();var p=iniciar();String assinatura=conta.assinatura();google.statusRevogacao=503;assertThat(conta.desconectar()).isFalse();assertThat(conta.consultar().estado()).isEqualTo("NAO_CONECTADA");assertThat(conta.assinatura()).isNotEqualTo(assinatura);assertThat(concluir(p)).isEqualTo("CONTA_ALTERADA");assertThatThrownBy(conta::accessToken).hasMessageContaining("Reconecte");}
    @Test void substituicaoSoDepoisDeValidarENaoRevogaAutorizacaoRecemCriada(){conectar();String assinatura=conta.assinatura();var p=iniciar();assertThat(conta.assinatura()).isEqualTo(assinatura);google.email="nova@example.invalid";google.id="nova-id";assertThat(concluir(p)).isEqualTo("CONECTADA");assertThat(conta.remetente()).isEqualTo("nova@example.invalid");assertThat(conta.assinatura()).isNotEqualTo(assinatura);assertThat(google.revogacoes.get()).isZero();}
    @Test void chaveErradaOuCofreAdulteradoFalhaFechado()throws Exception{conectar();conta.fechar();byte[] bytes=Files.readAllBytes(tmp.resolve("conta.enc"));bytes[bytes.length-1]^=1;Files.write(tmp.resolve("conta.enc"),bytes);conta=new ContaGmailDocumental(env,json,google.client(json));assertThat(conta.consultar().estado()).isEqualTo("INDISPONIVEL");assertThatThrownBy(()->conta.iniciar(sessao,"admin")).hasMessageContaining("autenticado");}
    @Test void cofreRecuperaNoReinicioERecusaDoisProcessos(){conectar();var outra=new ContaGmailDocumental(env,json,google.client(json));try{assertThat(outra.consultar().estado()).isEqualTo("INDISPONIVEL");}finally{outra.fechar();}String v=conta.assinatura();conta.fechar();conta=new ContaGmailDocumental(env,json,google.client(json));assertThat(conta.assinatura()).isEqualTo(v);assertThat(conta.accessToken()).isEqualTo("ACCESS_SINTETICO_PRIVADO");}
    @Test void redirectFixoRaizOuRotaRecusaInjecaoEHttp(){
        env.withProperty("rotabrasil.gmail.redirect-uri","https://atacante.example.invalid/callback");assertThat(conta.consultar().configurado()).isFalse();assertThatThrownBy(()->conta.iniciar(sessao,"admin")).hasMessageContaining("redirect URI");
        env.withProperty("rotabrasil.gmail.url-publica","https://homolog.example.invalid").withProperty("rotabrasil.gmail.redirect-uri","https://homolog.example.invalid/api/configuracoes/gmail/callback");
        assertThat(iniciar()).containsEntry("redirect_uri","https://homolog.example.invalid/api/configuracoes/gmail/callback");
        env.withProperty("rotabrasil.gmail.url-publica","http://homolog.example.invalid").withProperty("rotabrasil.gmail.redirect-uri","http://homolog.example.invalid/api/configuracoes/gmail/callback");assertThatThrownBy(()->conta.iniciar(sessao,"admin")).hasMessageContaining("HTTPS");
    }
}
