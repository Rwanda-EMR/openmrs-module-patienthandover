"""Builds the Handover Module User Guide 1.5.2 from the 1.2.6 guide by editing its WordprocessingML in place.

Only the Python standard library is used. New paragraphs and tables copy the XML conventions of the source
document (Aptos runs, Heading1/Heading2/ListBullet styles, manual bold step numbers, D9EAF7 table headers) so they
look identical to the existing content.
"""
import re
import sys
from xml.sax.saxutils import escape

SRC = sys.argv[1]  # unpacked source directory (modified in place)

RPR = '<w:rPr><w:rFonts w:ascii="Aptos" w:hAnsi="Aptos"/></w:rPr>'
RPR_B = '<w:rPr><w:rFonts w:ascii="Aptos" w:hAnsi="Aptos"/><w:b/></w:rPr>'
BORDERS = ('<w:tcBorders><w:top w:val="single" w:sz="4" w:color="D9D9D9"/><w:left w:val="single" w:sz="4" '
           'w:color="D9D9D9"/><w:bottom w:val="single" w:sz="4" w:color="D9D9D9"/><w:right w:val="single" w:sz="4" '
           'w:color="D9D9D9"/><w:insideH w:val="single" w:sz="4" w:color="D9D9D9"/><w:insideV w:val="single" '
           'w:sz="4" w:color="D9D9D9"/></w:tcBorders>')
MARGINS = ('<w:tcMar><w:top w:w="90" w:type="dxa"/><w:start w:w="100" w:type="dxa"/><w:bottom w:w="90" '
           'w:type="dxa"/><w:end w:w="100" w:type="dxa"/></w:tcMar>')


def t(text):
    return '<w:t xml:space="preserve">%s</w:t>' % escape(text)


def h1(text):
    return '<w:p><w:pPr><w:pStyle w:val="Heading1"/><w:keepNext/></w:pPr><w:r>%s%s</w:r></w:p>' % (RPR, t(text))


def h2(text):
    return '<w:p><w:pPr><w:pStyle w:val="Heading2"/><w:keepNext/></w:pPr><w:r>%s%s</w:r></w:p>' % (RPR, t(text))


def para(text, lead=None):
    runs = ''
    if lead:
        runs += '<w:r>%s%s</w:r>' % (RPR_B, t(lead + ' '))
    runs += '<w:r>%s%s</w:r>' % (RPR, t(text))
    return '<w:p>%s</w:p>' % runs


def step(number, text):
    return ('<w:p><w:pPr><w:spacing w:before="0" w:after="0"/><w:ind w:left="317" w:hanging="317"/></w:pPr>'
            '<w:r>%s%s</w:r><w:r>%s%s</w:r><w:r><w:br/></w:r></w:p>' % (RPR_B, t('%d. ' % number), RPR, t(text)))


def steps(items):
    return ''.join(step(i + 1, s) for i, s in enumerate(items))


def bullet(text, lead=None):
    runs = ''
    if lead:
        runs += '<w:r>%s%s</w:r>' % (RPR_B, t(lead + ' '))
    runs += '<w:r>%s%s</w:r>' % (RPR, t(text))
    return ('<w:p><w:pPr><w:pStyle w:val="ListBullet"/><w:spacing w:before="0" w:after="0"/></w:pPr>%s'
            '<w:r><w:br/></w:r></w:p>' % runs)


def bullets(items):
    return ''.join(bullet(*i) if isinstance(i, tuple) else bullet(i) for i in items)


