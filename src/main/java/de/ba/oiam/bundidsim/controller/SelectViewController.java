package de.ba.oiam.bundidsim.controller;

import de.ba.oiam.bundidsim.model.BundIdUser;
import de.ba.oiam.bundidsim.model.SamlRequestValues;
import de.ba.oiam.bundidsim.model.Status;
import de.ba.oiam.bundidsim.model.view.SelectFormData;
import de.ba.oiam.bundidsim.model.view.SelectFormDataValidator;
import de.ba.oiam.bundidsim.services.AuthResponseService;
import de.ba.oiam.bundidsim.services.SsoSessionService;
import de.ba.oiam.bundidsim.services.UserDefinitionService;
import de.ba.oiam.bundidsim.utils.AuthLevelTools;
import de.ba.oiam.bundidsim.utils.ObjectStringConverter;
import jakarta.servlet.http.HttpServletRequest;
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


/**
 * Schnellauswahl einer Person und Identifizierungsdaten.
 *
 * Geändert 2026 (Fork bpsim/standardkonform, siehe FORK.md): Response über AuthResponseService, erfolgreiche
 * Anmeldung als Anmeldesitzung (SSO).
 */
@Controller
@Slf4j
public class SelectViewController {

    @Autowired
    private AuthResponseService authResponseService;

    @Autowired
    private SsoSessionService ssoSessionService;

    @Autowired
    private UserDefinitionService userService;

    @Autowired
    private SelectFormDataValidator validator;

    /**
     * setzt den Form-Validator
     *
     * @param dataBinder
     */
    @InitBinder("formdata")
    protected void initBinder(WebDataBinder dataBinder) {
        // Form target
        log.debug("call initBinder()");
        dataBinder.setValidator(validator);
    }


    /**
     * Methode wird beim Formular-Submit aufgerufen.
     *
     * @param model
     * @param formData
     * @return
     */
    @PostMapping(path = "/select/submit")
    public String submitFormPage(
            Model model,
            HttpServletRequest request,
            @Valid @ModelAttribute("formdata") SelectFormData formData,
            BindingResult bindingResult) {

        log.debug("call QuickSelectionView-Submit...");
        // Ursprünglichen SAML-Request wiederherstellen
        SamlRequestValues requestParams =
                ObjectStringConverter.decodeAndDeserialize(
                        formData.getSamlRequest(), SamlRequestValues.class);
        log.debug("call submitFormPage, SamlRequest: [{}]", requestParams);
        log.debug("Form Data [{}]", formData);
        log.debug("user: [{}]", formData.getUserId());

        // Validierungsfehler behandeln
        if (bindingResult != null && bindingResult.hasErrors()) {
            log.debug("validationErrors found...");
            model.addAttribute("userlist", userService.getUserList());
            model.addAttribute(
                    "identWithList",
                    AuthLevelTools.createIdentificationWithList(requestParams.getReqAuthnLevel()));
            return "select_view";
        }

        // Validierung OK, SAML-Response erstellen
        Status samlStatus = Status.createStatusFromKey(formData.getStatus());
        BundIdUser user = userService.getUserById(formData.getUserId());
        user = addDataToUser(user, formData);
        log.debug("BundIdUser: [{}]", user);
        // Fork: erfolgreiche Anmeldung als Anmeldesitzung (SSO) merken
        if (SelectFormData.STATUS_OK.equalsIgnoreCase(formData.getStatus())) {
            ssoSessionService.remember(request, user, formData.getUserId(), formData.getDomainContext());
        }
        return authResponseService.prepareSamlResponse(model, formData.getSamlRequest(), samlStatus, user, user.getEidCitizenQaaLevel());
    }

    /**
     * Formular abbrechen -> ein Cancel-Response wird generiert
     *
     * @param model
     * @return
     */
    @PostMapping(path = "/select/cancel")
    public String cancelFormPage(Model model, @ModelAttribute("formdata") SelectFormData formData) {
        log.debug("call QuickSelectionView-Cancel...");
        Status samlStatus = Status.buildCancelStatus();

        return authResponseService.prepareSamlResponse(model, formData.getSamlRequest(), samlStatus, null, "");
    }

    // private helper


    private BundIdUser addDataToUser(BundIdUser user, SelectFormData formData) {

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

        String domainContext = formData.getDomainContext();
        if (StringUtils.hasText(domainContext)) {
            // Suffix an die bpk2 anfügen
            user.setBpk2(user.getBpk2() + domainContext);
            user.setMail(changeMailAddress(user.getMail(), domainContext));
        }
        return user;
    }

    // ****************************************************************************************************


    /**
     * Manipulation einer Email-Adresse: Gegeben: email: "test@online.de", context: "-team" Ergebnis:
     * "test-team@online.de"
     *
     * @param email
     * @param context
     * @return
     */
    private String changeMailAddress(String email, String context) {
        String[] emailParts = email.split("@");
        return emailParts[0] + context + "@" + emailParts[1];
    }
}
