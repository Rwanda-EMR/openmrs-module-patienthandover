from pathlib import Path

from docx import Document
from docx.enum.text import WD_ALIGN_PARAGRAPH
from docx.oxml import OxmlElement
from docx.oxml.ns import qn
from docx.shared import Inches, Pt, RGBColor


SOURCE = Path(r"C:\Users\user\Documents\PROJECTS\OpenMRS_Projects\patienthandover\Handover_Module_User_Guide_1.1.5.docx")
IMAGE_DIR = Path(r"C:\Users\user\AppData\Local\Temp\handover_guide_images")
OUTPUT = Path(r"C:\Users\user\Documents\PROJECTS\OpenMRS_Projects\patienthandover\Handover_Module_User_Guide_1.2.6_Updated.docx")

FIGURES = [
    ("OpenMRS home page and Handover entry point", "Confirm the login location shown in the upper-right corner before opening the module. The location determines which patients, trolley catalogues, equipment and pending assignments are shown. Select the Handover application tile to begin."),
    ("Patient Handover Dashboard", "Use the dashboard to review handover activity for the current location. Filter by date range, care setting, shift and status. The summary boxes distinguish pending, completed and cancelled work so a supervisor can quickly identify incomplete transactions."),
    ("Opening Make Patient Handover", "Select IPD or OPD and choose Load Patients. The automatic worklist contains eligible active patients for the current login location. IPD considers recent qualifying activity; manual addition remains available when an appropriate patient is not listed."),
    ("Opening the Add patient panel", "Choose Add patient to open database search. Search by a patient name or identifier. This search does not immediately alter the worklist; it is used to find one patient for the temporary selection list."),
    ("Searching for one patient", "Enter enough of the name or identifier to identify the correct record, then choose Search patient. Verify both the patient name and identifier before selecting Add, especially where patients have similar names."),
    ("Adding a search result to the temporary list", "Choose Add beside the correct search result. The patient is held in the temporary list. Search again to find another patient; repeat the same process until every required patient has been collected."),
    ("Completing the temporary patient list", "Review Patients ready to add. Remove an incorrect entry if necessary. When the list is complete, choose Add patients to worklist once. View manually added patients then displays these records in the normal handover table."),
    ("Patient worklist and handover controls", "Select the shift, build the sending and receiving teams, and select the patients to hand over. View All is useful when selecting across the entire eligible list. Remove selected from worklist hides records that should not be included in the current work context."),
    ("Adding sending and receiving team members", "The authenticated creator is automatically included in the sending team and signs at submission. Use Add sender or Add receiver, type a provider name, and choose a suggestion from the list. At least one valid receiving provider is required."),
    ("Selecting a provider from suggestions", "A typed name is not sufficient by itself. Choose the matching provider suggestion so the provider ID is recorded. If a name remains unresolved, select the correct suggestion or remove that incomplete team row before submission."),
    ("Completing patient status and SBAR", "For each selected patient, choose the clinical status and record Situation, Background, Assessment and Recommendation as appropriate. SBAR is not mandatory, but meaningful entries improve continuity and should be concise, current and clinically relevant."),
    ("Adding individual follow-up tasks", "Use Add/View tasks for patient-specific actions. Enter a clear task and due date/time. Each task is assigned to every active receiving team member, allowing the receiving team to see and complete follow-up responsibilities."),
    ("Monitoring a pending patient handover", "Pending rows show transaction details and team progress. A handover remains pending until required senders and receivers have signed. Use View members to determine exactly who has signed and who remains."),
    ("Viewing team signature progress", "The members window separates Sending Team and Receiving Team. Signed/total counts provide a quick summary, while each member row shows signature state and time. Close the window after review."),
    ("Patient Saved Handovers", "Use filters to retrieve saved patient transactions. View opens clinical details and SBAR, View members opens team progress, and report actions export the filtered range or an individual transaction according to the selected action."),
    ("Emergency trolley type and item administration", "Create named trolley types for locations that operate more than one trolley. Select the trolley before maintaining its catalogue. Add items manually or use the supplied import template."),
    ("Maintaining trolley quantities and minimum levels", "Each trolley item records starting/current quantity and minimum quantity. Edit minimum quantity when stock policy changes. A LOW STOCK warning appears when current quantity is below the configured minimum; existing items remain available unless explicitly retired."),
    ("Adding or editing a trolley type", "Provide a meaningful trolley name, a unique code and an optional description. The code helps distinguish similar units in reports. After saving, select that trolley before adding its item catalogue."),
    ("Making an emergency trolley handover", "Choose the trolley type and shift, build both teams, and review every permanent trolley item. Enter handed quantity and a useful note where required. Submit only after comparing the physical trolley with the displayed catalogue."),
    ("Pending emergency trolley transaction", "The pending section identifies trolley type, shift, items and team progress. Assigned members use View members and the available signing action. Managers or the creator can manage the handover before completion where privileges permit."),
    ("Editing trolley teams or transaction details", "The management dialog separates sending and receiving teams and records a reason for change. Preserve already valid signers, remove only incorrect assignments and add replacement providers carefully because team changes affect completion status."),
    ("Equipment catalogue administration", "Add equipment name, quantity, status and observation manually, or download and populate the CSV template. Upload validates the rows before creating catalogue entries. Use observations for condition details that are not represented by the status value."),
    ("Making an equipment handover", "Select the shift and teams, then select individual equipment or Select All. Enter the handed quantity for each selected line; it must be greater than zero and cannot exceed available quantity. Submit selected items or all catalogue items as appropriate."),
    ("Pending equipment handover", "The pending transaction row displays reference, date, shift, item count and team progress. View items verifies the handed snapshot. Assigned members sign individually; the transaction is complete only when required signatures are present."),
    ("Selecting a pharmacy and reporting period", "On Pharmacy Handover, choose From and To dates when reviewing activity, then select the pharmacy location and Load Items. Available stock is read from the Rwanda Pharmacy Management inventory service for the selected pharmacy."),
    ("Using patient status values", "Choose the status that represents the patient at handover: STABLE, URGENT, CRITICAL, DIED, CURED/RECOVERED, DEFAULTED/LEFT CARE or TRANSFERRED. The chosen status is retained in transaction details and summarized in patient reports."),
    ("Emergency trolley PDF report", "The PDF identifies the facility, trolley type, location, shift, status, submission/completion times, both teams and the handed item snapshot. Use it for an auditable printable record, subject to facility confidentiality and retention policy."),
    ("Pharmacy PDF report", "The pharmacy report records facility identity, pharmacy, shift, status, sending and receiving signatures, and handed stock details including item, code, batch, expiry date and handed quantity."),
    ("Patient summarized PDF report", "The summarized patient report keeps each handover separate and shows facility identity, location, care setting/shift, status, timestamps, both teams and counts by patient status. Use the detailed export when SBAR-level information is required."),
    ("Detailed pharmacy handover export", "A single-handover export shows the exact stock snapshot and team signature times for that transaction. It is different from a filtered multi-handover report, in which every handover begins on a new page."),
]

