package br.com.rotabrasil;

import br.com.rotabrasil.service.email.*;
import com.fasterxml.jackson.databind.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.*;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.context.annotation.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.*;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.*;
import org.springframework.test.web.servlet.*;
import org.springframework.web.bind.annotation.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT,properties={"server.address=127.0.0.1","server.servlet.context-path=/rota","server.servlet.session.cookie.same-site=lax","spring.datasource.url=jdbc:h2:mem:gmail-oauth;DB_CLOSE_DELAY=-1","spring.flyway.enabled=true","rotabrasil.seguranca.ativa=true","rotabrasil.email.modo=REAL","rotabrasil.email.transporte=GMAIL_API","rotabrasil.gmail.envio-ativado=true","rotabrasil.notificacoes.modo=SIMULADO"})
@AutoConfigureMockMvc @DirtiesContext(classMode=DirtiesContext.ClassMode.AFTER_CLASS)
@org.junit.jupiter.api.extension.ExtendWith(org.springframework.boot.test.system.OutputCaptureExtension.class)
class GmailDocumentalHttpTest {
    static final Path TMP=CentralEnviosRealHttpTest.temporario();
    static final GoogleControlado GOOGLE=new GoogleControlado();
    @TestConfiguration static class Controlado {
        @Bean @Primary GoogleDocumentalClient googleControlado(ObjectMapper json){return GOOGLE.client(json);}
        @Bean FixtureController fixtureController(ContaGmailDocumental conta){return new FixtureController(conta);}
    }
    /** Exclusivo do classpath de testes para o roteiro de navegador. */
    @RestController static class FixtureController {
        final ContaGmailDocumental conta;
        FixtureController(ContaGmailDocumental conta){this.conta=conta;}
        @PostMapping("/__fixture/google") Map<String,Object> fixture(@RequestBody Map<String,String> pedido)throws Exception{
            if(pedido.containsKey("url"))GOOGLE.autorizar(pedido.get("url"));
            if(pedido.containsKey("estado")){
                Object c=org.springframework.test.util.ReflectionTestUtils.getField(conta,"conta");
                org.springframework.test.util.ReflectionTestUtils.setField(c,"estado",pedido.get("estado"));
            }
            return Map.of("envios",GOOGLE.envios.get());
        }
    }
    @DynamicPropertySource static void props(DynamicPropertyRegistry r)throws Exception{
        byte[] chave=new byte[32];new java.security.SecureRandom().nextBytes(chave);Files.write(TMP.resolve("gmail.key"),chave);
        CentralEnviosRealHttpTest.prepararTls(TMP);
        r.add("server.ssl.enabled",()->true);r.add("server.ssl.key-store",()->TMP.resolve("tls.p12").toString());r.add("server.ssl.key-store-password",()->"SenhaTLSApenasFixture12!");
        r.add("rotabrasil.arquivos.viagens",()->TMP.resolve("acervo").toString());
        r.add("rotabrasil.seguranca.admin-usuario",()->"admin");r.add("rotabrasil.seguranca.admin-hash",()->new org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder(4).encode("SenhaSomenteFixture12!"));
        for(var e:Map.of("client-id","cliente-sintetico","client-secret","SECRET_SINTETICO_PRIVADO","url-publica","https://homolog.example.invalid/rota","redirect-uri","https://homolog.example.invalid/rota/api/configuracoes/gmail/callback","cofre-arquivo",TMP.resolve("gmail.enc").toString(),"chave-arquivo",TMP.resolve("gmail.key").toString()).entrySet())r.add("rotabrasil.gmail."+e.getKey(),e::getValue);
    }
    @Autowired MockMvc mvc;@Autowired ObjectMapper json;@Autowired JdbcTemplate jdbc;@Autowired ContaGmailDocumental conta;
    @Autowired org.springframework.core.env.ConfigurableEnvironment env;
    @org.springframework.boot.test.web.server.LocalServerPort int porta;
    long viagem,documento;
    @BeforeEach void preparar()throws Exception{
        jdbc.update("UPDATE conferencias_email SET anterior_id=NULL");jdbc.update("DELETE FROM tentativas_email WHERE anterior_id IS NOT NULL");jdbc.update("DELETE FROM tentativas_email");jdbc.update("DELETE FROM conferencias_email");
        jdbc.update("DELETE FROM usuarios");for(String nome:List.of("admin","operador","leitor"))jdbc.update("INSERT INTO usuarios(usuario,senha_hash,papel,criado_em) VALUES(?,?,?,CURRENT_TIMESTAMP)",nome,"hash-sintetico",nome.equals("leitor")?"CONSULTA":nome.equals("admin")?"ADMIN":"OPERADOR");
        GOOGLE.invalidGrant=false;GOOGLE.tokenValido=true;GOOGLE.refresh=true;GOOGLE.statusEnvio=200;GOOGLE.respostaPerdida=false;GOOGLE.statusRevogacao=200;GOOGLE.email="envio@example.invalid";GOOGLE.id="id-sintetico";
        conta.desconectar();GOOGLE.envios.set(0);
        GOOGLE.antesEnviar=()->assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM tentativas_email WHERE situacao='DESCONHECIDO'",Integer.class)).isPositive();
        var r=mvc.perform(multipart("/api/processamento/xml").file(new MockMultipartFile("arquivo","gmail.xml","application/xml",CteFixture.xml().getBytes(java.nio.charset.StandardCharsets.UTF_8))).with(user("operador").roles("OPERADOR")).with(csrf())).andExpect(status().isOk()).andReturn();
        var d=json.readTree(r.getResponse().getContentAsString());viagem=d.path("viagemId").asLong();documento=d.path("documentoId").asLong();
    }
    @AfterAll static void fechar(){GOOGLE.close();}
    MockHttpSession conectar()throws Exception{
        var session=new MockHttpSession();String url=json.readTree(mvc.perform(post("/api/configuracoes/gmail/conectar").session(session).with(user("admin").roles("ADMIN")).with(csrf())).andExpect(status().isOk()).andReturn().getResponse().getContentAsString()).path("url").asText();
        var p=GOOGLE.autorizar(url);
        mvc.perform(get("/api/configuracoes/gmail/callback").session(session).with(user("admin").roles("ADMIN")).param("state",p.get("state")).param("code","codigo-sintetico")).andExpect(status().isSeeOther()).andExpect(header().string("Location",org.hamcrest.Matchers.containsString("gmail=CONECTADA")));
        return session;
    }
    String rota(String s){return "/api/viagens/"+viagem+"/"+s;}
    String conferir()throws Exception{return json.readTree(mvc.perform(post(rota("conferencias-email")).with(user("operador").roles("OPERADOR")).with(csrf()).contentType("application/json").content(json.writeValueAsString(Map.of("selecao",Map.of("destinatario","cliente@example.invalid","documentoIds",List.of(documento)))))).andExpect(status().isOk()).andExpect(jsonPath("$.snapshot.remetente").value("envio@example.invalid")).andReturn().getResponse().getContentAsString()).path("id").asText();}
    ResultActions confirmar(String id)throws Exception{return mvc.perform(post(rota("tentativas-email")).with(user("operador").roles("OPERADOR")).with(csrf()).contentType("application/json").content(json.writeValueAsString(Map.of("conferenciaId",id,"confirmado",true))));}
    @Test void adminObrigatorioCsrfInicioEDesconexaoESessaoCallback()throws Exception{
        mvc.perform(get("/api/configuracoes/gmail")).andExpect(status().isUnauthorized());
        for(String role:List.of("OPERADOR","CONSULTA"))for(String acao:List.of("conectar","desconectar"))mvc.perform(post("/api/configuracoes/gmail/"+acao).with(user(role.equals("OPERADOR")?"operador":"leitor").roles(role)).with(csrf())).andExpect(status().isForbidden());
        for(String acao:List.of("conectar","desconectar"))mvc.perform(post("/api/configuracoes/gmail/"+acao).with(user("admin").roles("ADMIN"))).andExpect(status().isForbidden());
        mvc.perform(get("/api/configuracoes/gmail/callback").param("state","errado").param("code","code")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/configuracoes/gmail/callback").with(user("operador").roles("OPERADOR"))).andExpect(status().isForbidden());
        assertThat(GOOGLE.envios.get()).isZero();
    }
    @Test void adminSemCredenciaisGoogleConsulta200EInicio409SemEncerrarSessao()throws Exception{
        var fonte=new org.springframework.core.env.MapPropertySource("google-nao-configurado",Map.of("rotabrasil.gmail.client-id","","rotabrasil.gmail.client-secret","","rotabrasil.gmail.redirect-uri",""));
        env.getPropertySources().addFirst(fonte);
        try{
            mvc.perform(get("/api/configuracoes/gmail").with(user("admin").roles("ADMIN"))).andExpect(status().isOk()).andExpect(jsonPath("$.estado").value("NAO_CONFIGURADA")).andExpect(jsonPath("$.configurado").value(false)).andExpect(jsonPath("$.aviso").value(org.hamcrest.Matchers.containsString("Integração Google ainda não configurada")));
            mvc.perform(post("/api/configuracoes/gmail/conectar").with(user("admin").roles("ADMIN")).with(csrf())).andExpect(status().isConflict());
            mvc.perform(post("/api/configuracoes/gmail/conectar").with(user("admin").roles("ADMIN"))).andExpect(status().isForbidden());
            mvc.perform(get("/api/sessao").with(user("admin").roles("ADMIN"))).andExpect(status().isOk()).andExpect(jsonPath("$.usuario").value("admin")).andExpect(jsonPath("$.csrf").isNotEmpty());
        }finally{env.getPropertySources().remove(fonte.getName());}
        assertThat(GOOGLE.envios.get()).isZero();
    }
    @Test void conexaoNaoEnviaEConfirmacaoApiMimeIdempotenciaHistorico(org.springframework.boot.test.system.CapturedOutput output)throws Exception{
        conectar();assertThat(GOOGLE.envios.get()).isZero();String id=conferir();assertThat(GOOGLE.envios.get()).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM usuarios",Integer.class)).isEqualTo(3);
        assertThat(jdbc.queryForObject("SELECT papel FROM usuarios WHERE usuario='operador'",String.class)).isEqualTo("OPERADOR");
        confirmar(id).andExpect(status().isOk()).andExpect(jsonPath("$.situacao").value("ACEITO_API")).andExpect(jsonPath("$.motivo").value(org.hamcrest.Matchers.containsString("não comprova entrega")));
        confirmar(id).andExpect(status().isOk()).andExpect(jsonPath("$.situacao").value("ACEITO_API"));assertThat(GOOGLE.envios.get()).isEqualTo(1);
        assertThat(GOOGLE.mime).contains("From: envio@example.invalid","To: cliente@example.invalid","multipart/mixed","application/xml");
        var mime=new jakarta.mail.internet.MimeMessage(jakarta.mail.Session.getInstance(new Properties()),new java.io.ByteArrayInputStream(GOOGLE.mime.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        var partes=(jakarta.mail.Multipart)mime.getContent();byte[] anexo=partes.getBodyPart(1).getInputStream().readAllBytes();
        var snapshot=json.readTree(jdbc.queryForObject("SELECT snapshot FROM tentativas_email WHERE conferencia_id=?",String.class,id));
        assertThat(br.com.rotabrasil.service.ConteudoXml.sha256(anexo)).isEqualTo(snapshot.path("arquivos").get(0).path("sha256").asText());
        var diagnostico=mvc.perform(get("/api/configuracoes/gmail").with(user("admin").roles("ADMIN"))).andExpect(status().isOk()).andExpect(jsonPath("$.ultimaTentativa").value("ACEITO_API")).andReturn().getResponse().getContentAsString();
        assertThat(diagnostico).doesNotContain("ACCESS_SINTETICO","REFRESH_SINTETICO","SECRET_SINTETICO","gmail.key","gmail.enc");
        assertThat(jdbc.queryForObject("SELECT snapshot FROM tentativas_email WHERE conferencia_id=?",String.class,id)).doesNotContain("ACCESS_SINTETICO","REFRESH_SINTETICO","SECRET_SINTETICO");
        mvc.perform(get("/api/envio-documental").with(user("operador").roles("OPERADOR"))).andExpect(jsonPath("$.provedor").value("GMAIL_API")).andExpect(jsonPath("$.autenticacaoVerificada").value(true));
        assertThat(output.getAll()).doesNotContain("ACCESS_SINTETICO_PRIVADO","REFRESH_SINTETICO_PRIVADO","SECRET_SINTETICO_PRIVADO","codigo-sintetico");
    }
    @Test void trocaEDesconexaoExigemNovaConferenciaPreservamRecuperacao()throws Exception{
        conectar();String antiga=conferir();GOOGLE.email="nova@example.invalid";GOOGLE.id="nova-id";conectar();
        confirmar(antiga).andExpect(status().isConflict());assertThat(GOOGLE.envios.get()).isZero();
        // A conferência antiga conserva sua identidade e assinatura; nunca é reescrita.
        assertThat(jdbc.queryForObject("SELECT snapshot FROM conferencias_email WHERE id=?",String.class,antiga)).contains("envio@example.invalid");
        conta.desconectar();confirmar(antiga).andExpect(status().isConflict());assertThat(GOOGLE.envios.get()).isZero();
    }
    @Test void confirmacoesConcorrentesUmUnicoPostAoProvedor()throws Exception{
        conectar();String id=conferir();try(var pool=Executors.newFixedThreadPool(2)){
            var a=pool.submit(()->confirmar(id).andReturn().getResponse().getContentAsString());var b=pool.submit(()->confirmar(id).andReturn().getResponse().getContentAsString());assertThat(a.get(20,TimeUnit.SECONDS)).isEqualTo(b.get(20,TimeUnit.SECONDS));
        }assertThat(GOOGLE.envios.get()).isEqualTo(1);
    }
    @Test void respostaPerdidaOu5xxDesconhecidoNaoRepete()throws Exception{
        conectar();String id=conferir();GOOGLE.respostaPerdida=true;confirmar(id).andExpect(jsonPath("$.situacao").value("DESCONHECIDO"));confirmar(id).andExpect(jsonPath("$.situacao").value("DESCONHECIDO"));assertThat(GOOGLE.envios.get()).isEqualTo(1);
    }
    @Test void recusaComprovadaDaApiNaoSeConfundeComAceite()throws Exception{
        conectar();GOOGLE.statusEnvio=403;confirmar(conferir()).andExpect(jsonPath("$.situacao").value("FALHA"));assertThat(GOOGLE.envios.get()).isEqualTo(1);
    }
    @Test void erro5xxNaoPresumeRecusaNemRepeteEnvio()throws Exception{
        conectar();GOOGLE.statusEnvio=503;String id=conferir();confirmar(id).andExpect(jsonPath("$.situacao").value("DESCONHECIDO"));confirmar(id).andExpect(jsonPath("$.situacao").value("DESCONHECIDO"));assertThat(GOOGLE.envios.get()).isEqualTo(1);
    }
    @Test void tokenRecusadoPelaApiExigeReconexaoSemTentarRenovarOuReenviar()throws Exception{
        conectar();GOOGLE.statusEnvio=401;String id=conferir();confirmar(id).andExpect(jsonPath("$.situacao").value("FALHA"));
        assertThat(conta.consultar().estado()).isEqualTo("RECONEXAO_NECESSARIA");confirmar(id).andExpect(jsonPath("$.situacao").value("FALHA"));assertThat(GOOGLE.envios.get()).isEqualTo(1);
    }
    @Test void remetenteArbitrarioRecusadoEIdentidadeValidadaPreservada()throws Exception{
        conectar();long versao=jdbc.queryForObject("SELECT versao FROM configuracao_operacao WHERE id=1",Long.class);
        mvc.perform(put("/api/configuracoes").with(user("admin").roles("ADMIN")).with(csrf()).contentType("application/json").content(json.writeValueAsString(Map.of("versao",versao,"remetente","fraude@example.invalid","subpasta","","exportacaoAutomatica",false)))).andExpect(status().isConflict());
        assertThat(conta.remetente()).isEqualTo("envio@example.invalid");assertThat(GOOGLE.envios.get()).isZero();
    }
    @Test void historicoSmtpAnteriorPermaneceLegivel()throws Exception{
        conectar();String id=conferir();confirmar(id).andExpect(status().isOk());jdbc.update("UPDATE tentativas_email SET situacao='ACEITO_SMTP',motivo='Aceite SMTP histórico' WHERE conferencia_id=?",id);
        conta.desconectar();confirmar(id).andExpect(jsonPath("$.situacao").value("ACEITO_SMTP"));assertThat(GOOGLE.envios.get()).isEqualTo(1);
    }
    @Test @org.junit.jupiter.api.condition.EnabledIfSystemProperty(named="rotabrasil.browser",matches="true")
    void navegadorComGoogleControlado()throws Exception{
        String hash=new org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder(4).encode("SenhaSomenteFixture12!");jdbc.update("UPDATE usuarios SET senha_hash=?",hash);
        var props=new org.springframework.core.env.MapPropertySource("url-browser-google",Map.of("rotabrasil.gmail.url-publica","https://127.0.0.1:"+porta+"/rota","rotabrasil.gmail.redirect-uri","https://127.0.0.1:"+porta+"/rota/api/configuracoes/gmail/callback"));env.getPropertySources().addFirst(props);
        Path saida=Path.of(System.getProperty("rotabrasil.browser.output",System.getProperty("java.io.tmpdir")+"/gmail-oauth-browser"));Files.createDirectories(saida);
        var pb=new ProcessBuilder("node",Path.of("../scripts/testar-google-documental.cjs").toAbsolutePath().normalize().toString(),"https://127.0.0.1:"+porta+"/rota",saida.toString(),String.valueOf(viagem),TMP.resolve("publico.cer").toString()).redirectErrorStream(true).redirectOutput(saida.resolve("playwright.log").toFile());
        pb.environment().put("NODE_EXTRA_CA_CERTS",TMP.resolve("publico.cer").toString());pb.environment().put("ROTABRASIL_TESTE_GOOGLE_CONTROLADO","1");var p=pb.start();
        try{assertThat(p.waitFor(180,TimeUnit.SECONDS)).isTrue();assertThat(p.exitValue()).isZero();}finally{if(p.isAlive())p.destroyForcibly();env.getPropertySources().remove(props.getName());}
    }
}
