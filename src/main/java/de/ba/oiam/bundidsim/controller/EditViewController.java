package de.ba.oiam.bundidsim.controller;

import de.ba.oiam.bundidsim.model.BundIdUser;
import de.ba.oiam.bundidsim.model.SamlRequestValues;
import de.ba.oiam.bundidsim.model.Status;
import de.ba.oiam.bundidsim.model.view.EditFormData;
import de.ba.oiam.bundidsim.model.view.EditFormDataValidator;
import de.ba.oiam.bundidsim.model.view.SelectFormData;
import de.ba.oiam.bundidsim.services.AuthResponseService;
import de.ba.oiam.bundidsim.services.SsoSessionService;
import de.ba.oiam.bundidsim.services.UserDefinitionService;
import de.ba.oiam.bundidsim.utils.AuthLevelTools;
import de.ba.oiam.bundidsim.utils.ObjectStringConverter;
import de.ba.oiam.bundidsim.utils.PersonTools;
import de.ba.oiam.bundidsim.utils.SchnellAnmeldungTools;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.util.StringUtils;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.InitBinder;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;

import java.util.Arrays;


/**
 * Webcontroller für die Detail-Bearbeitung einer Person.
 *
 * Geändert 2026 (Fork bpsim/standardkonform, siehe FORK.md): Response über AuthResponseService, erfolgreiche
 * Anmeldung als Anmeldesitzung (SSO); seit 2026-10-18 bPK2/E-Mail mit dem fachlichen Kontext der Auswahl, Mittel mit
 * Niveau und „Zurück zur Auswahl“ (POST /edit/zurueck).
 */
@Controller
@Slf4j
public class EditViewController {

    @Autowired
    private UserDefinitionService userService;

    @Autowired
    private EditFormDataValidator validator;

    @Autowired
    private AuthResponseService authResponseService;

    @Autowired
    private SsoSessionService ssoSessionService;

    @InitBinder("eformdata")
    protected void initBinder(WebDataBinder dataBinder) {
        // Form target
        log.debug("call initBinder()");
        dataBinder.setValidator(validator);
    }

    /**
     * Aus dem QuickSelectionView wird über den Button "Person bearbeiten" diese Methode aufgerufen.
     */
    @PostMapping(path = "/edit")
    public String startEditView(Model model, @ModelAttribute("formdata") SelectFormData formData) {

        String userId = formData.getUserId();
        log.debug("call startEditView with user [{}]", userId);

        BundIdUser user = userService.getUserById(userId);
        SamlRequestValues requestParams =
                ObjectStringConverter.decodeAndDeserialize(
                        formData.getSamlRequest(), SamlRequestValues.class);

        // Fork: bPK2 und E-Mail so zeigen, wie sie mit dem gewählten fachlichen Kontext in die Response gingen
        if (StringUtils.hasText(formData.getDomainContext())) {
            user = PersonTools.applySelection(user, AuthLevelTools.IDENTIFICATION_EID, null, null,
                    formData.getDomainContext().trim());
        }

        // Formmodel bereitstellen
        EditFormData editFormData =
                EditFormData.builder().samlRequest(formData.getSamlRequest())
                        .status(SelectFormData.STATUS_OK)
                        .identifikationWith(AuthLevelTools.IDENTIFICATION_EID)
                        .domainContext(formData.getDomainContext())
                        .user(user).build();

        model.addAttribute("eformdata", editFormData);
        // Fork: Mittel mit Niveau (AuswahlSeite)
        AuswahlSeite.mittel(model, requestParams.getReqAuthnLevel());
        log.debug("Model: [{}]", editFormData.toString());
        return "edit_view";
    }