def replace_text(container, old, new):
    for paragraph in container.paragraphs:
        if old in paragraph.text:
            for run in paragraph.runs:
                run.text = run.text.replace(old, new)
            if old in paragraph.text:
                paragraph.text = paragraph.text.replace(old, new)
    for table in container.tables:
        for row in table.rows:
            for cell in row.cells:
                replace_text(cell, old, new)

def shade_cell(cell, fill):
    tc_pr = cell._tc.get_or_add_tcPr()
    shd = OxmlElement("w:shd")
    shd.set(qn("w:fill"), fill)
    tc_pr.append(shd)

doc = Document(str(SOURCE))
replace_text(doc, "1.1.5", "1.2.6")
for section in doc.sections:
    replace_text(section.header, "1.1.5", "1.2.6")
    replace_text(section.footer, "1.1.5", "1.2.6")

doc.add_page_break()
doc.add_heading("12 Version 1.2.6 workflow updates", level=1)
doc.add_paragraph("This section supplements the preserved instructions above. Version 1.2.6 strengthens patient handover data preservation, manual patient selection, patient status reporting and validation feedback. Existing patient, pharmacy, emergency trolley and equipment workflows remain available.")
updates = [
    ("Patient eligibility", "The automatic IPD worklist uses active, admitted patients with a qualifying encounter in the previous two days. OPD uses current-day activity. A clinically appropriate patient absent from the automatic list may be added manually without this automatic-list condition."),
    ("One-by-one manual addition", "Search for one patient, choose Add, then repeat for each additional patient. The temporary list is committed only when Add patients to worklist is selected."),
    ("Manual-only worklist", "View manually added patients displays those patients in the main handover table, where they can be selected and given status, SBAR and tasks like automatically listed patients."),
    ("Validation recovery", "Before server validation, patient selections, statuses, SBAR and task values are saved in the user session. If a required value is missing, the same worklist context and entered values are restored."),
    ("Patient statuses", "Available values are STABLE, URGENT, CRITICAL, DIED, CURED/RECOVERED, DEFAULTED/LEFT CARE and TRANSFERRED. Reports summarize these statuses per handover."),
    ("Draft behavior", "The browser draft protects work in progress. Server-backed rejected-form recovery adds protection after validation redirect. Successful submission clears rejected-form recovery."),
]
table = doc.add_table(rows=1, cols=2)
table.style = "Table Grid"
table.rows[0].cells[0].text = "Update"
table.rows[0].cells[1].text = "How it works"
for cell in table.rows[0].cells:
    shade_cell(cell, "00665F")
    for run in cell.paragraphs[0].runs:
        run.font.color.rgb = RGBColor(255, 255, 255)
        run.bold = True
