/*
 * Neu 2026 im Fork bpsim/standardkonform (siehe FORK.md). Lizenz wie das Gesamtprojekt: Apache License 2.0.
 *
 * Kleine Hilfen für die Seiten mit Personenauswahl (Auswahl, Angaben ändern, Anmeldung am Postfach). Als Datei statt
 * Inline-Skript, damit ein Reverse Proxy die CSP "script-src 'self'" setzen kann. Ohne Skript funktionieren alle
 * Seiten ebenso – der Knopf heißt dann allgemein "Mit der gewählten Person anmelden".
 *
 * - Formulare mit data-bpsim-auswahl: Der Knopf nennt die gewählte Person (Radio mit data-name → [data-bpsim-name])
 *   bzw. bei Status "Abgebrochen"/"Fehler", was er wirklich schickt ([data-bpsim-status]).
 * - Die Zusammenfassung von "Erweitert" zeigt Status und fachlichen Kontext ([data-bpsim-statustext],
 *   [data-bpsim-kontext]), auch nach einer Änderung.
 * - Fehlerzusammenfassung (#fehler) bekommt den Fokus, damit Screenreader sie sofort vorlesen.
 */
(function () {
    'use strict';
    document.documentElement.classList.add('bpsim-js');

    function aktualisieren(form) {
        var person = form.querySelector('input[type=radio][data-name]:checked');
        form.querySelectorAll('[data-bpsim-name]').forEach(function (el) {
            if (person) {
                el.textContent = person.getAttribute('data-name');
            }
        });

        var status = form.querySelector('select[name=status]');
        var wert = status ? status.value : 'OK';
        form.querySelectorAll('[data-bpsim-status]').forEach(function (el) {
            el.hidden = el.getAttribute('data-bpsim-status') !== wert;
        });
        form.querySelectorAll('[data-bpsim-statustext]').forEach(function (el) {
            if (status && status.selectedIndex >= 0) {
                el.textContent = status.options[status.selectedIndex].text;
            }
        });

        var kontext = form.querySelector('input[name=domainContext]');
        form.querySelectorAll('[data-bpsim-kontext]').forEach(function (el) {
            if (kontext) {
                el.textContent = kontext.value.trim() === '' ? '(leer)' : kontext.value;
            }
        });
    }

    function start() {
        document.querySelectorAll('form[data-bpsim-auswahl]').forEach(function (form) {
            form.addEventListener('change', function () { aktualisieren(form); });
            form.addEventListener('input', function () { aktualisieren(form); });
            aktualisieren(form);
        });
        var fehler = document.getElementById('fehler');
        if (fehler) {
            fehler.focus();
        }
    }

    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', start);
    } else {
        start();
    }
})();
