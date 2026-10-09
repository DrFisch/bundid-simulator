/*
 * Neu 2026 im Fork bpsim/standardkonform (siehe FORK.md): Auto-POST der SAML-Response (HTTP-POST-Binding).
 * Vorher stand dieses Skript inline in auth_response.html und wurde per onload-Attribut gestartet. Als eigene Datei
 * erlaubt es eine Content-Security-Policy mit script-src 'self' – ohne 'unsafe-inline'. Verhalten unverändert.
 */
(function () {
    function setRequestPathCookies() {
        var requestPath = window.location.pathname + window.location.search + window.location.hash;
        document.cookie = "saml_request_path=\"+encodeURIComponent(requestPath)+\";path=/;";
    }

    window.addEventListener('load', function () {
        setRequestPathCookies();
        document.forms[0].submit();
    });
})();
