package br.com.rotabrasil;
/** Utilitário SOMENTE de teste; não é empacotado no JAR. Nenhuma credencial real como entrada. */
public final class HashFixtureHomologacao {
    public static void main(String[] args){System.out.print(new org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder(4).encode("SenhaSomenteFixture12!"));}
}