for title, explanation in updates:
    cells = table.add_row().cells
    cells[0].text = title
    cells[1].text = explanation

doc.add_heading("Upgrade note", level=2)
doc.add_paragraph("Remove every older patienthandover or handover OMOD before deploying handover-1.2.6.omod. Stop Tomcat, back up the database and module directory, deploy only one Handover OMOD, start Tomcat, and monitor catalina.out until Liquibase and the OpenMRS application context finish without errors.")

doc.add_page_break()
doc.add_heading("Appendix B Illustrated module workflows", level=1)
doc.add_paragraph("The following screenshots were supplied with the version 1.2.6 guide material. Screen content varies with privileges, login location, available data and configuration. Follow the explanation below each image because responsive layouts can move controls on smaller screens.")
for index, (title, explanation) in enumerate(FIGURES, start=1):
    doc.add_heading(f"Figure B{index}. {title}", level=2)
    image_path = IMAGE_DIR / f"image{index}.png"
    if not image_path.exists():
        raise FileNotFoundError(image_path)
    paragraph = doc.add_paragraph()
    paragraph.alignment = WD_ALIGN_PARAGRAPH.CENTER
    paragraph.add_run().add_picture(str(image_path), width=Inches(6.35))
    caption = doc.add_paragraph()
    caption.alignment = WD_ALIGN_PARAGRAPH.CENTER
    run = caption.add_run(f"Figure B{index}: {title}")
    run.bold = True
    run.italic = True
    doc.add_paragraph(explanation)
    if index % 2 == 0 and index != len(FIGURES):
        doc.add_page_break()

doc.add_heading("Quick start for a new user", level=1)
for text in [
    "Confirm the login location before opening Handover.",
    "Open the required handover type and select the shift.",
    "Select the patients or items that are physically and clinically being handed over.",
    "Add all outgoing and receiving providers by choosing names from provider suggestions.",
    "Review quantities, patient status, SBAR and tasks before submitting.",
    "Use View members to monitor signatures; do not share another member's account or sign on their behalf.",
    "Use the relevant report page and filters for operational review, then export PDF or CSV only when authorized.",
]:
    doc.add_paragraph(text, style="List Number")

doc.styles["Normal"].font.name = "Arial"
doc.styles["Normal"].font.size = Pt(10)
doc.core_properties.title = "OpenMRS Handover Module User Guide"
doc.core_properties.subject = "Version 1.2.6 installation, administration and illustrated user workflows"
doc.core_properties.comments = "Updated from version 1.1.5 without deleting the original guide content; screenshots incorporated from the supplied version 1.2.6 image document."
doc.save(str(OUTPUT))
print(OUTPUT)
