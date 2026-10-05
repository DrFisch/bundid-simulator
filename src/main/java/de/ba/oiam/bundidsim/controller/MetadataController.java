/*
 * Neu 2026 im Fork bpsim/standardkonform (siehe FORK.md). Lizenz wie das Gesamtprojekt: Apache License 2.0.
 */
package de.ba.oiam.bundidsim.controller;

import de.ba.oiam.bundidsim.services.SamlSigningService;
import de.ba.oiam.bundidsim.utils.XmlParserTools;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.security.cert.CertificateEncodingException;
import java.util.Base64;

/**
 * GET /saml/metadata liefert die SAML-Metadaten des simulierten IdP (EntityID, Signaturzertifikat,
 * SSO-Endpunkt mit HTTP-POST-Binding). Nur verfügbar, wenn app.saml.idp-entity-id gesetzt ist.
 */
@RestController
public class MetadataController {

    @Autowired
    private SamlSigningService samlSigningService;

    @Value("${app.saml.idp-entity-id:}")
    private String idpEntityId;

    @Value("${app.saml.sso-url:}")
    private String ssoUrl;

    @GetMapping(path = "/saml/metadata", produces = "application/samlmetadata+xml")
    public ResponseEntity<String> metadata() throws CertificateEncodingException {
        if (!StringUtils.hasText(idpEntityId) || !StringUtils.hasText(ssoUrl)) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
        String keyDescriptor = "";
        if (samlSigningService.isEnabled()) {
            String certificate = Base64.getEncoder().encodeToString(samlSigningService.getCertificate().getEncoded());
            keyDescriptor = """
                        <md:KeyDescriptor use="signing">
                          <ds:KeyInfo><ds:X509Data><ds:X509Certificate>%s</ds:X509Certificate></ds:X509Data></ds:KeyInfo>
                        </md:KeyDescriptor>
                    """.formatted(certificate);
        }
        String metadata = """
                <?xml version="1.0" encoding="UTF-8"?>
                <md:EntityDescriptor xmlns:md="urn:oasis:names:tc:SAML:2.0:metadata"
                                     xmlns:ds="http://www.w3.org/2000/09/xmldsig#" entityID="%s">
                  <md:IDPSSODescriptor WantAuthnRequestsSigned="false"
                                       protocolSupportEnumeration="urn:oasis:names:tc:SAML:2.0:protocol">
                %s    <md:NameIDFormat>urn:oasis:names:tc:SAML:2.0:nameid-format:transient</md:NameIDFormat>
                    <md:SingleSignOnService Binding="urn:oasis:names:tc:SAML:2.0:bindings:HTTP-POST" Location="%s"/>
                  </md:IDPSSODescriptor>
                </md:EntityDescriptor>
                """.formatted(XmlParserTools.escape(idpEntityId), keyDescriptor, XmlParserTools.escape(ssoUrl));
        return ResponseEntity.ok(metadata);
    }
}
