package br.com.rotabrasil;

import br.com.rotabrasil.service.email.ProntidaoEnvioDocumental;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.env.MockEnvironment;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class ProntidaoEnvioDocumentalTest {
    @Test void enderecoSalvoNaoEAutenticacaoEListaPropriedadesDocumentaisFaltantes(){
        var env=new MockEnvironment();var jdbc=mock(JdbcTemplate.class);
        when(jdbc.queryForObject(anyString(),eq(String.class))).thenReturn("conta@example.invalid");
        var estado=new ProntidaoEnvioDocumental(env,jdbc).consultar();
        assertThat(estado.confirmacaoRealDisponivel()).isFalse();assertThat(estado.autenticacaoVerificada()).isFalse();
        assertThat(String.join(" ",estado.bloqueios())).contains("SIMULADO","rotabrasil.email.smtp.host","rotabrasil.email.smtp.usuario","rotabrasil.email.smtp.senha").doesNotContain("Microsoft","OUTLOOK");
    }
    @Test void gmailDocumentalNaoDependeDeTipoContaMicrosoftOuTesteAdministrativo(){
        var env=new MockEnvironment().withProperty("rotabrasil.email.modo","REAL").withProperty("rotabrasil.email.smtp.ativado","true").withProperty("rotabrasil.seguranca.ativa","true")
                .withProperty("rotabrasil.email.smtp.host","smtp.gmail.com").withProperty("rotabrasil.email.smtp.usuario","conta@example.invalid").withProperty("rotabrasil.email.smtp.remetente","conta@example.invalid")
                .withProperty("rotabrasil.email.smtp.senha","CREDENCIAL_SOMENTE_FIXTURE").withProperty("rotabrasil.email.smtp.credencial-versao","fixture-v1").withProperty("rotabrasil.notificacoes.tipo-conta","NAO_IDENTIFICADA");
        var jdbc=mock(JdbcTemplate.class);when(jdbc.queryForObject(anyString(),eq(String.class))).thenReturn("conta@example.invalid");
        var estado=new ProntidaoEnvioDocumental(env,jdbc).consultar();
        assertThat(estado.provedor()).isEqualTo("GMAIL");assertThat(estado.confirmacaoRealDisponivel()).isTrue();assertThat(estado.autenticacaoVerificada()).isFalse();
        assertThat(estado.toString()).doesNotContain("CREDENCIAL_SOMENTE_FIXTURE");
        assertThat(estado.credencialDisponivel()).isTrue();assertThat(estado.transporteAtivado()).isTrue();assertThat(estado.sessaoProtegida()).isTrue();
    }
    @Test void falhaDeConsultaNaoSeConverteEmAusenciaDeConfiguracao(){
        var jdbc=mock(JdbcTemplate.class);when(jdbc.queryForObject(anyString(),eq(String.class))).thenThrow(new org.springframework.dao.DataAccessResourceFailureException("falha controlada"));
        assertThatThrownBy(()->new ProntidaoEnvioDocumental(new MockEnvironment(),jdbc).consultar()).isInstanceOf(org.springframework.dao.DataAccessResourceFailureException.class);
    }
    @Test void assinaturaMudaComIdentidadeEAutorizacaoSemHashDeSegredo(){
        var env=new MockEnvironment().withProperty("rotabrasil.email.smtp.host","smtp.gmail.com");var p=new ProntidaoEnvioDocumental(env,mock(JdbcTemplate.class));String primeira=p.assinatura();
        env.withProperty("rotabrasil.email.smtp.senha","SEGREDO_SOMENTE_FIXTURE");assertThat(p.assinatura()).isEqualTo(primeira);
        env.withProperty("rotabrasil.email.smtp.credencial-versao","autorizacao-fixture-2");assertThat(p.assinatura()).isNotEqualTo(primeira);String segunda=p.assinatura();
        env.withProperty("rotabrasil.email.smtp.usuario","outra@example.invalid");assertThat(p.assinatura()).isNotEqualTo(segunda);
    }
}
