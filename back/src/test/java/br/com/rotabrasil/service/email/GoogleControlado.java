package br.com.rotabrasil.service.email;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/** Somente loopback. Nenhum endpoint externo pode ser chamado pela fixture. */
public final class GoogleControlado implements AutoCloseable {
    private final HttpServer server;
    private final ExecutorService executor=Executors.newCachedThreadPool();
    public final AtomicInteger trocas=new AtomicInteger(),renovacoes=new AtomicInteger(),envios=new AtomicInteger(),revogacoes=new AtomicInteger();
    public volatile String challenge,email="envio@example.invalid",id="google-id-sintetico",scope=ContaGmailDocumental.SEND+" "+ContaGmailDocumental.EMAIL;
    public volatile boolean refresh=true,invalidGrant=false,identidadeVerificada=true,tokenValido=true,rotacionar=false,respostaPerdida=false;
    public volatile int statusEnvio=200,statusRevogacao=200;
    public volatile String mime,ultimoRefresh;
    public volatile Runnable antesEnviar=()->{};
    private final ObjectMapper json=new ObjectMapper();
    public GoogleControlado(){try{
        server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);server.setExecutor(executor);
        server.createContext("/",e->{
            try{
                String path=e.getRequestURI().getPath(),corpo="{}";int status=200;
                String recebido=new String(e.getRequestBody().readAllBytes(),StandardCharsets.UTF_8);
                var form=parametros(recebido);
                if(path.equals("/token")){
                    boolean renovar="refresh_token".equals(form.get("grant_type"));
                    if(renovar){renovacoes.incrementAndGet();ultimoRefresh=form.get("refresh_token");}
                    else{trocas.incrementAndGet();String calculado=Base64.getUrlEncoder().withoutPadding().encodeToString(MessageDigest.getInstance("SHA-256").digest(form.getOrDefault("code_verifier","").getBytes(StandardCharsets.US_ASCII)));if(!Objects.equals(challenge,calculado))throw new IllegalArgumentException();}
                    if(invalidGrant){status=400;corpo="{\"error\":\"invalid_grant\"}";}
                    else{
                        var t=new HashMap<String,Object>();if(tokenValido)t.put("access_token","ACCESS_SINTETICO_PRIVADO");t.put("expires_in",3600);t.put("token_type","Bearer");t.put("scope",scope);
                        if((!renovar&&refresh)||renovar&&rotacionar)t.put("refresh_token",rotacionar?"REFRESH_ROTACIONADO_PRIVADO":"REFRESH_SINTETICO_PRIVADO");
                        corpo=json.writeValueAsString(t);
                    }
                }else if(path.equals("/identity"))corpo=json.writeValueAsString(Map.of("id",id,"email",email,"verified_email",identidadeVerificada));
                else if(path.equals("/revoke")){revogacoes.incrementAndGet();status=statusRevogacao;}
                else if(path.equals("/send")){
                    antesEnviar.run();
                    envios.incrementAndGet();mime=new String(Base64.getUrlDecoder().decode(json.readTree(recebido).path("raw").asText()),StandardCharsets.UTF_8);
                    if(respostaPerdida)return;
                    status=statusEnvio;corpo="{\"id\":\"mensagem-sintetica\"}";
                }
                byte[] bytes=corpo.getBytes(StandardCharsets.UTF_8);e.getResponseHeaders().set("Content-Type","application/json");
                e.sendResponseHeaders(status,bytes.length);e.getResponseBody().write(bytes);
            }catch(Exception ex){byte[] bytes="{\"error\":\"invalid_grant\"}".getBytes(StandardCharsets.UTF_8);try{e.sendResponseHeaders(400,bytes.length);e.getResponseBody().write(bytes);}catch(Exception ignored){}}
            finally{e.close();}
        });server.start();
    }catch(Exception ex){throw new IllegalStateException(ex);}}
    public GoogleDocumentalClient client(ObjectMapper json){String base="http://127.0.0.1:"+server.getAddress().getPort();return new GoogleDocumentalClient(json,base+"/token",base+"/identity",base+"/revoke",base+"/send");}
    public Map<String,String> autorizar(String url){var p=parametros(URI.create(url).getRawQuery());challenge=p.get("code_challenge");return p;}
    public static Map<String,String> parametros(String form){var m=new HashMap<String,String>();if(form!=null)for(String parte:form.split("&")){var v=parte.split("=",2);if(v.length==2)m.put(URLDecoder.decode(v[0],StandardCharsets.UTF_8),URLDecoder.decode(v[1],StandardCharsets.UTF_8));}return m;}
    @Override public void close(){server.stop(0);executor.shutdownNow();}
}
