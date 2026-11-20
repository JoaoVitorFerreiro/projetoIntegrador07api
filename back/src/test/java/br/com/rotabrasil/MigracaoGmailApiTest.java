package br.com.rotabrasil;

import br.com.rotabrasil.manutencao.InventarioBanco;
import br.com.rotabrasil.migracao.V3__ConferirEPreencherIdentidade;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import java.nio.file.*;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;

class MigracaoGmailApiTest {
    @TempDir Path tmp;
    @Test void v15ParaV16PreservaAceiteSmtpSnapshotEDemaisRestricoes()throws Exception{
        Path acervo=Files.createDirectory(tmp.resolve("acervo"));var fonte=new DriverManagerDataSource("jdbc:h2:mem:gmail-migracao-"+UUID.randomUUID()+";DB_CLOSE_DELAY=-1","sa","");
        var v3=new V3__ConferirEPreencherIdentidade(new InventarioBanco(acervo),acervo,null);
        Flyway.configure().dataSource(fonte).javaMigrations(v3).target("15").cleanDisabled(true).load().migrate();var jdbc=new JdbcTemplate(fonte);
        jdbc.update("INSERT INTO clientes(id,nome,cnpj) VALUES(1,'Cliente sintético','11222333000181')");jdbc.update("INSERT INTO viagens(id,numero,cliente_id) VALUES(1,'SINTETICA',1)");
        String conf=UUID.randomUUID().toString(),tentativa=UUID.randomUUID().toString();
        jdbc.update("INSERT INTO conferencias_email(id,viagem_id,selecao,snapshot,criada_em,expira_em) VALUES(?,1,'{}','{\"versao\":1}',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)",conf);
        jdbc.update("INSERT INTO tentativas_email(id,conferencia_id,viagem_id,snapshot,modo,situacao,motivo,criada_em) VALUES(?,?,1,'{\"versao\":1,\"historico\":true}','REAL','ACEITO_SMTP','Aceite SMTP histórico',CURRENT_TIMESTAMP)",tentativa,conf);
        var antes=jdbc.queryForMap("SELECT * FROM tentativas_email WHERE id=?",tentativa);
        assertThatThrownBy(()->jdbc.update("UPDATE tentativas_email SET situacao='ACEITO_API' WHERE id=?",tentativa)).isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
        Flyway.configure().dataSource(fonte).javaMigrations(v3).cleanDisabled(true).load().migrate();
        assertThat(jdbc.queryForMap("SELECT * FROM tentativas_email WHERE id=?",tentativa)).isEqualTo(antes);
        jdbc.update("UPDATE tentativas_email SET situacao='ACEITO_API' WHERE id=?",tentativa);
        assertThatThrownBy(()->jdbc.update("UPDATE tentativas_email SET modo='INVALIDO' WHERE id=?",tentativa)).isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
        assertThatThrownBy(()->jdbc.update("UPDATE tentativas_email SET situacao='ENTREGUE' WHERE id=?",tentativa)).isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM \"flyway_schema_history\" WHERE \"version\"='16' AND \"success\"=TRUE",Integer.class)).isEqualTo(1);
    }
}
