"""Builds the Handover Module User Guide 2.0.0 from the 1.5.12 guide by editing its WordprocessingML in place.

Usage: unzip docs/Handover_Module_User_Guide_1.5.12.docx into a directory, run
    python3 tools/update_user_guide_200.py <directory>
and zip the directory again as docs/Handover_Module_User_Guide_2.0.0.docx.

Only the Python standard library is used. Paragraphs and table rows are changed by replacing the text of their
existing runs, or by cloning an existing paragraph or row, so the formatting of the guide is kept.
"""
import re
import sys
from xml.sax.saxutils import escape

SRC = sys.argv[1]
OLD_VERSION = '1.5.12'
VERSION = '2.0.0'

path = SRC + '/word/document.xml'
doc = open(path, encoding='utf-8').read()

T_RUN = re.compile(r'(<w:t(?: [^>]*)?>)([^<]*)(</w:t>)')


def find_once(needle):
    count = doc.count(needle)
    if count != 1:
        raise SystemExit('anchor %r found %d times' % (needle, count))
    return doc.index(needle)


def bounds(anchor, open_tag, close_tag):
    i = find_once(anchor)
    start = max(doc.rfind(open_tag + '>', 0, i), doc.rfind(open_tag + ' ', 0, i))
    end = doc.index(close_tag, i) + len(close_tag)
    return start, end


def para_bounds(anchor):
    return bounds(anchor, '<w:p', '</w:p>')


def row_bounds(anchor):
    return bounds(anchor, '<w:tr', '</w:tr>')


def with_texts(xml, texts):
    runs = T_RUN.findall(xml)
    if len(runs) != len(texts):
        raise SystemExit('element has %d text runs, %d texts given: %r' % (len(runs), len(texts), xml[:200]))
    it = iter(texts)
    return T_RUN.sub(lambda m: '<w:t xml:space="preserve">' + escape(next(it)) + m.group(3), xml)


def retext(anchor, *texts):
    """Replaces the text runs of the paragraph containing anchor, keeping their formatting."""
    global doc
    s, e = para_bounds(anchor)
    doc = doc[:s] + with_texts(doc[s:e], texts) + doc[e:]


def clone_after(anchor, *texts):
    """Inserts a copy of the paragraph containing anchor, with new texts, right after it."""
    global doc
    s, e = para_bounds(anchor)
    doc = doc[:e] + with_texts(doc[s:e], texts) + doc[e:]


def retext_row(anchor, *texts):
    global doc
    s, e = row_bounds(anchor)
    doc = doc[:s] + with_texts(doc[s:e], texts) + doc[e:]


def clone_row_after(anchor, *texts):
    global doc
    s, e = row_bounds(anchor)
    doc = doc[:e] + with_texts(doc[s:e], texts) + doc[e:]


# ----------------------------------------------------------------------------------------------------------
# Version
# ----------------------------------------------------------------------------------------------------------
doc = doc.replace(OLD_VERSION, VERSION)

# ----------------------------------------------------------------------------------------------------------
# Installation and upgrade
# ----------------------------------------------------------------------------------------------------------
retext_row('Make Patient Handover drafts', 'Make Patient Handover drafts',
           'Kept only in the open browser tab while moving between worklist pages; not stored in the database')
retext('Upgrading from 1.4 or 1.5.x: ', 'Upgrading from 1.4 or 1.5.x: ',
       'No manual SQL is required. On the first start, version 2.0 updates the SBAR concept settings to the new '
       'defaults wherever they still hold an earlier default; settings changed by hand are kept. Drafts saved on the '
       'server by earlier versions are deleted when their owner next opens Make Patient Handover. After deploying, ask '
       'users to refresh the browser with Ctrl+F5 so the new page scripts and styles are loaded.')

# ----------------------------------------------------------------------------------------------------------
# Section 4: SBAR from patient records
# ----------------------------------------------------------------------------------------------------------
retext('Investigation summary (the INVESTIGATION SUMMARY box',
       'Investigation summary (TREATMENT PLAN OTHER REMARKS on the admission form) or plan (progress_plan): the plan '
       'is shown only when it was recorded after the investigation summary. Then treatment/action taken (PROGRESS NOTE, '
       'INVESTIGATION/TREATMENT) and health education needs.')
retext('Current admission only: ', 'Current admission only: ',
       'only information saved at or after the patient’s admission time is used, for every box, and each line shows '
       'when it was saved. Information saved together with the admission, such as the clinical summary, final '
       'diagnosis and investigation summary on the admission form, is included. The admission time is the latest of: '
       'the start of the open visit, the latest admission form, and the start of the last stretch of care. A gap of '
       'more than 72 hours between two encounters means the patient left and came back, so the stay starts at the '
       'return. Today’s date is not used: information entered after the last admission is shown however long ago that '
       'admission was. Point at the REVIEW badge to see the admission time used.')
retext('Abnormal laboratory results are results of laboratory orders',
       'Abnormal laboratory results are results of laboratory orders that lie outside the test’s reference range or '
       'are coded Positive, Abnormal, Reactive or Detected. Only the latest result of each test is considered, up to ten '
       'tests per patient. Each result carries a flag — [↓ LOW], [↑ HIGH], [↓↓ CRITICAL LOW], [↑↑ CRITICAL HIGH] or '
       '[ABNORMAL] — and its line is highlighted in red in the Assessment box, which is then labelled Abnormal lab. The '
       'same lines are highlighted when the saved handover is viewed or printed.')
