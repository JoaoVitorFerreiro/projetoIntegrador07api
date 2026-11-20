package br.com.rotabrasil;

import br.com.rotabrasil.manutencao.GerarHashSenha;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import static org.assertj.core.api.Assertions.*;

class GerarAcessoHomologacaoTest {
    @TempDir Path tmp;
    @Test void geraAcessoAleatorioBCryptSemSobrescreverCredencialExistente()throws Exception{
        Files.createDirectory(tmp.resolve("segredos"));Files.writeString(tmp.resolve(".rotabrasil-homologacao"),"NOVA_BASE_SINTETICA_V16\n");
        GerarHashSenha.gerarHomologacao(tmp);
        String senha=Files.readString(tmp.resolve("segredos/acesso-admin-local.txt")),hash=Files.readString(tmp.resolve("segredos/rotabrasil.seguranca.admin-hash"));
        assertThat(senha.length()).isEqualTo(32);
        assertThat(new org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder().matches(senha,hash)).isTrue();
        assertThatThrownBy(()->GerarHashSenha.gerarHomologacao(tmp)).isInstanceOf(FileAlreadyExistsException.class);
        assertThat(Files.readString(tmp.resolve("segredos/rotabrasil.seguranca.admin-hash")).equals(hash)).isTrue();
    }
    @Test void recusaDiretorioSemMarcadorSintetico()throws Exception{
        Files.createDirectory(tmp.resolve("segredos"));assertThatThrownBy(()->GerarHashSenha.gerarHomologacao(tmp)).isInstanceOf(NoSuchFileException.class);
        try(var arquivos=Files.list(tmp.resolve("segredos"))){assertThat(arquivos.count()).isZero();}
    }
}
