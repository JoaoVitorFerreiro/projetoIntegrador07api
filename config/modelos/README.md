# Modelos públicos, sem segredos

Os modelos `gmail-documental*.properties.example` configuram exclusivamente o envio
documental da Central. Copie-os somente para a homologação externa preparada pelo
script protegido, nunca preencha credenciais no checkout.

Procedimento e comandos completos: [homologação Gmail documental](../../docs/gmail-documental-homologacao.md).

O backend carrega a configuração por arquivo explícito e `configtree` externo.
`rotabrasil.email.smtp.senha` não deve aparecer com valor vazio no modelo copiado:
isso pode sobrescrever a propriedade importada do arquivo secreto. BASIC usa somente
senha de aplicativo Google autorizada; XOAUTH2 exige token/consentimento/renovação externos.
O identificador `rotabrasil.email.smtp.credencial-versao` é não secreto e muda quando
a autorização/credencial é substituída, invalidando conferências anteriores.
