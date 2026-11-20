package br.com.rotabrasil.service.email;

import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;
import java.io.ByteArrayOutputStream;
import java.util.*;

@Component
@ConditionalOnExpression("'${rotabrasil.email.modo:SIMULADO}' == 'REAL' and '${rotabrasil.email.transporte:SMTP}' == 'GMAIL_API'")
public class TransporteGmailApi implements TransporteEmail {
    private final ContaGmailDocumental conta;private final GoogleDocumentalClient google;
    public TransporteGmailApi(ContaGmailDocumental conta,GoogleDocumentalClient google){this.conta=conta;this.google=google;}
    @Override public Resultado enviar(MensagemPreparada mensagem){return conta.exclusivo(()->{
        String token,raw;
        try {
            token=conta.accessToken();
            var mime=new MimeMessage(Session.getInstance(new Properties()));
            var helper=new MimeMessageHelper(mime,true,"UTF-8");
            helper.setFrom(conta.remetente());helper.setTo(mensagem.destinatario());helper.setSubject(mensagem.assunto());helper.setText(mensagem.texto(),false);
            for(var a:mensagem.arquivos())helper.addAttachment(a.nome(),new ByteArrayResource(a.conteudo()),a.nome().toLowerCase(Locale.ROOT).endsWith(".pdf")?"application/pdf":a.nome().toLowerCase(Locale.ROOT).endsWith(".xml")?"application/xml":"application/octet-stream");
            mime.saveChanges();var out=new ByteArrayOutputStream();mime.writeTo(out);raw=Base64.getUrlEncoder().withoutPadding().encodeToString(out.toByteArray());
        }catch(Exception ex){return Resultado.RECUSADO;}
        try{return google.enviar(token,raw);}
        catch(GoogleDocumentalClient.Falha ex){if(ex.invalidGrant){conta.exigirReconexao();return Resultado.RECUSADO;}return Resultado.DESCONHECIDO;}
    });}
}