def table(headers, rows, widths):
    """Table styled like the guide's existing tables; widths in DXA (sum ~9648)."""
    def cell(text, width, header):
        shade = '<w:shd w:val="clear" w:color="auto" w:fill="D9EAF7"/>' if header else ''
        rpr = '<w:rPr><w:b/><w:color w:val="000000"/><w:sz w:val="18"/></w:rPr>' if header else \
            '<w:rPr><w:rFonts w:ascii="Aptos" w:hAnsi="Aptos"/><w:sz w:val="18"/></w:rPr>'
        # CT_TcPr child order: tcW, tcBorders, shd, tcMar, vAlign
        return ('<w:tc><w:tcPr><w:tcW w:type="dxa" w:w="%d"/>%s%s%s<w:vAlign w:val="center"/></w:tcPr>'
                '<w:p><w:pPr><w:spacing w:after="0"/></w:pPr><w:r>%s%s</w:r></w:p></w:tc>'
                % (width, BORDERS, shade, MARGINS, rpr, t(text)))
    grid = ''.join('<w:gridCol w:w="%d"/>' % w for w in widths)
    out = ('<w:tbl><w:tblPr><w:tblW w:type="dxa" w:w="%d"/><w:jc w:val="center"/><w:tblLayout w:type="fixed"/>'
           '<w:tblLook w:firstColumn="1" w:firstRow="1" w:lastColumn="0" w:lastRow="0" w:noHBand="0" '
           'w:noVBand="1" w:val="04A0"/></w:tblPr><w:tblGrid>%s</w:tblGrid>' % (sum(widths), grid))
    out += '<w:tr><w:trPr><w:tblHeader/><w:cantSplit/></w:trPr>%s</w:tr>' % ''.join(
        cell(h, w, True) for h, w in zip(headers, widths))
    for row in rows:
        out += '<w:tr><w:trPr><w:cantSplit/></w:trPr>%s</w:tr>' % ''.join(
            cell(c, w, False) for c, w in zip(row, widths))
    return out + '</w:tbl><w:p/>'


# --------------------------------------------------------------------------------------------------------------
# Locating top-level elements by anchor text
# --------------------------------------------------------------------------------------------------------------
path = SRC + '/word/document.xml'
doc = open(path, encoding='utf-8').read()


def find_once(needle):
    count = doc.count(needle)
    if count != 1:
        raise SystemExit('anchor %r found %d times' % (needle, count))
    return doc.index(needle)


def para_bounds(anchor):
    i = find_once(anchor)
    start = max(doc.rfind('<w:p>', 0, i), doc.rfind('<w:p ', 0, i))
    end = doc.index('</w:p>', i) + len('</w:p>')
    return start, end


def table_bounds(anchor):
    i = find_once(anchor)
    start = doc.rfind('<w:tbl>', 0, i)
    end = doc.index('</w:tbl>', i) + len('</w:tbl>')
    return start, end


def replace_text(old, new, expected=1):
    global doc
    if doc.count(old) != expected:
        raise SystemExit('text %r found %d times, expected %d' % (old, doc.count(old), expected))
    doc = doc.replace(old, new)


def insert_before_para(anchor, xml):
    global doc
    s, _ = para_bounds(anchor)
    doc = doc[:s] + xml + doc[s:]


def insert_after_para(anchor, xml):
    global doc
    _, e = para_bounds(anchor)
    doc = doc[:e] + xml + doc[e:]


def replace_range(start_anchor, end_anchor, xml):
    """Replaces everything from the paragraph containing start_anchor up to (not including) the paragraph
    containing end_anchor."""
    global doc
    s, _ = para_bounds(start_anchor)
    e, _ = para_bounds(end_anchor)
    doc = doc[:s] + xml + doc[e:]


def replace_para(anchor, xml):
    global doc
    s, e = para_bounds(anchor)
    doc = doc[:s] + xml + doc[e:]


def add_table_rows(table_anchor, rows, template_row_anchor=None):
    """Appends rows to a table by cloning an existing row (one text run per cell)."""
    global doc
    s, e = table_bounds(table_anchor)
    tbl = doc[s:e]
    tr_list = re.findall(r'<w:tr>.*?</w:tr>', tbl, re.S)
    templates = [tr for tr in tr_list[1:] if template_row_anchor is None or template_row_anchor in tr] or tr_list[-1:]
    new = ''
    for n, values in enumerate(rows):
        tpl = templates[n % len(templates)] if template_row_anchor is None else templates[0]
        texts = re.findall(r'<w:t(?: [^>]*)?>[^<]*</w:t>', tpl)
        if len(texts) != len(values):
            raise SystemExit('template row has %d texts, row has %d values' % (len(texts), len(values)))
        out, pos = '', 0
        for old, value in zip(texts, values):
            k = tpl.index(old, pos)
            out += tpl[pos:k] + t(value)
            pos = k + len(old)
        new += out + tpl[pos:]
    tbl = tbl[:-len('</w:tbl>')] + new + '</w:tbl>'
    doc = doc[:s] + tbl + doc[e:]


