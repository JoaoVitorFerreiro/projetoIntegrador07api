package br.com.rotabrasil.manutencao;
import java.util.Arrays;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
/** CLI sem banco/rede; lê senha pelo console sem eco. Nunca aceita senha como argumento. */
public final class GerarHashSenha {
    public static void main(String[] args){
        if(args.length==2&&args[0].equals("--gerar-homologacao")){
            try{gerarHomologacao(java.nio.file.Path.of(args[1]));System.out.println("Acesso ADMIN aleatório criado somente nos arquivos privados de homologação. Nenhuma senha ou hash é exibido.");return;}
            catch(Exception ex){throw new IllegalStateException("Geração recusada: use homologação sintética V16 nova, com diretório privado e arquivos de acesso ainda ausentes");}
        }
        var console=System.console();boolean arquivo=args.length==2&&args[0].equals("--arquivo");if((args.length!=0&&!arquivo)||console==null)throw new IllegalStateException("Execute em terminal interativo, sem argumentos ou com --arquivo CAMINHO_NOVO");
        char[] senha=console.readPassword("Nova senha (12 a 72 bytes UTF-8): ");
        try{String texto=new String(senha);int bytes=texto.getBytes(java.nio.charset.StandardCharsets.UTF_8).length;
            if(texto.length()<12||bytes>72)throw new IllegalArgumentException("Tamanho inválido");
            String hash=new BCryptPasswordEncoder(12).encode(texto);
            if(arquivo){try{java.nio.file.Files.writeString(java.nio.file.Path.of(args[1]),hash,java.nio.charset.StandardCharsets.UTF_8,java.nio.file.StandardOpenOption.CREATE_NEW);console.printf("Hash gravado no arquivo externo solicitado; não compartilhe seu conteúdo.%n");}catch(java.io.IOException ex){throw new IllegalStateException("Não foi possível criar arquivo novo para o hash");}}
            else console.printf("Hash BCrypt para configuração protegida:%n%s%n",hash);
        }finally{Arrays.fill(senha,'\0');}
    }
    /** Uso explícito do preparador protegido, após restringir a ACL; não redefine usuário existente. */
    public static void gerarHomologacao(java.nio.file.Path raiz)throws Exception{
        raiz=raiz.toAbsolutePath().normalize();
        for(var p=raiz;p!=null;p=p.getParent())if(java.nio.file.Files.isSymbolicLink(p))throw new IllegalArgumentException();
        if(!java.nio.file.Files.readString(raiz.resolve(".rotabrasil-homologacao")).trim().equals("NOVA_BASE_SINTETICA_V16"))throw new IllegalArgumentException();
        var pasta=raiz.resolve("segredos");var acesso=pasta.resolve("acesso-admin-local.txt");var destino=pasta.resolve("rotabrasil.seguranca.admin-hash");
        if(!java.nio.file.Files.isDirectory(pasta)||java.nio.file.Files.exists(acesso)||java.nio.file.Files.exists(destino))throw new java.nio.file.FileAlreadyExistsException("Acesso já provisionado");
        byte[] bytes=new byte[24];new java.security.SecureRandom().nextBytes(bytes);
        try{
            String senha=java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
            String hash=new BCryptPasswordEncoder(12).encode(senha);
            java.nio.file.Files.writeString(acesso,senha,java.nio.file.StandardOpenOption.CREATE_NEW);
            try{java.nio.file.Files.writeString(destino,hash,java.nio.file.StandardOpenOption.CREATE_NEW);}
            catch(Exception ex){java.nio.file.Files.deleteIfExists(acesso);throw ex;}
        }finally{Arrays.fill(bytes,(byte)0);}
    }
}
