package db.migration;

import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import java.util.ArrayList;

/** V7 deixou o nome do CHECK a cargo do H2. Localiza somente o CHECK de situação. */
public class V16__aceite_gmail_api extends BaseJavaMigration {
    @Override public Integer getChecksum(){return 16001;}
    @Override public void migrate(Context context)throws Exception {
        var nomes=new ArrayList<String>();
        try(var p=context.getConnection().prepareStatement("SELECT c.CONSTRAINT_NAME FROM INFORMATION_SCHEMA.CHECK_CONSTRAINTS c JOIN INFORMATION_SCHEMA.TABLE_CONSTRAINTS t ON c.CONSTRAINT_SCHEMA=t.CONSTRAINT_SCHEMA AND c.CONSTRAINT_NAME=t.CONSTRAINT_NAME WHERE t.TABLE_SCHEMA='PUBLIC' AND t.TABLE_NAME='TENTATIVAS_EMAIL' AND c.CHECK_CLAUSE LIKE '%SITUACAO%'");var r=p.executeQuery()){
            while(r.next())nomes.add(r.getString(1));
        }
        if(nomes.size()!=1)throw new IllegalStateException("CHECK de situação documental divergente; migração interrompida");
        try(var s=context.getConnection().createStatement()){
            s.execute("ALTER TABLE tentativas_email DROP CONSTRAINT \""+nomes.getFirst().replace("\"","\"\"")+"\"");
            s.execute("ALTER TABLE tentativas_email ADD CONSTRAINT ck_tentativas_email_situacao CHECK (situacao IN ('SIMULADO','FALHA','DESCONHECIDO','ACEITO_SMTP','ACEITO_API'))");
        }
    }
}
