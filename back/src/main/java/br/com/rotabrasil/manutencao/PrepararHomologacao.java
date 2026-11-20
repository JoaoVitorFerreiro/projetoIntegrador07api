package br.com.rotabrasil.manutencao;

import br.com.rotabrasil.migracao.V3__ConferirEPreencherIdentidade;
import org.flywaydb.core.Flyway;
import java.nio.file.*;

/** Provisiona SOMENTE diretório que ainda não existe. Offline: sem Spring, servidor ou SMTP. */
public final class PrepararHomologacao {
    public static void main(String[] args) {
        try {
            if(args.length!=2||!args[0].equals("--diretorio"))throw new IllegalArgumentException();
            preparar(Path.of(args[1]));
            System.out.println("Homologação nova preparada: esquema V16 e fixture sintética. Nenhum servidor ou transporte iniciado.");
        } catch(Exception ex) {
            System.err.println("Preparação recusada. Informe diretório absoluto NOVO, com pai existente, sem ponto e vírgula ou redirecionamentos. Nenhuma base existente será migrada.");
            System.exit(1);
        }
    }
    public static void preparar(Path destino)throws Exception {
        if(!destino.isAbsolute()||destino.toString().contains(";"))throw new IllegalArgumentException();
        destino=destino.normalize();
        for(Path p=destino.getParent();p!=null;p=p.getParent())if(Files.isSymbolicLink(p))throw new IllegalArgumentException();
        Files.createDirectory(destino); // CREATE_NEW: nunca abre o banco de um diretório preexistente.
        for(String pasta:new String[]{"banco","acervo","exportacao","config","segredos","sinteticos"})Files.createDirectory(destino.resolve(pasta));
        var raiz=destino.resolve("acervo");
        var flyway=Flyway.configure().dataSource("jdbc:h2:file:"+destino.resolve("banco/rotabrasil").toString().replace('\\','/')+";WRITE_DELAY=0","sa","")
                .cleanDisabled(true).baselineOnMigrate(false).javaMigrations(new V3__ConferirEPreencherIdentidade(new InventarioBanco(raiz),raiz,null)).load();
        flyway.migrate();flyway.validate();
        if(!"16".equals(flyway.info().current().getVersion().getVersion()))throw new IllegalStateException();
        try(var in=PrepararHomologacao.class.getResourceAsStream("/email/cte-sintetico.xml")) {
            if(in==null)throw new IllegalStateException();Files.copy(in,destino.resolve("sinteticos/cte-sintetico.xml"));
        }
        Files.writeString(destino.resolve(".rotabrasil-homologacao"),"NOVA_BASE_SINTETICA_V16\n",StandardOpenOption.CREATE_NEW);
    }
}