VERSION = '1.5.2'

# --------------------------------------------------------------------------------------------------------------
# Front matter and upgrade section
# --------------------------------------------------------------------------------------------------------------
replace_text('>Version 1.2.6<', '>Version %s<' % VERSION)
replace_text('patient, pharmacy, emergency trolley, and equipment handovers',
             'patient, laboratory, pharmacy, emergency trolley, and equipment handovers')
replace_text('before copying version 1.2.6.', 'before copying version %s.' % VERSION)
replace_text('>handover-1.2.6.omod<', '>handover-%s.omod<' % VERSION)
replace_text('Copy handover-1.2.6.omod into', 'Copy handover-%s.omod into' % VERSION)
replace_text('sudo cp handover-1.2.6.omod', 'sudo cp handover-%s.omod' % VERSION)
replace_text('chown tomcat9:tomcat9 /var/lib/OpenMRS/modules/handover-1.2.6.omod',
             'chown tomcat:tomcat /var/lib/OpenMRS/modules/handover-%s.omod' % VERSION)
add_table_rows('>Guide information<', [
    ('Laboratory order type', 'Global property laboratorymanagement.orderType.labOrderTypeId identifies laboratory '
                              'orders for Laboratory Handover'),
    ('Make Patient Handover drafts', 'Saved per user, location and care setting in patient_handover_draft '
                                     '(created automatically on first start of 1.5.x)'),
])
insert_after_para('Verification: open Administration', para(
    'Version 1.5 creates the patient_handover_draft table automatically during the first start. No manual SQL is '
    'required. After deploying, ask users to refresh the browser with Ctrl+F5 so the new page scripts and styles '
    'are loaded.', lead='Upgrading from 1.4 or 1.5.x:'))

# --------------------------------------------------------------------------------------------------------------
# Section 4: patient handover (rewritten for the 1.5 page)
# --------------------------------------------------------------------------------------------------------------
patient = h1('4 Patient handover')
patient += h2('Load the worklist')
patient += para('Make Patient Handover lists patients who have an active visit at the current login location or one of '
                'its departments and whose visit (admission) started in the selected period.')
patient += steps([
    'Open Make Patient Handover and confirm the login location shown under the page title.',
    'Choose the Care setting: IPD or OPD.',
    'Set Admitted from and Admitted to. Both days are included. By default the period runs from two days before '
    'today until today. If the dates are entered in the wrong order they are swapped automatically.',
    'Choose Load patients.',
])
patient += bullets([
    'Use Find patient in worklist to filter the list by name or identifier.',
    'Use + Add patient to search the whole database when a patient is not listed. Add patients one at a time to the '
    'temporary list, then choose Add patient(s) to worklist.',
    'Remove on a patient card, or Remove selected, hides patients from this worklist for your session.',
    'View all shows every patient on one page; otherwise the worklist is shown in pages.',
])
patient += h2('Fill in the handover')
patient += para('The page has two numbered steps and a bar fixed to the bottom of the screen that always shows the '
                'number of selected patients, the draft status and the Submit handover button.')
patient += steps([
    'Step 1, Shift and teams: select the shift and the destination. You are included in the sending team and signed '
    'automatically. Add other senders, and add at least one receiver, by typing a name and choosing the provider '
    'suggestion.',
    'Step 2, Patients: every patient is shown as a card with name, identifier, sex and age. Tick the box at the left of '
    'a card to include the patient, or use Select all on this page. Selected cards are highlighted.',
    'Choose the patient Status. Cards of Urgent patients get an amber edge and Critical patients a red edge.',
    'Complete Situation, Background, Assessment and Recommendation. Each box accepts up to 1000 characters.',
    'Open task(s) for the receiving team to add follow-up tasks with a due date and time. Each task is assigned to '
    'every receiving team member.',
    'Check the bottom bar, choose Submit handover and confirm. All selected patients are saved as one transaction.',
])
patient += h2('SBAR filled in from the patient’s records')
patient += para('When the worklist opens, empty SBAR boxes are filled in from information already recorded for that '
                'patient in OpenMRS forms. This saves typing; it does not replace clinical review.')