    /**
     * Fork: zurück zur Personenauswahl, ohne die Anmeldung abzubrechen – vorher gab es nur „Abbrechen“, und das
     * schickt dem Dienst einen Abbruch. Person, Mittel und fachlicher Kontext bleiben gewählt.
     */
    @PostMapping(path = "/edit/zurueck")
    public String backToSelection(Model model, HttpServletRequest request, HttpServletResponse response,
                                  @ModelAttribute("eformdata") EditFormData formData) {
        SamlRequestValues requestParams =
                ObjectStringConverter.decodeAndDeserialize(formData.getSamlRequest(), SamlRequestValues.class);
        AuswahlSeite.auswahl(model, userService, requestParams);
        String[][] identWithList = AuthLevelTools.createIdentificationWithList(requestParams.getReqAuthnLevel());

        String userId = formData.getUser() == null ? null : formData.getUser().getId();
        if (userId == null || userService.getUserById(userId) == null) {
            userId = userService.getUserList().getFirst().getId();
        }
        String identification = Arrays.stream(identWithList).anyMatch(i -> i[0].equals(formData.getIdentifikationWith()))
                ? formData.getIdentifikationWith() : AuthLevelTools.IDENTIFICATION_EID;
        String domainContext = StringUtils.hasText(formData.getDomainContext()) ? formData.getDomainContext()
                : SchnellAnmeldungTools.auswahlKontext(SchnellAnmeldungTools.geraeteKennung(request, response));

        model.addAttribute("formdata", SelectFormData.builder()
                .samlRequest(formData.getSamlRequest())
                .status(SelectFormData.STATUS_OK)
                .userId(userId)
                .identifikationWith(identification)
                .domainContext(domainContext)
                .build());
        return "select_view";
    }

    @PostMapping(path = "/edit/submit")
    public String submitEditView(
            Model model,
            HttpServletRequest request,
            @Valid @ModelAttribute("eformdata") EditFormData formData,
            BindingResult bindingResult) {

        log.debug("call submitEditView");

        SamlRequestValues requestParams =
                ObjectStringConverter.decodeAndDeserialize(
                        formData.getSamlRequest(), SamlRequestValues.class);

        if (bindingResult != null && bindingResult.hasErrors()) {
            log.debug("validationErrors found...");
            AuswahlSeite.mittel(model, requestParams.getReqAuthnLevel());
            return "edit_view";
        }

        // Validierung OK, SAML-Response erstellen
        Status samlStatus = Status.createStatusFromKey(formData.getStatus());
        BundIdUser user = formData.getUser();
        addDataToUser(user, formData);
        log.debug("BundIdUser: [{}]", user);
        // Fork: erfolgreiche Anmeldung mit bearbeiteten Daten als Anmeldesitzung (SSO) merken
        if (SelectFormData.STATUS_OK.equalsIgnoreCase(formData.getStatus())) {
            ssoSessionService.remember(request, user, user.getId(), null);
        }
        return authResponseService.prepareSamlResponse(model, formData.getSamlRequest(), samlStatus, user, user.getEidCitizenQaaLevel());
    }

    @PostMapping(path = "/edit/cancel")
    public String cancelEditView(
            Model model,
            @ModelAttribute("eformdata") EditFormData formData) {

        log.debug("call cancelEditView");

        Status samlStatus = Status.buildCancelStatus();
        return authResponseService.prepareSamlResponse(model, formData.getSamlRequest(), samlStatus, null, "");
    }

    // private Helper ****************************************************************************************************

    private void addDataToUser(BundIdUser user, EditFormData formData) {

        // User-Daten vervollständigen
        user.setAssertionProvedBy(formData.getIdentifikationWith()); // Identifizierungsmittel
        user.setEidCitizenQaaLevel(
                AuthLevelTools.getAuthnLevelByIdentificationMethod(formData.getIdentifikationWith()));
        user.setVersion("2021.7.1");
        if (AuthLevelTools.IDENTIFICATION_EIDAS.equalsIgnoreCase(formData.getIdentifikationWith())) {
            // Speziell für eIDAS-Identifikation
            user.setEidasIssuingCountry(formData.getEidasCountry());
            user.setEidCitizenQaaLevel(AuthLevelTools.getAuthnLevelByLoa(formData.getEidasLoa()));
        }
    }

}
