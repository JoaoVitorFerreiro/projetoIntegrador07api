package br.com.rotabrasil.service.email;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.stream.Collectors;

/** Endpoints fixos em produção; sem redirects, retentativas ou logging de corpos. */
@Component
public class GoogleDocumentalClient {
    private final HttpClient http=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).followRedirects(HttpClient.Redirect.NEVER).build();
    private final ObjectMapper json;
    private final String baseToken, identidade, revogacao, envio;
    @org.springframework.beans.factory.annotation.Autowired
    public GoogleDocumentalClient(ObjectMapper json){this(json,"https://oauth2.googleapis.com/token","https://www.googleapis.com/oauth2/v2/userinfo","https://oauth2.googleapis.com/revoke","https://gmail.googleapis.com/gmail/v1/users/me/messages/send");}
    // Visível apenas ao pacote para testes com servidor controlado. Não há override por configuração externa.
    GoogleDocumentalClient(ObjectMapper json,String token,String identidade,String revogacao,String envio){this.json=json;this.baseToken=token;this.identidade=identidade;this.revogacao=revogacao;this.envio=envio;}
    static final class Falha extends RuntimeException {
        final boolean invalidGrant;
        Falha(boolean invalidGrant){super("Autorização Google indisponível");this.invalidGrant=invalidGrant;}
    }
    JsonNode tokens(Map<String,String> parametros){
        var r=chamar(baseToken,form(parametros),null,"application/x-www-form-urlencoded");
        if(r.statusCode()!=200)throw new Falha("invalid_grant".equals(corpo(r).path("error").asText()));
        return corpo(r);
    }
    JsonNode identidade(String access){
        var r=chamar(identidade,null,access,null);
        if(r.statusCode()!=200)throw new Falha(false);
        return corpo(r);
    }
    boolean revogar(String token){try{return chamar(revogacao,form(Map.of("token",token)),null,"application/x-www-form-urlencoded").statusCode()==200;}catch(RuntimeException ex){return false;}}
    TransporteEmail.Resultado enviar(String access,String raw){
        try {
            var r=chamar(envio,json.writeValueAsString(Map.of("raw",raw)),access,"application/json");
            if(r.statusCode()==200&&!corpo(r).path("id").asText().isBlank())return TransporteEmail.Resultado.ACEITO_API;
            if(r.statusCode()==401)throw new Falha(true);
            if(java.util.Set.of(400,403,404,413,429).contains(r.statusCode()))return TransporteEmail.Resultado.RECUSADO;
            return TransporteEmail.Resultado.DESCONHECIDO;
        }catch(Falha ex){if(ex.invalidGrant)throw ex;return TransporteEmail.Resultado.DESCONHECIDO;}
        catch(Exception ex){return TransporteEmail.Resultado.DESCONHECIDO;}
    }
    private HttpResponse<String> chamar(String uri,String body,String access,String tipo){
        try {
            var b=HttpRequest.newBuilder(URI.create(uri)).timeout(Duration.ofSeconds(20)).header("Accept","application/json");
            if(access!=null)b.header("Authorization","Bearer "+access);
            if(body==null)b.GET();else b.header("Content-Type",tipo).POST(HttpRequest.BodyPublishers.ofString(body));
            return http.send(b.build(),HttpResponse.BodyHandlers.ofString());
        }catch(InterruptedException ex){Thread.currentThread().interrupt();throw new Falha(false);}
        catch(Exception ex){throw new Falha(false);}
    }
    private JsonNode corpo(HttpResponse<String> r){try{if(r.body().length()>65536)throw new IllegalArgumentException();return json.readTree(r.body());}catch(Exception ex){throw new Falha(false);}}
    static String form(Map<String,String> valores){return valores.entrySet().stream().map(e->enc(e.getKey())+"="+enc(e.getValue())).collect(Collectors.joining("&"));}
    private static String enc(String v){return URLEncoder.encode(v,StandardCharsets.UTF_8);}
}