patient += table(['SBAR box', 'Filled from (default configuration)'], [
    ('Situation', 'Final diagnosis, other presumptive diagnosis, chief complaints, progress-note subjective'),
    ('Background', 'Clinical summary, history of present illness, past medical history, clinical history, known '
                   'allergies'),
    ('Assessment', 'Progress-note assessment and objective findings, investigation summary, other diagnosis, latest '
                   'vital signs (pulse, blood pressure, temperature, respiratory rate, oxygen saturation)'),
    ('Recommendation', 'Plan, treatment/action taken, identified health education needs'),
], [2400, 7248])
patient += bullets([
    'Each line shows the field, its latest recorded value and the date and time it was recorded, for example '
    '“Final diagnosis: Malaria (03 Nov 10:15)”. Always check the date: an old value may no longer be correct.',
    'Entries that contain only a placeholder such as “.” or “-” are ignored, and the most recent '
    'meaningful value is used instead.',
    'If nothing is recorded for a box, the same box from the patient’s last handover (within 30 days) is offered.',
    'Filled-in boxes have a yellow background and a REVIEW badge. Point at the badge to see where the text came from. '
    'The badge disappears as soon as you edit the text.',
    'Use records puts the text from the records back after you have changed it.',
    'When you submit, the confirmation warns if a selected patient still contains filled-in text that nobody edited.',
])
patient += para('The person who submits remains responsible for the handover content. Correct or delete any filled-in '
                'text that is out of date before submitting.', lead='Clinical responsibility:')
patient += h2('Drafts: continue where you stopped')
patient += para('Everything entered on Make Patient Handover is saved automatically as a draft: immediately in the '
                'browser and, a few seconds later, in your user account. A draft is kept for each user, login location '
                'and care setting.')
patient += bullets([
    'You may leave the page, log out or close the browser at any time. When you open Make Patient Handover again with '
    'the same location and care setting, the shift, teams, selected patients, statuses, SBAR text and tasks are '
    'restored, also on another computer.',
    ('the bar shows Saving draft to your account, Draft saved to your account at a given time, Continuing your draft '
     'from a given time, or Saved on this device only — will retry when the server cannot be reached (the browser '
     'copy is kept and saving is retried).', 'Draft status:'),
    'Text entered for patients on other pages of the worklist is kept in the draft.',
    'After a successful submission the draft is deleted automatically.',
    'Discard draft, in the bottom bar, deletes the draft from the browser and from your account and clears the form.',
])
patient += h2('Receive and manage')
patient += bullets([
    'Open Patient Saved Handovers or the patient dashboard. Pagination limits long result sets.',
    'Use View members to inspect both teams and signature progress.',
    'A member signs only their own assignment. Signature times are preserved independently, and a signature cannot be '
    'recorded twice. When several members sign at the same moment, the handover still completes correctly.',
    'Before completion, an authorized creator or manager can edit the handover, edit team members, or cancel it with '
    'a reason.',
    'The Age column uses hours below 24 hours and days after 24 hours.',
])
replace_range('>4 Patient handover<', '>5 Pharmacy handover<', patient)

# --------------------------------------------------------------------------------------------------------------
# New section 8: laboratory handover; later sections renumbered
# --------------------------------------------------------------------------------------------------------------
replace_text('>12 Version 1.2.6 workflow updates<', '>13 What’s new in version 1.5<')
replace_text('>11 Troubleshooting<', '>12 Troubleshooting<')
replace_text('>10 Verification checklist<', '>11 Verification checklist<')
replace_text('>9 Administration and technical reference<', '>10 Administration and technical reference<')
lab = h1('8 Laboratory handover')
lab += para('Laboratory Handover lists patients who have a laboratory order with a lab code (accession number) but no '
            'result yet, so that the outgoing laboratory team can hand pending work over to the next shift.')
