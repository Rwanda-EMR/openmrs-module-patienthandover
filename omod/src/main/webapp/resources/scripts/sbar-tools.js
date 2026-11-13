/*
 * SBAR tools for the Make Patient Handover page (locationHandover.gsp):
 *  - highlights abnormal laboratory result lines (flag such as [\u2193 LOW]) inside the SBAR boxes;
 *  - "Remove dates" and "Remove titles" for the selected patients.
 * Kept out of the .gsp on purpose: Groovy templates treat backslashes as their own escapes, so regular
 * expressions and '\n' cannot be written there. Uses from the page: sbarWrapper, refreshSuggestionMarker,
 * sbarBeforeClear, scheduleHandoverDraftSave, setDraftStatus, sbarConfiguredTitles, sbarKeptTitles.
 */
/* Abnormal laboratory results: lines with a flag such as [\u2193 LOW] are highlighted. A
   textarea cannot colour text, so a layer behind the (transparent) box marks those lines. */
var LAB_FLAG = /\[(?:\u2191\u2191 CRITICAL HIGH|\u2193\u2193 CRITICAL LOW|\u2191 HIGH|\u2193 LOW|ABNORMAL)\]|\((?:critical high|critical low|high|low|abnormal)\)(?= \(|\s*$)/;

function escapeHtml(text) {
    return text.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;');
}

function setupSbarHighlight(textarea) {
    if (textarea.getAttribute('data-highlight') === 'on') return textarea.previousSibling;
    var wrap = document.createElement('div');
    wrap.className = 'ph-sbar-hl-wrap';
    var backdrop = document.createElement('div');
    backdrop.className = 'ph-sbar-hl-backdrop';
    backdrop.setAttribute('aria-hidden', 'true');
    textarea.parentNode.insertBefore(wrap, textarea);
    wrap.appendChild(backdrop);
    wrap.appendChild(textarea);
    var style = window.getComputedStyle(textarea);
    var copy = ['paddingTop', 'paddingRight', 'paddingBottom', 'paddingLeft', 'fontFamily', 'fontSize', 'fontWeight',
        'lineHeight', 'letterSpacing', 'wordSpacing', 'tabSize', 'borderTopLeftRadius', 'borderTopRightRadius',
        'borderBottomLeftRadius', 'borderBottomRightRadius'];
    for (var i = 0; i < copy.length; i++) backdrop.style[copy[i]] = style[copy[i]];
    textarea.setAttribute('data-highlight', 'on');
    textarea.addEventListener('scroll', function() { backdrop.scrollTop = textarea.scrollTop; });
    if (window.ResizeObserver) new ResizeObserver(function() { placeSbarBackdrop(textarea, backdrop); }).observe(textarea);
    return backdrop;
}

function placeSbarBackdrop(textarea, backdrop) {
    backdrop.style.top = textarea.clientTop + 'px';
    backdrop.style.left = textarea.clientLeft + 'px';
    backdrop.style.width = textarea.clientWidth + 'px';   // excludes the scroll bar, so lines wrap the same
    backdrop.style.height = textarea.clientHeight + 'px';
    backdrop.scrollTop = textarea.scrollTop;
}

function refreshSbarHighlight(textarea) {
    if (!textarea || !sbarWrapper(textarea)) return;
    var backdrop = setupSbarHighlight(textarea);
    var lines = textarea.value.split('\n');
    var html = [];
    var flagged = false;
    for (var i = 0; i < lines.length; i++) {
        if (LAB_FLAG.test(lines[i])) {
            flagged = true;
            html.push('<mark>' + escapeHtml(lines[i]) + '</mark>');
        } else {
            html.push(escapeHtml(lines[i]));
        }
    }
    backdrop.innerHTML = html.join('\n') + '\n ';
    var wrapper = sbarWrapper(textarea);
    if (flagged) wrapper.classList.add('has-abnormal-lab'); else wrapper.classList.remove('has-abnormal-lab');
    placeSbarBackdrop(textarea, backdrop);
}

/* Remove dates / Remove titles for all selected patients, once the text has been reviewed. */
var SBAR_DATE = /\s*\(\d{1,2} [^\s()]{2,6} \d{1,2}:\d{2}\)\s*$/;
var LAB_TITLE = 'Abnormal lab - ';

/** Titles to remove: the configured concepts plus any other title in this page's pre-filled text
    (e.g. a diagnosis field listed under DIAGNOSES). */
function sbarTitles() {
    var titles = {};
    for (var t = 0; t < sbarConfiguredTitles.length; t++) titles[sbarConfiguredTitles[t]] = true;
    var fields = document.querySelectorAll('.ph-sbar-input textarea[data-suggestion]');
    for (var i = 0; i < fields.length; i++) {
        var lines = fields[i].getAttribute('data-suggestion').split('\n');
        for (var j = 0; j < lines.length; j++) {
            var colon = lines[j].indexOf(': ');
            if (colon > 0) titles[lines[j].substring(0, colon)] = true;
        }
    }
    for (var k = 0; k < sbarKeptTitles.length; k++) delete titles[sbarKeptTitles[k]];
    return titles;
}

function cleanSbarLine(line, kind, titles) {
    if (kind === 'dates') return line.replace(SBAR_DATE, '');
    if (line.indexOf(LAB_TITLE) === 0) return line.substring(LAB_TITLE.length); // keep the test name and flag
    var colon = line.indexOf(': ');
    return colon > 0 && titles[line.substring(0, colon)] ? line.substring(colon + 2) : line;
}

function cleanSelectedSbar(kind) {
    var selected = document.querySelectorAll('.patientSelection:checked');
    if (!selected.length) {
        alert('Select the patients first (tick the box next to each patient).');
        return;
    }
    var titles = kind === 'titles' ? sbarTitles() : null;
    var names = ['situation', 'background', 'assessment', 'recommendation'];
    var changedPatients = 0;
    for (var i = 0; i < selected.length; i++) {
        var patientId = selected[i].value;
        var before = {};
        for (var b = 0; b < names.length; b++) {
            var box = document.getElementById(names[b] + '_' + patientId);
            before[names[b]] = box ? box.value : '';
        }
        var changed = false;
        for (var j = 0; j < names.length; j++) {
            var field = document.getElementById(names[j] + '_' + patientId);
            if (!field || !field.value) continue;
            var lines = field.value.split('\n');
            for (var k = 0; k < lines.length; k++) lines[k] = cleanSbarLine(lines[k], kind, titles);
            var cleaned = lines.join('\n');
            if (cleaned !== field.value) {
                field.value = cleaned;
                refreshSuggestionMarker(field);
                changed = true;
            }
        }
        if (changed) {
            changedPatients++;
            if (!sbarBeforeClear[patientId]) sbarBeforeClear[patientId] = before; // for Restore SBAR
            var card = document.getElementById('sbarCleared_' + patientId);
            while (card && !(card.classList && card.classList.contains('ph-patient-card'))) card = card.parentNode;
            if (card) card.classList.add('is-sbar-cleaned');
        }
    }
    if (!changedPatients) {
        alert(kind === 'dates' ? 'No dates found in the SBAR text of the selected patients.'
                : 'No titles found in the SBAR text of the selected patients.');
        return;
    }
    scheduleHandoverDraftSave();
    setDraftStatus('saved', (kind === 'dates' ? 'Dates' : 'Titles') + ' removed for ' + changedPatients
            + ' patient(s). Restore SBAR brings back the original text.');
}

