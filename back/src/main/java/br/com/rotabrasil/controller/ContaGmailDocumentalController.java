package br.com.rotabrasil.controller;

import br.com.rotabrasil.service.email.ContaGmailDocumental;
import jakarta.servlet.http.*;
import org.springframework.http.*;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.net.URI;
import java.util.Map;

@RestController
@RequestMapping("/api/configuracoes/gmail")
public class ContaGmailDocumentalController {
    private final ContaGmailDocumental gmail;
    private final org.springframework.jdbc.core.JdbcTemplate jdbc;
    public ContaGmailDocumentalController(ContaGmailDocumental gmail,org.springframework.jdbc.core.JdbcTemplate jdbc){this.gmail=gmail;this.jdbc=jdbc;}
    private void admin(Authentication a){if(a==null||!a.isAuthenticated()||a.getAuthorities().stream().noneMatch(g->g.getAuthority().equals("ROLE_ADMIN")))throw new org.springframework.security.access.AccessDeniedException("ADMIN obrigatório");}
    @GetMapping public ContaGmailDocumental.Estado consultar(Authentication a){admin(a);var e=gmail.consultar();var ultimas=jdbc.queryForList("SELECT situacao FROM tentativas_email ORDER BY criada_em DESC LIMIT 1",String.class);return new ContaGmailDocumental.Estado(e.selecionado(),e.configurado(),e.conta(),e.estado(),e.permissaoEnvio(),e.autorizacaoVersao(),e.modo(),e.transporteAtivado(),ultimas.isEmpty()?"SEM_TENTATIVA":ultimas.getFirst(),e.recebimento(),e.aviso(),e.pendencias());}
    @PostMapping("/conectar") public Map<String,String> conectar(HttpServletRequest req,Authentication a){admin(a);return Map.of("url",gmail.iniciar(req.getSession(),a.getName()));}
    @PostMapping("/desconectar") public Map<String,Object> desconectar(Authentication a){admin(a);boolean revogada=gmail.desconectar();return Map.of("revogacaoConfirmada",revogada,"mensagem",revogada?"Uso local invalidado e revogação concluída. Confira novamente antes de enviar.":"Uso local invalidado. Revogação remota não confirmada; remova o acesso nas permissões da Conta Google.");}
    @GetMapping("/callback") public ResponseEntity<Void> callback(HttpServletRequest req,Authentication a,
            @RequestParam(required=false) String state,@RequestParam(required=false) String code,@RequestParam(required=false) String error){
        admin(a);String resultado=gmail.concluir(req.getSession(false),a.getName(),state,code,error);
        return ResponseEntity.status(303).header("Cache-Control","no-store").header("Referrer-Policy","no-referrer")
                .location(URI.create(gmail.retorno()+"?gmail="+resultado+"#configuracoes")).build();
    }
}