lab += h2('Requirements')
lab += bullets([
    'Sign in at a laboratory location: a location whose name, or whose parent location’s name, contains '
    '“Laboratory” or “Lab”. Administrators can allow other locations by listing their IDs in '
    'patienthandover.laboratoryLocationIds.',
    'The global property laboratorymanagement.orderType.labOrderTypeId must contain the laboratory order type ID; '
    'otherwise the page reports that the worklist is unavailable.',
    'Creating and signing as a sender requires Patient Handover: Create; signing as a receiver requires Patient '
    'Handover: Receive.',
])
lab += h2('Make a laboratory handover')
lab += steps([
    'Open Laboratory Handover. Set the Laboratory worklist period (From and To; by default the last two days) and '
    'choose Load worklist. The worklist covers the facility of the current login location.',
    'Review Patients with a lab code and no result. Use Remove to hide a row that should not be handed over.',
    'To add a patient who is not listed, use Find and add a patient: search by patient name, identifier or lab code '
    'and choose Add to worklist. Patients whose results are already recorded cannot be added.',
    'Tick the patients to hand over and enter a comment for each where useful (up to 2000 characters).',
    'Select the shift, add the sending and receiving team members from the provider suggestions, and choose Submit '
    'selected patients.',
])
lab += para('Orders whose results were recorded after the worklist was loaded are skipped at submission. If the page '
            'reports that the selected orders are no longer available, reload the worklist.', lead='Note:')
lab += h2('Sign and complete')
lab += bullets([
    'Pending laboratory handovers are shown to their team members, the creator and managers. View patients shows the '
    'handed patients, lab codes, order dates and comments.',
    'Senders sign with Sign as sender. Receivers use Mark my receipt after every sender has signed. The handover '
    'becomes Received when all team members have signed.',
    'While the handover is pending, the creator or a manager can use Edit to correct the comments.',
])
lab += h2('Laboratory Handover Report')
lab += para('Open Laboratory Handover Report, filter by From, To, shift and status, and choose Run report. View patients '
            'expands each handover. Download CSV and Download PDF export the filtered handovers and require Patient '
            'Handover: Export Reports; each handover also has its own Download PDF action.')
insert_before_para('>8 Reports and exports<', lab)
replace_text('>8 Reports and exports<', '>9 Reports and exports<')
add_table_rows('>Report<', [
    ('Laboratory Handover Report', 'From, To, shift, status',
     'Patients, lab codes, order dates, comments, teams and progress', 'Filtered PDF, individual PDF, CSV'),
], template_row_anchor='Equipment Report')

# --------------------------------------------------------------------------------------------------------------
# Section 10: administration
# --------------------------------------------------------------------------------------------------------------
settings = h2('Global properties')
settings += para('Change these values in Administration > Settings (or Advanced Settings). Changes take effect the next '
                 'time a page is opened; no restart is required.')
settings += table(['Property', 'Default', 'Purpose'], [
    ('patienthandover.overdueHours', '2', 'Hours after which a pending patient handover is shown as overdue'),
    ('patienthandover.laboratoryLocationIds', '(empty)', 'Comma-separated location IDs allowed to use Laboratory '
                                                        'Handover in addition to locations named Laboratory or Lab'),
    ('laboratorymanagement.orderType.labOrderTypeId', '(site specific)', 'Order type of laboratory orders used by the '
                                                                        'laboratory worklist'),
    ('patienthandover.sbar.prefillEnabled', 'true', 'Fill in empty SBAR boxes from the patient’s records'),
    ('patienthandover.sbar.maxAgeDays', '0', '0 uses the latest value of any age; a number of days ignores older '
                                             'values'),
    ('patienthandover.sbar.situationConcepts', 'see section 4', 'Concepts used for Situation, in display order'),
    ('patienthandover.sbar.backgroundConcepts', 'see section 4', 'Concepts used for Background'),
    ('patienthandover.sbar.assessmentConcepts', 'see section 4', 'Concepts used for Assessment'),
    ('patienthandover.sbar.recommendationConcepts', 'see section 4', 'Concepts used for Recommendation'),
], [3900, 1500, 4248])
settings += para('Each SBAR concept list is comma-separated. An entry may be a concept UUID, a numeric concept ID or a '
                 'SOURCE:CODE mapping such as CIEL:1284. Concepts that do not exist are skipped and reported in the '
                 'OpenMRS log with the text “SBAR pre-fill”. Several defaults are numeric concept IDs taken from '
                 'the facility forms; check them when moving to another server. The setting '
                 'patienthandover.sbar.lookbackDays from version 1.5.0 is no longer used and may be deleted.')
