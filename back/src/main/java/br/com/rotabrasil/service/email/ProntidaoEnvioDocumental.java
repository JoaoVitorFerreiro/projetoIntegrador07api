package br.com.rotabrasil.service.email;

import br.com.rotabrasil.exception.ConflitoRecursoException;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import java.util.*;

/** Diagnóstico local, sem conexão SMTP e independente dos canais motorista/teste Gmail. */
@Service
public class ProntidaoEnvioDocumental {
    public record Estado(String modo, boolean confirmacaoRealDisponivel, List<String> bloqueios,
                         String provedor, String autenticacao, boolean autenticacaoVerificada,
                         boolean remetenteSalvo, boolean remetenteSmtpConfigurado, boolean credencialDisponivel,
                         boolean transporteAtivado, boolean sessaoProtegida, java.time.OffsetDateTime autenticacaoComprovadaEm) {}
    private final Environment env;
    private final JdbcTemplate jdbc;
    @org.springframework.beans.factory.annotation.Autowired private ContaGmailDocumental gmail;
    public ProntidaoEnvioDocumental(Environment env, JdbcTemplate jdbc) { this.env=env; this.jdbc=jdbc; }
    public Estado consultar() {
        if(gmail!=null&&gmail.selecionado())return consultarGmail();
        String modo=env.getProperty("rotabrasil.email.modo","SIMULADO");
        String salvo=jdbc.queryForObject("SELECT remetente FROM configuracao_operacao WHERE id=1",String.class);
        String smtp=env.getProperty("rotabrasil.email.smtp.remetente","");
        String host=env.getProperty("rotabrasil.email.smtp.host","");
        String autenticacao=env.getProperty("rotabrasil.email.smtp.autenticacao","BASIC");
        boolean credencial=autenticacao.equals("BASIC")&&!env.getProperty("rotabrasil.email.smtp.senha","").isBlank();
        var faltas=new ArrayList<String>();
        if (!"REAL".equals(modo)) faltas.add("Transporte real não configurado: modo documental SIMULADO. A preparação não envia e-mail.");
        if (salvo==null||salvo.isBlank()) faltas.add("Configure o remetente da operação antes de confirmar envio real.");
        if (smtp.isBlank()) faltas.add("Remetente SMTP externo não configurado. Salvar endereço no painel não autentica a conta.");
        else if (salvo!=null&&!salvo.isBlank()&&!salvo.equalsIgnoreCase(smtp)) faltas.add("O remetente salvo difere da conta de envio SMTP. Solicite ao administrador que alinhe os endereços.");
        if (host.isBlank()) faltas.add("Host SMTP documental ausente: configure rotabrasil.email.smtp.host externamente (smtp.gmail.com para Gmail).");
        if (env.getProperty("rotabrasil.email.smtp.usuario","").isBlank()) faltas.add("Usuário SMTP documental ausente: configure rotabrasil.email.smtp.usuario com a conta autorizada.");
        if (!Set.of("BASIC","XOAUTH2").contains(autenticacao)) faltas.add("Autenticação SMTP documental não suportada: configure BASIC (senha de aplicativo Gmail) ou XOAUTH2 externamente.");
        else if (autenticacao.equals("BASIC")&&env.getProperty("rotabrasil.email.smtp.senha","").isBlank()) faltas.add("Credencial SMTP documental ausente: rotabrasil.email.smtp.senha deve ser provisionada externamente; para Gmail use somente senha de aplicativo autorizada, nunca a senha normal.");
        if (autenticacao.equals("XOAUTH2")) {
            try { TokenSmtpExterno.ler(env.getProperty("rotabrasil.email.smtp.oauth2.token-arquivo",""));credencial=true; }
            catch (RuntimeException ex) { faltas.add("Autorização SMTP externa indisponível ou expirada. Provisione ou renove rotabrasil.email.smtp.oauth2.token-arquivo no servidor Java."); }
        }
        if ("REAL".equals(modo)) {
            if(env.getProperty("rotabrasil.email.smtp.credencial-versao","").isBlank())faltas.add("Informe rotabrasil.email.smtp.credencial-versao: identificador não secreto da autorização externa. Alterá-lo exige nova conferência.");
            if (!env.getProperty("rotabrasil.email.smtp.ativado",Boolean.class,false)) faltas.add("Transporte SMTP documental sem ativação externa explícita.");
            if (!env.getProperty("rotabrasil.seguranca.ativa",Boolean.class,false)) faltas.add("Envio real exige sessão protegida e CSRF.");
        }
        // Somente aceite REAL da MESMA configuração/autorização é evidência histórica.
        // Nunca promove SIMULADO, assinatura antiga ou presença de segredo a autenticação.
        var aceites="REAL".equals(modo)&&!env.getProperty("rotabrasil.email.smtp.credencial-versao","").isBlank()
                ?jdbc.query("SELECT concluida_em FROM tentativas_email WHERE modo='REAL' AND situacao='ACEITO_SMTP' AND snapshot LIKE ? AND concluida_em IS NOT NULL ORDER BY concluida_em DESC LIMIT 1",
                    (rs,n)->rs.getObject(1,java.time.OffsetDateTime.class),"%\"configuracaoSha256\":\""+assinatura()+"\"%")
                :List.<java.time.OffsetDateTime>of();
        var comprovada=aceites.isEmpty()?null:aceites.getFirst();
        return new Estado(modo,faltas.isEmpty(),List.copyOf(faltas),host.isBlank()?"NAO_CONFIGURADO":host.equalsIgnoreCase("smtp.gmail.com")?"GMAIL":"SMTP_EXTERNO",Set.of("BASIC","XOAUTH2").contains(autenticacao)?autenticacao:"NAO_SUPORTADA",comprovada!=null,
                salvo!=null&&!salvo.isBlank(),!smtp.isBlank(),credencial,env.getProperty("rotabrasil.email.smtp.ativado",Boolean.class,false),env.getProperty("rotabrasil.seguranca.ativa",Boolean.class,false),comprovada);
    }
    /** Não inclui senha, token, hash de segredo ou caminho privado. Renovação do mesmo token não muda a identidade. */
    public String assinatura() {
        if(gmail!=null&&gmail.selecionado())return br.com.rotabrasil.service.ConteudoXml.sha256(gmail.assinatura().getBytes(java.nio.charset.StandardCharsets.UTF_8));
        var valores=List.of(env.getProperty("rotabrasil.email.modo","SIMULADO"),env.getProperty("rotabrasil.email.smtp.host",""),
                env.getProperty("rotabrasil.email.smtp.porta","587"),env.getProperty("rotabrasil.email.smtp.usuario",""),env.getProperty("rotabrasil.email.smtp.remetente",""),
                env.getProperty("rotabrasil.email.smtp.autenticacao","BASIC"),env.getProperty("rotabrasil.email.smtp.credencial-versao",""),
                env.getProperty("rotabrasil.email.smtp.ativado","false"),env.getProperty("rotabrasil.seguranca.ativa","false"));
        return br.com.rotabrasil.service.ConteudoXml.sha256(valores.stream().map(v->v.length()+":"+v).collect(java.util.stream.Collectors.joining()).getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }
    public void validarConfirmacaoReal() {
        var estado=consultar();
        if (!estado.confirmacaoRealDisponivel()) throw new ConflitoRecursoException("Confirmação real bloqueada: "+String.join(" ",estado.bloqueios()));
    }
    public String remetente(String smtp){return gmail!=null&&gmail.selecionado()?gmail.remetente():smtp;}
    public <T> T exclusivo(java.util.function.Supplier<T> acao){return gmail!=null&&gmail.selecionado()?gmail.exclusivo(acao):acao.get();}
    private Estado consultarGmail(){
        var g=gmail.consultar();var faltas=new ArrayList<String>();
        if(!"REAL".equals(g.modo()))faltas.add("Modo documental SIMULADO; conectar Google não ativa envios reais.");
        if(!g.configurado())faltas.add("Configuração externa ou cofre Gmail indisponível. Solicite a um ADMIN.");
        if(!gmail.disponivelParaTentativa())faltas.add("Conta Gmail: "+g.estado()+". Consulte Configurações; aguarde em falha temporária ou solicite reconexão ao ADMIN.");
        if(!g.transporteAtivado())faltas.add("Envio Gmail API sem ativação externa explícita (rotabrasil.gmail.envio-ativado).");
        boolean segura=env.getProperty("rotabrasil.seguranca.ativa",Boolean.class,false);
        if(!segura)faltas.add("Envio real exige sessão protegida e CSRF.");
        String banco=env.getProperty("spring.datasource.url","");
        if(banco.startsWith("jdbc:h2:file:")&&!java.util.regex.Pattern.compile("(?i)(^|;)WRITE_DELAY\\s*=\\s*0(;|$)").matcher(banco).find())faltas.add("Gmail real com H2 em arquivo exige WRITE_DELAY=0.");
        var aceites=jdbc.query("SELECT concluida_em FROM tentativas_email WHERE modo='REAL' AND situacao='ACEITO_API' AND snapshot LIKE ? AND concluida_em IS NOT NULL ORDER BY concluida_em DESC LIMIT 1",(rs,n)->rs.getObject(1,java.time.OffsetDateTime.class),"%\"configuracaoSha256\":\""+assinatura()+"\"%");
        var ultima=aceites.isEmpty()?null:aceites.getFirst();
        return new Estado(g.modo(),faltas.isEmpty(),List.copyOf(faltas),"GMAIL_API","OAUTH2",ultima!=null,g.conta()!=null,g.conta()!=null,g.permissaoEnvio(),g.transporteAtivado(),segura,ultima);
    }
}
