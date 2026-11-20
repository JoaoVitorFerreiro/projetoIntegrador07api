package br.com.rotabrasil.service.email;

import com.fasterxml.jackson.databind.ObjectMapper;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.file.*;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Arrays;

/** JCA AES-256-GCM. Arquivo externo; chave separada. Um único processo possui o cofre. */
final class CofreGmail implements AutoCloseable {
    static final class Conta {
        public String id, email, versao, access, refresh, cliente, estado;
        public Instant expira;
        public Conta() {}
        @Override public String toString() { return "ContaGmail[segredos omitidos]"; }
    }
    private final Path arquivo;
    private final byte[] chave;
    private final ObjectMapper json;
    private final FileChannel canal;
    private final FileLock trava;
    CofreGmail(Path arquivo, Path chave, ObjectMapper json) {
        this.arquivo=arquivo;this.json=json;
        FileChannel aberto=null;
        try {
            if (!arquivo.isAbsolute()||!chave.isAbsolute()||Files.isSymbolicLink(arquivo)||Files.isSymbolicLink(chave)
                    || !Files.isDirectory(arquivo.getParent())) throw new IllegalArgumentException();
            this.chave=Files.readAllBytes(chave);
            if(this.chave.length!=32)throw new IllegalArgumentException();
            aberto=FileChannel.open(arquivo.resolveSibling(arquivo.getFileName()+".lock"),StandardOpenOption.CREATE,StandardOpenOption.WRITE);
            trava=aberto.tryLock();
            if(trava==null)throw new IllegalStateException();
            canal=aberto;
        } catch(Exception ex) {
            if(aberto!=null)try{aberto.close();}catch(Exception ignored){}
            throw new IllegalStateException("Cofre Gmail indisponível; confira chave, permissões e exclusividade do processo");
        }
    }
    Conta ler() {
        try {
            if(!Files.exists(arquivo))return null;
            if(Files.size(arquivo)>65536)throw new IllegalArgumentException();
            byte[] bytes=Files.readAllBytes(arquivo);
            var buffer=ByteBuffer.wrap(bytes);
            if(buffer.get()!=1)throw new IllegalArgumentException();
            byte[] iv=new byte[12];buffer.get(iv);
            var c=cifra(Cipher.DECRYPT_MODE,iv);
            byte[] claro=c.doFinal(bytes,13,bytes.length-13);
            try{return json.readValue(claro,Conta.class);}finally{Arrays.fill(claro,(byte)0);}
        } catch(Exception ex){throw new IllegalStateException("Cofre Gmail não pôde ser autenticado; restaure chave e cofre correspondentes");}
    }
    void salvar(Conta conta) {
        Path temp=null;
        try {
            byte[] iv=new byte[12];new SecureRandom().nextBytes(iv);
            byte[] claro=json.writeValueAsBytes(conta), cifrado;
            try{cifrado=cifra(Cipher.ENCRYPT_MODE,iv).doFinal(claro);}finally{Arrays.fill(claro,(byte)0);}
            temp=Files.createTempFile(arquivo.getParent(),".gmail-",".tmp");
            byte[] bytes=ByteBuffer.allocate(13+cifrado.length).put((byte)1).put(iv).put(cifrado).array();
            try(var out=FileChannel.open(temp,StandardOpenOption.WRITE)){var buffer=ByteBuffer.wrap(bytes);while(buffer.hasRemaining())out.write(buffer);out.force(true);}
            Files.move(temp,arquivo,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);
        } catch(Exception ex){throw new IllegalStateException("Não foi possível persistir o cofre Gmail de forma atômica");}
        finally{if(temp!=null)try{Files.deleteIfExists(temp);}catch(Exception ignored){}}
    }
    private Cipher cifra(int modo,byte[] iv)throws Exception {
        var c=Cipher.getInstance("AES/GCM/NoPadding");
        c.init(modo,new SecretKeySpec(chave,"AES"),new GCMParameterSpec(128,iv));
        c.updateAAD("RotaBrasil/Gmail/documental/v1".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        return c;
    }
    public void close()throws Exception{Arrays.fill(chave,(byte)0);trava.release();canal.close();}
}