insert_before_para('>Database and service integration<', settings)
replace_text('Version 1.2.6 adds named emergency trolley units', 'Version 1.2.6 added named emergency trolley units')
insert_after_para('Version 1.2.6 added named emergency trolley units', para(
    'Version 1.4 adds laboratory_handover and laboratory_handover_item for laboratory handovers. Version 1.5 adds '
    'patient_handover_draft, which keeps one unsubmitted Make Patient Handover form per user, location and care '
    'setting. Signing locks the team rows of the transaction so that simultaneous signatures are recorded safely.'))

# --------------------------------------------------------------------------------------------------------------
# Section 11 checklist
# --------------------------------------------------------------------------------------------------------------
replace_text('Only handover-1.2.6.omod is deployed', 'Only handover-%s.omod is deployed' % VERSION)
replace_para('Patient Select All works and the automatic list', bullet(
    'On Make Patient Handover, Select all on this page and Remove selected work, and Admitted from/to returns the '
    'active patients admitted in that period.'))
insert_after_para('On Make Patient Handover, Select all on this page', bullets([
    'For a patient with recorded notes or vital signs, SBAR boxes show text marked REVIEW with the source date.',
    'A draft started on one computer is restored on another for the same user, location and care setting, and '
    'disappears after the handover is submitted.',
    'At a laboratory location, Laboratory Handover loads pending laboratory orders and a submitted laboratory handover '
    'can be signed by both teams.',
]))

# --------------------------------------------------------------------------------------------------------------
# Section 12 troubleshooting
# --------------------------------------------------------------------------------------------------------------
replace_text('deploy 1.2.6, restart Tomcat', 'deploy %s, restart Tomcat' % VERSION)
add_table_rows('>Symptom<', [
    ('SBAR boxes stay empty', 'Nothing recorded for the configured concepts, pre-fill disabled, or concept IDs differ '
                              'on this server',
     'Check the patienthandover.sbar settings, search the OpenMRS log for “SBAR pre-fill”, and confirm that the '
     'ward’s forms record the configured concepts.'),
    ('SBAR shows an old value', 'The latest recorded value is old; by default there is no age limit',
     'Check the date in brackets and edit the text. Optionally set patienthandover.sbar.maxAgeDays.'),
    ('Draft status shows Saved on this device only', 'The server could not be reached or the session expired',
     'Keep the page open; saving is retried. If the session expired, log in again; the browser copy is kept.'),
    ('Select all, Remove selected or Use records do nothing', 'An older page script is cached by the browser',
     'Refresh with Ctrl+F5 and confirm that version 1.5.2 or later is deployed.'),
    ('Laboratory Handover is available only at a laboratory location', 'The login location is not recognised as a '
                                                                        'laboratory',
     'Log in at the laboratory location or add its ID to patienthandover.laboratoryLocationIds.'),
    ('Laboratory worklist is unavailable', 'laboratorymanagement.orderType.labOrderTypeId is not configured',
     'Set the property to the laboratory order type ID.'),
])
replace_text('Check the latest encounter and admission state, or use manual add',
             'Widen Admitted from/to, check the latest encounter and admission state, or use manual add')

