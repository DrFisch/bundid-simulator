/*
 * Neu 2026 im Fork bpsim/standardkonform (siehe FORK.md). Lizenz wie das Gesamtprojekt: Apache License 2.0.
 */
package de.ba.oiam.bundidsim.utils;

import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ForkStandardkonformToolsTest {

    @Test
    void findsIssuerAndLevelIndependentOfPrefix() throws Exception {
        String xml = """
                <saml2p:AuthnRequest xmlns:saml2p="urn:oasis:names:tc:SAML:2.0:protocol"
                    xmlns:saml2="urn:oasis:names:tc:SAML:2.0:assertion" ID="_r1">
                  <saml2:Issuer>https://sp.example.test</saml2:Issuer>
                  <saml2p:RequestedAuthnContext Comparison="minimum">
                    <saml2:AuthnContextClassRef>
                      STORK-QAA-Level-3
                    </saml2:AuthnContextClassRef>
                  </saml2p:RequestedAuthnContext>
                </saml2p:AuthnRequest>""";
        Document doc = XmlParserTools.parseXmlNamespaceAware(xml);
        Element root = doc.getDocumentElement();
        Element rac = XmlParserTools.findChild(root, XmlParserTools.NS_SAML_PROTOCOL, "RequestedAuthnContext");

        assertThat(XmlParserTools.findChildText(root, XmlParserTools.NS_SAML_ASSERTION, "Issuer"))
                .isEqualTo("https://sp.example.test");
        assertThat(XmlParserTools.findChildText(rac, XmlParserTools.NS_SAML_ASSERTION, "AuthnContextClassRef"))
                .isEqualTo("STORK-QAA-Level-3");
    }

    @Test
    void rejectsDoctype() {
        String xml = "<!DOCTYPE x [<!ENTITY e SYSTEM \"file:///etc/passwd\">]><x>&e;</x>";
        assertThatThrownBy(() -> XmlParserTools.parseXmlNamespaceAware(xml)).isInstanceOf(Exception.class);
    }

    @Test
    void escapesXmlSpecialCharacters() {
        assertThat(XmlParserTools.escape("a&b<c>\"d'")).isEqualTo("a&amp;b&lt;c&gt;&quot;d&apos;");
        assertThat(XmlParserTools.escape(null)).isNull();
    }

    @Test
    void mapsLevelsBetweenStorkAndEidas() {
        assertThat(AuthLevelTools.normalizeRequestedLevel(" http://eidas.europa.eu/LoA/substantial "))
                .isEqualTo(AuthLevelTools.STORK_3);
        assertThat(AuthLevelTools.normalizeRequestedLevel("STORK-QAA-Level-4")).isEqualTo(AuthLevelTools.STORK_4);
        assertThat(AuthLevelTools.toAuthnContextClassRef(AuthLevelTools.STORK_4, "eidas"))
                .isEqualTo(AuthLevelTools.EIDAS_LOA_HIGH);
        assertThat(AuthLevelTools.toAuthnContextClassRef(AuthLevelTools.STORK_1, "eidas"))
                .isEqualTo(AuthLevelTools.EIDAS_LOA_LOW);
        assertThat(AuthLevelTools.toAuthnContextClassRef(AuthLevelTools.STORK_3, "stork"))
                .isEqualTo(AuthLevelTools.STORK_3);
    }
}
