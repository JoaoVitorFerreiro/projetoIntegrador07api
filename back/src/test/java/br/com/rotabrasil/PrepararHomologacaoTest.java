package br.com.rotabrasil;

import br.com.rotabrasil.manutencao.PrepararHomologacao;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.sql.DriverManager;
import static org.assertj.core.api.Assertions.*;

class PrepararHomologacaoTest {
    @TempDir Path tmp;
    @Test void criaSomenteBaseNovaOfflineV15ComDiretoriosSeparados()throws Exception{
        Path root=tmp.resolve("nova");PrepararHomologacao.preparar(root);
        for(String p:new String[]{"banco","acervo","exportacao","segredos","config","sinteticos"})assertThat(root.resolve(p)).isDirectory();
        assertThat(root.resolve("sinteticos/cte-sintetico.xml")).hasContent(new String(getClass().getResourceAsStream("/email/cte-sintetico.xml").readAllBytes(),java.nio.charset.StandardCharsets.UTF_8));
        try(var c=DriverManager.getConnection("jdbc:h2:file:"+root.resolve("banco/rotabrasil")+";IFEXISTS=TRUE","sa","");var s=c.createStatement()){
            try(var r=s.executeQuery("SELECT COUNT(*) FROM \"flyway_schema_history\" WHERE \"version\"='15' AND \"success\"=TRUE")){r.next();assertThat(r.getInt(1)).isEqualTo(1);}
            try(var r=s.executeQuery("SELECT COUNT(*) FROM usuarios")){r.next();assertThat(r.getInt(1)).isZero();}
        }
    }
    @Test void recusaQualquerDiretorioExistenteSemAbrirOuAlterarArquivos()throws Exception{
        Path root=Files.createDirectory(tmp.resolve("existente"));Path sentinela=root.resolve("nao-tocar.txt");Files.writeString(sentinela,"somente fixture");
        assertThatThrownBy(()->PrepararHomologacao.preparar(root)).isInstanceOf(FileAlreadyExistsException.class);assertThat(sentinela).hasContent("somente fixture");
    }
}
