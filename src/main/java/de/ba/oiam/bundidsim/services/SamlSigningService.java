/*
 * Neu 2026 im Fork bpsim/standardkonform (siehe FORK.md). Lizenz wie das Gesamtprojekt: Apache License 2.0.
 */
package de.ba.oiam.bundidsim.services;

import de.ba.oiam.bundidsim.utils.XmlParserTools;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import javax.xml.crypto.dsig.CanonicalizationMethod;
import javax.xml.crypto.dsig.DigestMethod;
import javax.xml.crypto.dsig.Reference;
import javax.xml.crypto.dsig.SignatureMethod;
import javax.xml.crypto.dsig.SignedInfo;
import javax.xml.crypto.dsig.Transform;
import javax.xml.crypto.dsig.XMLSignatureFactory;
import javax.xml.crypto.dsig.dom.DOMSignContext;
import javax.xml.crypto.dsig.keyinfo.KeyInfo;
import javax.xml.crypto.dsig.keyinfo.KeyInfoFactory;
import javax.xml.crypto.dsig.spec.C14NMethodParameterSpec;
import javax.xml.crypto.dsig.spec.TransformParameterSpec;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.InputStream;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.cert.X509Certificate;
import java.util.List;

/**
 * Signiert die Assertion einer SAML-Response (enveloped XML-Signatur, RSA-SHA256, Exclusive C14N).
 * Schlüssel und Zertifikat kommen aus einem PKCS12-Keystore (app.saml.signing.*). Ist die Signatur
 * abgeschaltet (Standard), bleibt die Response unverändert unsigniert wie im Original.
 */
@Service
@Slf4j
public class SamlSigningService {

    @Value("${app.saml.signing.enabled:false}")
    private boolean enabled;

    @Value("${app.saml.signing.keystore:}")
    private String keystorePath;

    @Value("${app.saml.signing.password:}")
    private String keystorePassword;

    @Value("${app.saml.signing.alias:idp-signing}")
    private String keyAlias;

    private PrivateKey privateKey;
    private X509Certificate certificate;

    @PostConstruct
    void loadKey() throws Exception {
        if (!enabled) {
            log.info("SAML-Signatur ist abgeschaltet (app.saml.signing.enabled=false).");
            return;
        }
        KeyStore keyStore = KeyStore.getInstance("PKCS12");
        try (InputStream in = Files.newInputStream(Path.of(keystorePath))) {
            keyStore.load(in, keystorePassword.toCharArray());
        }
        privateKey = (PrivateKey) keyStore.getKey(keyAlias, keystorePassword.toCharArray());
        certificate = (X509Certificate) keyStore.getCertificate(keyAlias);
        if (privateKey == null || certificate == null) {
            throw new IllegalStateException("Schlüssel/Zertifikat '" + keyAlias + "' nicht im Keystore " + keystorePath);
        }
        log.info("SAML-Signatur aktiv, Zertifikat: {}", certificate.getSubjectX500Principal());
    }

    public boolean isEnabled() {
        return enabled;
    }

    public X509Certificate getCertificate() {
        return certificate;
    }

    /**
     * signiert die (erste) Assertion im Response-XML. Die Signatur wird direkt nach saml:Issuer eingefügt
     * (Schema-Reihenfolge: Issuer, Signature, Subject, ...).
     */
    public String signAssertion(String responseXml) {
        if (!enabled) {
            return responseXml;
        }
        try {
            Document doc = XmlParserTools.parseXmlNamespaceAware(responseXml);
            Element assertion = (Element) doc.getElementsByTagNameNS(XmlParserTools.NS_SAML_ASSERTION, "Assertion").item(0);
            if (assertion == null) {
                return responseXml; // Fehler-Response ohne Assertion
            }
            assertion.setIdAttribute("ID", true);
            Element issuer = XmlParserTools.findChild(assertion, XmlParserTools.NS_SAML_ASSERTION, "Issuer");

            XMLSignatureFactory factory = XMLSignatureFactory.getInstance("DOM");
            Reference reference = factory.newReference(
                    "#" + assertion.getAttribute("ID"),
                    factory.newDigestMethod(DigestMethod.SHA256, null),
                    List.of(factory.newTransform(Transform.ENVELOPED, (TransformParameterSpec) null),
                            factory.newTransform(CanonicalizationMethod.EXCLUSIVE, (TransformParameterSpec) null)),
                    null, null);
            SignedInfo signedInfo = factory.newSignedInfo(
                    factory.newCanonicalizationMethod(CanonicalizationMethod.EXCLUSIVE, (C14NMethodParameterSpec) null),
                    factory.newSignatureMethod(SignatureMethod.RSA_SHA256, null),
                    List.of(reference));
            KeyInfoFactory keyInfoFactory = factory.getKeyInfoFactory();
            KeyInfo keyInfo = keyInfoFactory.newKeyInfo(List.of(keyInfoFactory.newX509Data(List.of(certificate))));

            DOMSignContext signContext = new DOMSignContext(privateKey, assertion, issuer.getNextSibling());
            signContext.setDefaultNamespacePrefix("ds");
            factory.newXMLSignature(signedInfo, keyInfo).sign(signContext);

            StringWriter writer = new StringWriter();
            var transformer = TransformerFactory.newInstance().newTransformer();
            transformer.setOutputProperty(OutputKeys.ENCODING, "UTF-8");
            transformer.transform(new DOMSource(doc), new StreamResult(writer));
            return writer.toString();
        } catch (Exception e) {
            throw new IllegalStateException("Assertion konnte nicht signiert werden", e);
        }
    }
}