retext('Restore SBAR ', 'Restore SBAR ',
       'appears after Clear SBAR, Remove dates or Remove titles and brings back the text as it was before. After a page '
       'reload it brings back the text from the patient’s records.')
clone_after('Restore SBAR ', 'Remove dates ',
            '(Patients toolbar) removes the dates in brackets, such as (19 Aug 14:06), from the SBAR text of all selected '
            'patients. Use it after reviewing the text.')
clone_after('(Patients toolbar) removes the dates in brackets', 'Remove titles ',
            '(Patients toolbar) removes titles such as “Progress plan:” or “Clinican notes:” from the SBAR text of all '
            'selected patients. Vital signs keep their titles (Pulse: 68.0) and laboratory results keep the test name '
            'and flag; only “Abnormal lab - ” is removed. Text you typed yourself is not changed.')
retext('opens a list of what was found for the patient', 'Why? ',
       '(System Developer role only) opens a list of what was found for the patient: the visit start, the latest '
       'admission form, the latest encounter and the admission time used, then for every configured field its latest '
       'value, when it was saved and whether it was used or why not (for example saved before the admission time, or '
       'concept not found). Use it when expected text does not appear.')

# ----------------------------------------------------------------------------------------------------------
# Section 4: drafts
# ----------------------------------------------------------------------------------------------------------
retext('Drafts: continue where you stopped', 'Drafts: nothing is lost while you work')
retext('Everything entered on Make Patient Handover is saved automatically as a draft',
       'What you enter on Make Patient Handover is kept automatically as a draft in the open browser tab, so nothing '
       'is lost while you move between pages of the worklist or when a form is returned with an error.')
retext('You may leave the page, log out or close the browser at any time.',
       'The draft is removed when you refresh the page, close the tab, open the page in a new tab or another browser, '
       'submit the handover or choose Discard draft, and after 12 hours without changes '
       '(setting patienthandover.draft.maxAgeHours). It is not stored in your user account.')
retext('Draft status: ', 'What is kept: ',
       'only what you typed or changed. SBAR text left exactly as it was filled in from records is not kept; it is '
       'filled in again from the latest records when the page opens.')
retext('Discard draft, in the bottom bar, deletes the draft from the browser and from your account',
       'Discard draft, in the bottom bar, deletes the draft and clears the form.')

# ----------------------------------------------------------------------------------------------------------
# Configuration and database
# ----------------------------------------------------------------------------------------------------------
retext_row('patienthandover.sbar.episodeGapHours', 'patienthandover.sbar.episodeGapHours', '72',
           'Hours between two encounters after which the patient is treated as having left and come back; protects '
           'against visits that were never closed. Today’s date is not used.')
clone_row_after('patienthandover.sbar.episodeGapHours', 'patienthandover.draft.maxAgeHours', '12',
                'Hours an unsent Make Patient Handover draft is kept in the browser tab without changes')
i = find_once('Version 1.5 adds patient_handover_draft')
s, e = para_bounds('Version 1.5 adds patient_handover_draft')
para_text = ''.join(m[1] for m in T_RUN.findall(doc[s:e]))
retext('Version 1.5 adds patient_handover_draft', para_text.replace(
    'Version 1.5 adds patient_handover_draft, which keeps one unsubmitted Make Patient Handover form per user, location '
    'and care setting.',
    'Version 1.5 added patient_handover_draft for unsubmitted Make Patient Handover forms; from version 2.0 drafts '
    'stay in the browser tab and the table is no longer used.'))

# ----------------------------------------------------------------------------------------------------------
# Checks after deployment and troubleshooting
# ----------------------------------------------------------------------------------------------------------
retext('A draft started on one computer is restored on another',
       'Text typed for a patient on one worklist page is still there after moving to another page and back, and is '
       'gone after refreshing the page. Abnormal laboratory lines are highlighted in red.')
retext_row('Draft status shows Saved on this device only', 'Typed text is gone after refreshing the page',
           'Drafts are kept only while moving between pages of the same tab (version 2.0)',
           'This is expected. Submit before refreshing; use the worklist page links rather than the browser refresh '
           'button.')
retext('Refresh with Ctrl+F5 and confirm that version 1.5.2 or later is deployed.',
       'Refresh with Ctrl+F5 and confirm that version %s is deployed.' % VERSION)

# ----------------------------------------------------------------------------------------------------------
# What's new
# ----------------------------------------------------------------------------------------------------------
retext_row('Drafts in the user account', 'Drafts in the browser tab',
           'Make Patient Handover keeps unsent work only in the open tab while moving between worklist pages. Refresh, '
           'a new tab, submission, Discard draft or 12 hours without changes remove it. See section 4.')
clone_row_after('SBAR from patient records', 'Abnormal laboratory results highlighted',
                'Abnormal results carry a flag such as [↓ LOW] and are highlighted in red on Make Patient Handover and '
                'in saved handovers. See section 4.')
clone_row_after('Abnormal laboratory results highlighted', 'Remove dates and Remove titles',
                'After review, remove the dates and the titles from the SBAR text of all selected patients; vital '
                'signs and laboratory test names keep their titles. See section 4.')
clone_row_after('Remove dates and Remove titles', 'Why? for System Developers',
                'The explanation of the SBAR pre-fill is shown only to the System Developer role.')

open(path, 'w', encoding='utf-8').write(doc)

for name in ['/word/footer1.xml', '/docProps/core.xml', '/docProps/app.xml']:
    p = SRC + name
    try:
        content = open(p, encoding='utf-8').read()
    except FileNotFoundError:
        continue
    open(p, 'w', encoding='utf-8').write(content.replace(OLD_VERSION, VERSION))
print('ok')
