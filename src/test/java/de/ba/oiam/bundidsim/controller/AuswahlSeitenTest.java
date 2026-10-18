/*
 * Neu 2026 im Fork bpsim/standardkonform (siehe FORK.md). Lizenz wie das Gesamtprojekt: Apache License 2.0.
 */
package de.ba.oiam.bundidsim.controller;

import de.ba.oiam.bundidsim.utils.SchnellAnmeldungTools;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Rendert die Seiten mit Personenauswahl wirklich (Thymeleaf) und prüft, was Befund B15 behebt: Das Formular ist vom
 * ersten Aufruf an vollständig, der Knopf nennt die Person, Fehler stehen oben, der Rückweg aus „Angaben ändern“ und
 * die Schnellanmeldung am Postfach funktionieren.
 */
@SpringBootTest(properties = {"app.sso.enabled=true", "app.postfach.enabled=true",
        "app.postfach.storage-dir=target/postfach-test"})
class AuswahlSeitenTest {

    private static final Pattern SAML_REQUEST = Pattern.compile("name=\"samlRequest\"[^>]*value=\"([^\"]+)\"");

    @Autowired
    private WebApplicationContext context;

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(context).build();
    }

    @Test
    void selectionIsCompleteFromTheFirstCall() throws Exception {
        MvcResult result = mvc.perform(get("/saml")).andExpect(status().isOk()).andReturn();
        String html = result.getResponse().getContentAsString();
        Cookie device = result.getResponse().getCookie(SchnellAnmeldungTools.COOKIE);

        assertThat(device).isNotNull();
        // Pflichtfeld "fachlicher Kontext" je Gerät vorbelegt
        assertThat(html).contains("value=\"" + SchnellAnmeldungTools.auswahlKontext(device.getValue()) + "\"");
        // Knopf nennt die vorausgewählte Person, IDs für Tests und Skripte unverändert
        assertThat(html).contains("data-bpsim-name>Maria Neumann<")
                .contains("id=\"applyPerson\"", "id=\"user_1\"", "id=\"userKachel_2\"", "id=\"identWith\"",
                        "id=\"domaincontext\"", "id=\"schnellAnmelden\"", "id=\"schnellModal\"", "id=\"schnellBestaetigen\"")
                // Anfrage des Dienstes und Niveau je Mittel sichtbar, kein Fehler
                .contains("Gefordert", "EID (Ausweis) – Niveau hoch", "/select/submit#fehler")
                .doesNotContain("id=\"fehler\"");
    }

    @Test
    void emptyContextIsRejectedWithSummaryAtTheTop() throws Exception {
        String html = mvc.perform(post("/select/submit").param("userId", "U02").param("identifikationWith", "eID")
                        .param("status", "OK").param("domainContext", " ").param("samlRequest", samlRequest()))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();

        assertThat(html).contains("id=\"fehler\"", "Fachlicher Kontext fehlt", "href=\"#domaincontext\"")
                .doesNotContain("SAMLResponse");
        // Zusammenfassung vor der Personenliste, "Erweitert" offen
        assertThat(html.indexOf("id=\"fehler\"")).isLessThan(html.indexOf("id=\"user_1\""));
        assertThat(html).containsPattern("<details[^>]*id=\"erweitert\"[^>]*open|<details[^>]*open[^>]*id=\"erweitert\"");
    }

    @Test
    void validSelectionAnswersWithSignedFlowResponse() throws Exception {
        String html = mvc.perform(post("/select/submit").param("userId", "U02").param("identifikationWith", "eID")
                        .param("status", "OK").param("domainContext", "-test").param("samlRequest", samlRequest()))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(html).contains("name=\"SAMLResponse\"");
    }

    @Test
    void backFromEditKeepsPersonAndContext() throws Exception {
        String sr = samlRequest();
        String edit = mvc.perform(post("/edit").param("userId", "U02").param("domainContext", "-lukas")
                        .param("samlRequest", sr))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(edit).contains("BUNDIDSIM-U02-lukas", "m-schmidt-lukas@example.com", "/edit/zurueck");

        String back = mvc.perform(post("/edit/zurueck").param("user.id", "U02").param("identifikationWith", "eID")
                        .param("domainContext", "-lukas").param("samlRequest", sr))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(back).containsPattern("<input(?=[^>]*id=\"user_2\")(?=[^>]*checked)[^>]*>")
                .contains("value=\"-lukas\"", "data-bpsim-name>Manfred Schmidt<");
    }

    @Test
    void postfachOffersQuickLoginAndPrefilledContext() throws Exception {
        MvcResult page = mvc.perform(get("/postfach/anmelden")).andExpect(status().isOk()).andReturn();
        Cookie device = page.getResponse().getCookie(SchnellAnmeldungTools.COOKIE);
        assertThat(page.getResponse().getContentAsString())
                .contains("id=\"postfachSchnell\"", "id=\"postfachAnmelden\"",
                        "value=\"" + SchnellAnmeldungTools.auswahlKontext(device.getValue()) + "\"")
                .doesNotContain("value=\"eIDAS\"");

        MvcResult quick = mvc.perform(post("/postfach/schnell").cookie(device).param("ziel", "https://boese.example/"))
                .andExpect(redirectedUrl("/postfach")).andReturn();
        MockHttpSession session = (MockHttpSession) quick.getRequest().getSession(false);
        String liste = mvc.perform(get("/postfach").session(session)).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(liste).contains("Nachrichten", "John Doe");
    }

    private String samlRequest() throws Exception {
        String html = mvc.perform(get("/saml")).andReturn().getResponse().getContentAsString();
        Matcher m = SAML_REQUEST.matcher(html);
        assertThat(m.find()).isTrue();
        return m.group(1);
    }
}