# --------------------------------------------------------------------------------------------------------------
# Appendix A tables
# --------------------------------------------------------------------------------------------------------------
replace_text('following 16 tables', 'following 19 tables')
add_table_rows('>Area<', [
    ('Patient', 'patient_handover_draft', 'Unsubmitted Make Patient Handover form of one user',
     'draft_id; user_id; location_id; care_setting; draft_data (form values as JSON); date_created; date_changed; uuid'),
    ('Laboratory', 'laboratory_handover', 'Laboratory handover transaction header',
     'laboratory_handover_id; location_id; shift; status; date_submitted; date_received; received_by; audit fields'),
    ('Laboratory', 'laboratory_handover_item', 'Pending laboratory order snapshot included in one handover',
     'laboratory_handover_item_id; laboratory_handover_id; patient_id; patient_name; patient_identifier; lab_code; '
     'order_ids; ordered_on; comment'),
])

# --------------------------------------------------------------------------------------------------------------
# Section 13: what's new, with the 1.2.6 notes kept as history
# --------------------------------------------------------------------------------------------------------------
whats_new = para('This section summarises the changes since version 1.2.6. The instructions in the earlier sections '
                 'already describe the current behaviour.')
whats_new += table(['Update', 'How it works'], [
    ('Drafts in the user account', 'Make Patient Handover saves the form to the user account as well as the browser, '
                                   'restores the newer copy, and deletes it after submission. See section 4.'),
    ('SBAR from patient records', 'Empty SBAR boxes are filled in from the latest recorded observations, or from the '
                                  'last handover, and marked REVIEW until edited. See section 4.'),
    ('New patient layout', 'Patients are shown as cards with labelled SBAR boxes, status colours and a fixed bottom '
                           'bar with the draft status and Submit handover.'),
    ('Admission date range', 'Admitted from and Admitted to replace the Admitted within (days) list.'),
    ('Laboratory handover', 'Team-based handover of pending laboratory orders, with report, CSV and PDF. See section 8.'),
    ('Safer signing', 'Simultaneous signatures complete the handover correctly, a signature cannot be recorded twice, '
                      'and a trolley handover cannot be received twice.'),
    ('Input limits', 'SBAR boxes stop at 1000 characters and the server explains which patient’s text is too long.'),
], [3000, 6648])
whats_new += h2('Earlier updates in version 1.2.6')
replace_para('This section supplements the preserved instructions above.', whats_new)
replace_para('Remove every older patienthandover or handover OMOD before deploying', para(
    'Remove every older patienthandover or handover OMOD before deploying handover-%s.omod. Stop Tomcat, back up the '
    'database and module directory, deploy only one Handover OMOD, start Tomcat, and monitor the log until the module '
    'has started. Then refresh the browser with Ctrl+F5.' % VERSION))

# --------------------------------------------------------------------------------------------------------------
# Appendix B and quick start
# --------------------------------------------------------------------------------------------------------------
replace_para('The following screenshots were supplied with the version 1.2.6 guide material.', para(
    'The following screenshots were taken with version 1.2.6. From version 1.5, Make Patient Handover (Figures B3, '
    'B8, B11, B12 and B26) uses the patient cards, the Admitted from and Admitted to dates, the REVIEW markers and the '
    'bottom bar described in section 4; the steps are otherwise unchanged. Screen content varies with privileges, '
    'login location, available data and configuration.'))
s, e = para_bounds('Review quantities, patient status, SBAR and tasks before submitting.')
review = doc[s:e]
extra = review.replace('Review quantities, patient status, SBAR and tasks before submitting.',
                       'Check every SBAR box marked REVIEW: correct or delete filled-in text that is out of date.')
doc = doc[:e] + extra + doc[e:]

open(path, 'w', encoding='utf-8').write(doc)

# Footer and document properties
for name in ['/word/footer1.xml', '/docProps/core.xml', '/docProps/app.xml']:
    p = SRC + name
    content = open(p, encoding='utf-8').read()
    content = content.replace('1.2.6', VERSION)
    open(p, 'w', encoding='utf-8').write(content)
print('ok')
