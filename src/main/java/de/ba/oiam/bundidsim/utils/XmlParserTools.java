/*
 * Copyright 2025. IT-Systemhaus der Bundesagentur fuer Arbeit
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package de.ba.oiam.bundidsim.utils;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * statische Hilfsroutinen zur Verarbeitung von XML-Dokumenten (SAML-Request)
 *
 * Geändert 2026 (Fork bpsim/standardkonform, siehe FORK.md): namespace-bewusstes Parsen ohne DTD
 * (parseXmlNamespaceAware, findChildText) und XML-Escaping (escape) ergänzt.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
@Slf4j
public class XmlParserTools {

    public static final String NS_SAML_ASSERTION = "urn:oasis:names:tc:SAML:2.0:assertion";
    public static final String NS_SAML_PROTOCOL = "urn:oasis:names:tc:SAML:2.0:protocol";

    /**
     * Namespace-bewusstes Parsen ohne DTD-Verarbeitung (Schutz vor XXE). Elemente werden über Namespace und
     * lokalen Namen gefunden, unabhängig vom verwendeten Präfix (saml:, saml2:, samlp:, saml2p: ...).
     */
    public static Document parseXmlNamespaceAware(String xml)
            throws ParserConfigurationException, SAXException, IOException {
        DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
        dbf.setNamespaceAware(true);
        dbf.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        dbf.setXIncludeAware(false);
        dbf.setExpandEntityReferences(false);
        DocumentBuilder db = dbf.newDocumentBuilder();
        return db.parse(new InputSource(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8))));
    }

    /**
     * liefert das erste direkte Kindelement mit Namespace und lokalem Namen oder null.
     */
    public static Element findChild(Element parent, String namespace, String localName) {
        if (parent == null) {
            return null;
        }
        for (Node node = parent.getFirstChild(); node != null; node = node.getNextSibling()) {
            if (node.getNodeType() == Node.ELEMENT_NODE
                    && namespace.equals(node.getNamespaceURI())
                    && localName.equals(node.getLocalName())) {
                return (Element) node;
            }
        }
        return null;
    }

    /**
     * liefert den getrimmten Textinhalt des ersten direkten Kindelements oder null.
     */
    public static String findChildText(Element parent, String namespace, String localName) {
        Element child = findChild(parent, namespace, localName);
        return child == null ? null : child.getTextContent().trim();
    }

    /**
     * maskiert Sonderzeichen für die Verwendung in XML-Text und XML-Attributen.
     */
    public static String escape(String value) {
        if (value == null) {
            return null;
        }
        return value.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&apos;");
    }

    /**
     * XML-Parsing für einen String.
     *
     * @param xml Xml-String
     * @return W3C Document
     * @throws ParserConfigurationException
     * @throws SAXException
     * @throws IOException
     */
    public static Document parseXML(String xml) throws ParserConfigurationException, SAXException, IOException {
        DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
        DocumentBuilder db = dbf.newDocumentBuilder();
        Document doc = db.parse(new InputSource(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8))));
        doc.getDocumentElement().normalize();
        return doc;
    }

    /**
     * sucht in eiem {@link Document} nach einem Tag und liefert den Textinhalt als String.
     *
     * @param doc     Xml-Document
     * @param tagname
     * @return Textinhalt
     */
    public static String findValueByTagname(Document doc, String tagname) {
        NodeList nodeList = doc.getElementsByTagName("*");
        for (int i = 0; i < nodeList.getLength(); i++) {
            Node node = nodeList.item(i);
            if (node.getNodeType() == Node.ELEMENT_NODE) {
                // do something with the current element
                String nodeName = node.getNodeName();
                if (nodeName.equalsIgnoreCase(tagname)) {
                    return node.getTextContent();
                }
            }
        }
        return null;
    }
}
