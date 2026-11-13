<%
    ui.decorateWith("appui", "standardEmrPage")
    ui.includeCss("patienthandover", "patienthandover.css")
    ui.includeJavascript("patienthandover", "task-controls.js")
    ui.includeJavascript("patienthandover", "sbar-tools.js")
%>

<script type="text/javascript">
    var breadcrumbs = [
        { icon: "icon-home", link: '/' + OPENMRS_CONTEXT_PATH + '/index.htm' },
        { label: "${ ui.message("patienthandover.make.title") }" }
    ];

function syncPatientProvider(input){var options=document.getElementById("patientTeamProviders").options,id="",i;for(i=0;i<options.length;i++)if(options[i].value===input.value){id=options[i].getAttribute("data-provider-id");break;}input.nextElementSibling.value=id;input.setCustomValidity(id?"":"Select a provider from the suggestions.");}
function addPatientTeamRow(containerId,name,required){var row=document.createElement("div");row.className="ph-team-row";var visibleName=name==='outgoingProviderIds'?'outgoingProviderNames':'receivingProviderNames';row.innerHTML='<input type="text" name="'+visibleName+'" list="patientTeamProviders" placeholder="Type provider name" onchange="syncPatientProvider(this)" '+(required?'required':'')+'/><input type="hidden" name="'+name+'"/><button type="button" class="ph-btn-ghost" onclick="removePatientTeamRow(this)">Remove</button>';document.getElementById(containerId).appendChild(row);row.firstElementChild.focus();}
function removePatientTeamRow(button){var row=button.parentNode,box=row.parentNode;if(box.id==="patientReceivingTeam"&&box.children.length===1){row.querySelector("input[type=text]").value="";row.querySelector("input[type=hidden]").value="";}else box.removeChild(row);}
</script>

<div class="ph-page ph-make-page">
    ${ ui.includeFragment("patienthandover", "handoverNavigation", [ activeTab: "make" ]) }

    <%
        // Get attributes from the model (set by controller)
        def locationHandoverMessage = binding.hasVariable('locationHandoverMessage') ? locationHandoverMessage : null
        def locationHandoverError   = binding.hasVariable('locationHandoverError')   ? locationHandoverError   : null
        def worklistError           = binding.hasVariable('worklistError')           ? worklistError           : null
        def currentLocation         = binding.hasVariable('currentLocation')         ? currentLocation         : null
        def careSetting             = binding.hasVariable('careSetting')             ? careSetting             : null
        def searchQuery             = binding.hasVariable('searchQuery')             ? searchQuery             : null
        def searchResults           = binding.hasVariable('searchResults')           ? searchResults           : null
        def worklistQuery           = binding.hasVariable('worklistQuery')           ? worklistQuery           : null
        def patients                = binding.hasVariable('patients')                ? patients                : null
        def providers                = binding.hasVariable('providers')              ? providers               : null
        def manuallyAddedPatients    = binding.hasVariable('manuallyAddedPatients')  ? manuallyAddedPatients   : []
        def stagedPatients           = binding.hasVariable('stagedPatients')         ? stagedPatients          : []
        def draftStorageKey         = binding.hasVariable('draftStorageKey')         ? draftStorageKey         : null
        def draftMaxAgeHours        = binding.hasVariable('draftMaxAgeHours')        ? draftMaxAgeHours        : 12
        def canExplainSbar          = binding.hasVariable('canExplainSbar')          ? canExplainSbar          : false
        def sbarTitlesLiteral       = binding.hasVariable('sbarTitlesLiteral')       ? sbarTitlesLiteral       : 'null'
        def sbarKeptTitlesLiteral   = binding.hasVariable('sbarKeptTitlesLiteral')   ? sbarKeptTitlesLiteral   : 'null'
        def sbarSuggestions         = binding.hasVariable('sbarSuggestions')         ? sbarSuggestions         : [:]
        def viewAllPatients         = binding.hasVariable('viewAllPatients')         ? viewAllPatients         : false
        def showManualPatients      = binding.hasVariable('showManualPatients')      ? showManualPatients      : false
        def rejectedForm            = binding.hasVariable('rejectedForm')            ? rejectedForm            : [:]
        def hasRejectedForm         = rejectedForm && !rejectedForm.isEmpty()
        def rejectedValue           = { fieldName -> def values = rejectedForm ? rejectedForm[fieldName] : null; values && values.length ? values[0] : '' }
        def rejectedPatientIds      = rejectedForm && rejectedForm['patientIds'] ? rejectedForm['patientIds'].toList() : []
        def h                       = { value -> value == null ? '' : ui.escapeHtml(value.toString()) }
        def attr                    = { value -> value == null ? '' : ui.escapeAttribute(value.toString()) }
        def sbarFields              = [
            [name: 'situation',      letter: 'S', title: 'Situation',      hint: 'What is happening now'],
            [name: 'background',     letter: 'B', title: 'Background',     hint: 'Relevant history'],
            [name: 'assessment',     letter: 'A', title: 'Assessment',     hint: 'Current findings and vitals'],
            [name: 'recommendation', letter: 'R', title: 'Recommendation', hint: 'What the next shift must do']
        ]
        def statuses                = [STABLE: 'Stable', URGENT: 'Urgent', CRITICAL: 'Critical', DIED: 'Died',
                                       CURED: 'Cured / recovered', DEFAULTED: 'Defaulted / left care', TRANSFERRED: 'Transferred']
    %>

    <% if (locationHandoverMessage != null) { %>
        <div class="ph-alert ph-alert-success" role="status">${ h(locationHandoverMessage) }</div>
    <% } %>
    <% if (locationHandoverError != null) { %>
        <div class="ph-alert ph-alert-error" role="alert">${ h(locationHandoverError) }</div>
    <% } %>
    <% if (worklistError != null) { %>
        <div class="ph-alert ph-alert-error" role="alert">${ h(worklistError) }</div>
    <% } %>

    <% if (currentLocation != null) { %>
        <section class="ph-card ph-setup-card">
            <div class="ph-card-header">
                <div>
                    <h2 class="ph-card-title">Make patient handover</h2>
                    <p class="ph-card-subtitle">Signed in at <strong>${ h(currentLocation.name) }</strong></p>
                </div>
                <form method="get" class="ph-make-control-row ph-setup-form" action="${ ui.pageLink('patienthandover', 'locationHandover') }">
                    <label>Care setting
                        <select name="careSetting">
                            <option value="IPD" ${ careSetting == 'IPD' ? 'selected="selected"' : '' }>IPD</option>
                            <option value="OPD" ${ careSetting == 'OPD' ? 'selected="selected"' : '' }>OPD</option>
                        </select>
                    </label>
                    <label>Admitted from
                        <input type="date" name="admittedFrom" value="${ attr(admittedFrom) }" max="${ attr(admittedTo) }" required="required"/>
                    </label>
                    <label>Admitted to
                        <input type="date" name="admittedTo" value="${ attr(admittedTo) }" required="required"/>
                    </label>
                    <input type="submit" value="Load patients"/>
                </form>
            </div>

            <div class="ph-patient-actions">
                <details class="ph-add-patient-picker" ${ searchQuery != null || (stagedPatients != null && !stagedPatients.isEmpty()) ? 'open="open"' : '' }>
                    <summary class="button confirm">+ Add patient</summary>
                    <div class="ph-picker-panel">
                        <strong>Find one patient to add</strong>
                        <p class="ph-help-text">Search and add patients one at a time. When the temporary list is complete, add all of them to the worklist.</p>
                        <form method="get" class="ph-make-control-row ph-patient-search" action="${ ui.pageLink('patienthandover', 'locationHandover') }">
                            <input type="hidden" name="careSetting" value="${ attr(careSetting) }"/>
                            <input type="hidden" name="admittedFrom" value="${ attr(admittedFrom) }"/><input type="hidden" name="admittedTo" value="${ attr(admittedTo) }"/>
                            <input type="text" name="query" value="${ attr(searchQuery) }" placeholder="Patient name or identifier" required="required"/>
                            <button type="submit">Search patient</button>
                        </form>
                        <% if (searchQuery != null) { %>
                            <% if (searchResults == null || searchResults.isEmpty()) { %>
                                <p>No patients found for <strong>${ h(searchQuery) }</strong>.</p>
                            <% } else { %>
                                <table class="ph-table ph-compact-table">
                                    <tr><th>Name</th><th>Identifier</th><th>Gender</th><th>Action</th></tr>
                                    <% searchResults.each { result -> %>
                                        <tr><td>${ h(result.personName.fullName) }</td><td>${ h(result.patientIdentifier?.identifier) }</td><td>${ h(result.gender) }</td><td><a class="button" href="${ ui.pageLink('patienthandover', 'locationHandover', [careSetting: careSetting, admittedFrom: admittedFrom, admittedTo: admittedTo, stagePatientId: result.patientId]) }">Add</a></td></tr>
                                    <% } %>
                                </table>
                            <% } %>
                        <% } %>
                        <h4>Patients ready to add (${ stagedPatients?.size() ?: 0 })</h4>
                        <% if (stagedPatients == null || stagedPatients.isEmpty()) { %>
                            <p>No patient has been selected yet.</p>
                        <% } else { %>
                            <table class="ph-table ph-compact-table">
                                <tr><th>Patient</th><th>Identifier</th><th>Action</th></tr>
                                <% stagedPatients.each { patient -> %>
                                    <tr><td>${ h(patient.personName?.fullName) }</td><td>${ h(patient.patientIdentifier?.identifier ?: '-') }</td><td><a href="${ ui.pageLink('patienthandover', 'locationHandover', [careSetting: careSetting, admittedFrom: admittedFrom, admittedTo: admittedTo, removeStagedPatientId: patient.patientId]) }">Remove</a></td></tr>
                                <% } %>
                            </table>
                            <a class="button confirm" href="${ ui.pageLink('patienthandover', 'locationHandover', [careSetting: careSetting, admittedFrom: admittedFrom, admittedTo: admittedTo, commitStaged: true, viewAll: true]) }">Add ${ stagedPatients.size() } patient(s) to worklist</a>
                            <a class="button cancel" href="${ ui.pageLink('patienthandover', 'locationHandover', [careSetting: careSetting, admittedFrom: admittedFrom, admittedTo: admittedTo, clearStaged: true]) }">Clear temporary list</a>
                        <% } %>
                    </div>
                </details>
                <span class="ph-chip">Manually added: ${ manuallyAddedPatients?.size() ?: 0 }</span>
                <% if (manuallyAddedPatients != null && !manuallyAddedPatients.isEmpty()) { %>
                    <a class="ph-link-muted" href="${ ui.pageLink('patienthandover', 'locationHandover', [careSetting: careSetting, admittedFrom: admittedFrom, admittedTo: admittedTo, clearManual: true]) }">Clear manually added</a>
                <% } %>

                <form method="get" class="ph-worklist-search" action="${ ui.pageLink('patienthandover', 'locationHandover') }">
                    <input type="hidden" name="careSetting" value="${ attr(careSetting) }"/>
                    <input type="hidden" name="admittedFrom" value="${ attr(admittedFrom) }"/><input type="hidden" name="admittedTo" value="${ attr(admittedTo) }"/>
                    <label for="worklistPatientFilter" class="ph-visually-hidden">Find patient in this worklist</label>
                    <span class="ph-search-controls">
                        <input id="worklistPatientFilter" type="text" name="worklistQuery" value="${ attr(worklistQuery) }" placeholder="Find patient in worklist"/>
                        <button type="submit">Search</button>
                    </span>
                    <% if (worklistQuery != null && !worklistQuery.isEmpty()) { %>
                        <a class="ph-link-muted" href="${ ui.pageLink('patienthandover', 'locationHandover', [careSetting: careSetting, admittedFrom: admittedFrom, admittedTo: admittedTo]) }">Clear</a>
                    <% } %>
                </form>
            </div>
        </section>

        <% if (patients == null || patients.isEmpty()) { %>
            <div class="ph-empty-state">
                <p><strong>No active ${ h(careSetting) } patients admitted between ${ h(admittedFrom) } and ${ h(admittedTo) } were found at ${ h(currentLocation.name) } or its departments.</strong></p>
                <p>Use <em>Add patient</em> above when the patient is not displayed automatically.</p>
            </div>
        <% } else { %>
            <form id="handoverBatchForm" method="post"
                  action="${ ui.pageLink('patienthandover', 'locationHandover') }"
                  novalidate="novalidate"
                  onsubmit="return reviewBatch();">

                <input type="hidden" name="careSetting" value="${ attr(careSetting) }"/>
                <input type="hidden" name="admittedFrom" value="${ attr(admittedFrom) }"/><input type="hidden" name="admittedTo" value="${ attr(admittedTo) }"/>
                <input type="hidden" name="showManual" value="${ showManualPatients ? 'true' : 'false' }"/>
                <input type="hidden" name="viewAll" value="${ viewAllPatients ? 'true' : 'false' }"/>
                <input type="hidden" name="page" value="${ binding.hasVariable('patientCurrentPage') ? patientCurrentPage : 1 }"/>
                <input type="hidden" name="worklistQuery" value="${ attr(worklistQuery) }"/>

                <div id="handoverValidationError" class="ph-alert ph-alert-error ph-validation-summary"
                     role="alert" aria-live="assertive" tabindex="-1" style="display:none;"></div>

                <% if (hasRejectedForm) { %>
                    <div class="ph-alert ph-alert-warning">Your submitted values were restored. Correct the problem shown above and submit again.</div>
                <% } %>

                <section class="ph-card">
                    <h3 class="ph-section-title"><span class="ph-step">1</span> Shift and teams</h3>
                    <datalist id="patientTeamProviders"><% providers.each { p -> if (!p.retired) { %><option value="${ attr(p.name) }" data-provider-id="${p.providerId}"></option><% } } %></datalist>
                    <div class="ph-handover-team-row">
                        <label><strong>Shift</strong><select name="shift" required><option value="">-- Select shift --</option><option value="DAY" ${ rejectedValue('shift') == 'DAY' ? 'selected="selected"' : '' }>DAY</option><option value="NIGHT" ${ rejectedValue('shift') == 'NIGHT' ? 'selected="selected"' : '' }>NIGHT</option></select></label>
                        <label><strong>Destination</strong><select name="destinationLocationId" required><% handoverLocations.findAll{!it.retired}.each{destination->%><option value="${destination.locationId}" ${(rejectedValue('destinationLocationId')?:currentLocation?.locationId?.toString())==destination.locationId.toString()?'selected="selected"':''}>${ h(destination.name) }${destination.locationId==currentLocation?.locationId?' (same location)':''}</option><%}%></select></label>
                        <fieldset class="ph-team-builder"><legend>Sending team</legend><p class="ph-hint">You are included and signed automatically.</p><div id="patientSendingTeam"></div><button type="button" class="ph-btn-secondary" onclick="addPatientTeamRow('patientSendingTeam','outgoingProviderIds',false)">+ Add sender</button></fieldset>
                        <fieldset class="ph-team-builder"><legend>Receiving team</legend><div id="patientReceivingTeam"><div class="ph-team-row"><input type="text" name="receivingProviderNames" list="patientTeamProviders" placeholder="Type provider name" onchange="syncPatientProvider(this)" required/><input type="hidden" name="receivingProviderIds"/><button type="button" class="ph-btn-ghost" onclick="removePatientTeamRow(this)">Remove</button></div></div><button type="button" class="ph-btn-secondary" onclick="addPatientTeamRow('patientReceivingTeam','receivingProviderIds',true)">+ Add receiver</button></fieldset>
                    </div>
                </section>

                <section class="ph-card">
                    <div class="ph-section-header">
                        <h3 class="ph-section-title"><span class="ph-step">2</span> Patients <span class="ph-chip">${ binding.hasVariable('patientTotalItems') ? patientTotalItems : patients.size() } on worklist</span></h3>
                        <div class="ph-table-toolbar">
                            <label class="ph-select-all"><input type="checkbox" id="selectAllPatients" onclick="toggleAllPatients(this)"/> Select all on this page</label>
                            <span class="ph-selected-count"><strong id="selectedPatientCount">0</strong> selected</span>
                            <% if (!viewAllPatients) { %>
                                <a class="ph-btn-secondary" href="${ ui.pageLink('patienthandover', 'locationHandover', [careSetting: careSetting, admittedFrom: admittedFrom, admittedTo: admittedTo, worklistQuery: worklistQuery, viewAll: true]) }">View all</a>
                            <% } %>
                            <button type="button" class="ph-btn-ghost" onclick="removeSelectedPatients()">Remove selected</button>
                            <button type="button" class="ph-btn-secondary" onclick="cleanSelectedSbar('dates')" title="After review: remove the dates such as (19 Aug 14:06) from the SBAR text of the selected patients">Remove dates</button>
                            <button type="button" class="ph-btn-secondary" onclick="cleanSelectedSbar('titles')" title="After review: remove titles such as 'Progress plan:' from the SBAR text of the selected patients (vital signs and lab test names are kept)">Remove titles</button>
                        </div>
                    </div>
                    <p class="ph-hint ph-sbar-legend"><span class="ph-review-badge">Review</span> Text marked like this was filled in from the patient's recent records or last handover. Check and edit it before you submit.</p>

                    <div class="ph-patient-list">
                    <% patients.each { patient ->
                        def pid = patient.patientId
                        def suggestions = sbarSuggestions ? sbarSuggestions[pid] : null
                    %>
                        <div class="handover-patient-row ph-patient-card" data-patient-search="${ attr((patient.personName.fullName + ' ' + (patient.patientIdentifier?.identifier ?: '')).toLowerCase()) }">
                            <div class="ph-patient-card-head">
                                <label class="ph-patient-select" title="Include this patient in the handover">
                                    <input type="checkbox" class="patientSelection" name="patientIds" value="${ pid }" ${ rejectedPatientIds.contains(pid.toString()) ? 'checked="checked"' : '' }/>
                                </label>
                                <div class="ph-patient-identity">
                                    <strong>${ h(patient.personName.fullName) }</strong>
                                    <span>${ h(patient.patientIdentifier?.identifier ?: 'No identifier') } &middot; ${ h(patient.gender) }${ patient.age != null ? ' &middot; ' + patient.age + ' y' : '' }</span>
                                </div>
                                <label class="ph-patient-status">Status
                                    <select name="priority_${ pid }" class="ph-status-select">
                                        <% statuses.each { code, label -> %><option value="${code}" ${ rejectedValue('priority_' + pid) == code ? 'selected="selected"' : '' }>${label}</option><% } %>
                                    </select>
                                </label>
                                <button type="button" class="ph-btn-secondary ph-sbar-clear" onclick="clearSbar(${ pid })" title="Empty the four SBAR boxes so you can write your own">Clear SBAR</button>
                                <button type="button" class="ph-btn-secondary ph-sbar-restore" onclick="restoreSbar(${ pid })" title="Bring back the text as it was before Clear SBAR, Remove dates or Remove titles (after a page reload: the text from records)">Restore SBAR</button>
                                <% if (canExplainSbar) { %>
                                <button type="button" class="ph-btn-ghost ph-sbar-why" data-name="${ attr(patient.personName.fullName) }" onclick="explainSbar(${ pid }, this.getAttribute('data-name'))" title="Show which records were used for the SBAR text and why others were not">Why?</button>
                                <% } %>
                                <input type="hidden" name="sbarCleared_${ pid }" id="sbarCleared_${ pid }" value=""/>
                                <a class="ph-patient-remove" href="${ ui.pageLink('patienthandover', 'locationHandover', [careSetting: careSetting, admittedFrom: admittedFrom, admittedTo: admittedTo, removePatientId: pid]) }" title="Remove from worklist">Remove</a>
                            </div>

                            <div class="ph-sbar-inputs">
                                <% sbarFields.each { f ->
                                    def suggestion = suggestions ? suggestions[f.name] : null
                                    def fieldName = f.name + '_' + pid
                                    def value = hasRejectedForm ? rejectedValue(fieldName) : (suggestion?.text ?: '')
                                %>
                                    <div class="ph-sbar-input">
                                        <div class="ph-sbar-label">
                                            <span class="ph-sbar-letter">${ f.letter }</span>
                                            <label for="${ fieldName }">${ f.title }</label>
                                            <% if (suggestion) { %>
                                                <span class="ph-review-badge" title="${ attr(suggestion.source) }">Review</span>
                                            <% } %>
                                        </div>
                                        <textarea id="${ fieldName }" name="${ fieldName }" rows="4" maxlength="1000" placeholder="${ f.hint }"
                                            <% if (suggestion) { %>data-suggestion="${ attr(suggestion.text) }"<% } %>>${ h(value) }</textarea>
                                    </div>
                                <% } %>
                            </div>

                            <details class="ph-task-editor">
                                <summary><span id="taskCount_${ pid }">0</span> task(s) for the receiving team</summary>
                                <div id="taskList_${ pid }" class="structured-task-list">
                                    <div class="structured-task-row">
                                        <textarea name="taskDescription_${ pid }" rows="2" placeholder="Task description">${ h(rejectedValue('taskDescription_' + pid)) }</textarea>
                                        <label>Due <input type="datetime-local" name="taskDueDate_${ pid }" value="${ attr(rejectedValue('taskDueDate_' + pid)) }"/></label>
                                        <button type="button" class="remove-task-button ph-btn-ghost">Remove</button>
                                    </div>
                                </div>
                                <p class="ph-hint">Tasks are assigned automatically to every receiving team member.</p>
                                <button type="button" class="add-task-button ph-btn-secondary" data-patient-id="${ pid }">+ Add another task</button>
                            </details>
                        </div>
                    <% } %>
                    </div>
                </section>

                <div class="ph-submit-bar">
                    <span id="draftStatus" class="ph-draft-pill" data-state="idle">Entries are kept while you move between pages of this tab</span>
                    <span class="ph-submit-summary"><strong id="selectedPatientCountBar">0</strong> patient(s) selected</span>
                    <button type="button" class="ph-btn-ghost" onclick="discardHandoverDraft()">Discard draft</button>
                    <input type="submit" class="ph-btn-primary" value="Submit handover"/>
                </div>
            </form>
        <% } %>
    <% } %>

    <dialog id="sbarExplainDialog" class="ph-edit-dialog ph-sbar-explain-dialog">
        <div class="ph-dialog-header"><h3 id="sbarExplainTitle">Where the SBAR text comes from</h3><button type="button" class="ph-dialog-close" onclick="document.getElementById('sbarExplainDialog').close()">&times;</button></div>
        <div id="sbarExplainBody" class="ph-sbar-explain-body"></div>
    </dialog>

    <script type="text/javascript">
    // No backslash escapes in this block: the Groovy page template would turn them into raw
    // characters (e.g. a real line break inside a string) and break the whole script.
    var NL = String.fromCharCode(10);

    function updateTaskCount(patientId) {
        var fields = document.getElementsByName('taskDescription_' + patientId);
        var count = 0;
        for (var i = 0; i < fields.length; i++) {
            var value = fields[i].value;
            if (value != null && value.trim() != '') count++;
        }
        var label = document.getElementById('taskCount_' + patientId);
        if (label) label.innerHTML = count;
    }

    function updateAllTaskCounts() {
        var lists = document.getElementsByClassName('structured-task-list');
        for (var i = 0; i < lists.length; i++) updateTaskCount(lists[i].id.replace('taskList_', ''));
    }

    function addTaskRow(patientId) {
        var list = document.getElementById('taskList_' + patientId);
        if (!list || !list.firstElementChild) return;
        if (list.children.length === 1 && list.firstElementChild.style.display === 'none') {
            list.firstElementChild.style.display = '';
            updateTaskCount(patientId);
            scheduleHandoverDraftSave();
            return;
        }
        var row = list.firstElementChild.cloneNode(true);
        var fields = row.querySelectorAll('textarea, input, select');
        for (var i = 0; i < fields.length; i++) {
            if (fields[i].tagName.toLowerCase() === 'select') fields[i].selectedIndex = 0;
            else fields[i].value = '';
        }
        list.appendChild(row);
        scheduleHandoverDraftSave();
    }

    function toggleAllPatients(source) {
        var form = document.getElementById('handoverBatchForm');
        if (!form) return;
        var checkboxes = form.querySelectorAll('input.patientSelection[name="patientIds"]');
        for (var i = 0; i < checkboxes.length; i++) checkboxes[i].checked = source.checked;
        source.indeterminate = false;
        updateSelectAllPatients();
        scheduleHandoverDraftSave();
    }

    function updateSelectAllPatients() {
        var selectAll = document.getElementById('selectAllPatients');
        var form = document.getElementById('handoverBatchForm');
        if (!selectAll || !form) return;
        var rows = form.querySelectorAll('.handover-patient-row');
        for (var r = 0; r < rows.length; r++) {
            var box = rows[r].querySelector('.patientSelection');
            if (box && box.checked) rows[r].classList.add('is-selected'); else rows[r].classList.remove('is-selected');
        }
        var checkboxes = form.querySelectorAll('.handover-patient-row .patientSelection');
        var checked = form.querySelectorAll('.handover-patient-row .patientSelection:checked');
        selectAll.checked = checkboxes.length > 0 && checked.length === checkboxes.length;
        selectAll.indeterminate = checked.length > 0 && checked.length < checkboxes.length;
        var counters = ['selectedPatientCount', 'selectedPatientCountBar'];
        for (var c = 0; c < counters.length; c++) {
            var counter = document.getElementById(counters[c]);
            if (counter) counter.innerHTML = checked.length;
        }
    }

    function updateStatusStyles() {
        var selects = document.querySelectorAll('.ph-status-select');
        for (var i = 0; i < selects.length; i++) {
            var card = selects[i];
            while (card && !(card.classList && card.classList.contains('ph-patient-card'))) card = card.parentNode;
            if (card) card.setAttribute('data-status', selects[i].value);
        }
    }

    function showHandoverValidationErrors(errors) {
        var box = document.getElementById('handoverValidationError');
        if (!box) return;
        while (box.firstChild) box.removeChild(box.firstChild);
        if (!errors.length) {
            box.style.display = 'none';
            return;
        }
        var heading = document.createElement('strong');
        heading.appendChild(document.createTextNode('Handover cannot be submitted. Please correct:'));
        box.appendChild(heading);
        var list = document.createElement('ul');
        for (var i = 0; i < errors.length; i++) {
            var item = document.createElement('li');
            item.appendChild(document.createTextNode(errors[i]));
            list.appendChild(item);
        }
        box.appendChild(list);
        box.style.display = 'block';
        if (box.scrollIntoView) box.scrollIntoView(true);
        if (box.focus) box.focus();
    }

    function removeSelectedPatients() {
        var selected = document.querySelectorAll('.patientSelection:checked');
        if (!selected.length) {
            showHandoverValidationErrors(['Select at least one patient to remove from the worklist.']);
            return;
        }
        if (!confirm('Remove ' + selected.length + ' selected patient(s) from this worklist?')) return;
        saveHandoverDraft();
        var url = '${ ui.pageLink("patienthandover", "locationHandover") }?careSetting=${ ui.urlEncode(careSetting ?: "") }&admittedFrom=${ ui.urlEncode(admittedFrom ?: "") }&admittedTo=${ ui.urlEncode(admittedTo ?: "") }';
        for (var i = 0; i < selected.length; i++) url += '&removePatientIds=' + encodeURIComponent(selected[i].value);
        window.location.href = url;
    }

    var handoverSubmitting = false;

    function reviewBatch() {
        var form = document.getElementById('handoverBatchForm');
        var errors = [];
        if (!form) return false;

        var shift = form.elements['shift'];
        if (!shift || !shift.value) errors.push('Select a shift.');

        var receiverIds = form.querySelectorAll('input[name="receivingProviderIds"]');
        var validReceivers = 0;
        var incompleteReceiver = false;
        for (var receiverIndex = 0; receiverIndex < receiverIds.length; receiverIndex++) {
            var receiverText = receiverIds[receiverIndex].previousElementSibling;
            if (receiverIds[receiverIndex].value) validReceivers++;
            else if (receiverText && receiverText.value.trim()) incompleteReceiver = true;
        }
        if (!validReceivers) {
            errors.push(incompleteReceiver
                    ? 'Choose at least one receiving team member from the provider suggestions.'
                    : 'Add at least one receiving team member.');
        } else if (incompleteReceiver) {
            errors.push('Choose each typed receiving team member from the provider suggestions, or remove the incomplete row.');
        }

        var senderIds = form.querySelectorAll('input[name="outgoingProviderIds"]');
        for (var senderIndex = 0; senderIndex < senderIds.length; senderIndex++) {
            var senderText = senderIds[senderIndex].previousElementSibling;
            if (!senderIds[senderIndex].value && senderText && senderText.value.trim()) {
                errors.push('Choose each typed sending team member from the provider suggestions, or remove the incomplete row.');
                break;
            }
        }

        var selected = form.querySelectorAll('.patientSelection:checked');
        if (!selected.length) errors.push('Select at least one patient.');

        var critical = 0;
        var unreviewed = 0;
        var allowedStatuses = {STABLE:true, URGENT:true, CRITICAL:true, DIED:true, CURED:true, DEFAULTED:true, TRANSFERRED:true};
        var missingTaskDueDate = false;
        for (var i = 0; i < selected.length; i++) {
            var id = selected[i].value;
            var prioritySelect = document.getElementsByName('priority_' + id)[0];
            if (!prioritySelect || !allowedStatuses[prioritySelect.value]) {
                errors.push('Select a valid patient status for every selected patient.');
                break;
            }
            if (prioritySelect.value === 'CRITICAL') critical++;
            var card = document.getElementById('situation_' + id);
            while (card && !(card.classList && card.classList.contains('ph-patient-card'))) card = card.parentNode;
            if (card && card.querySelector('.ph-sbar-input.is-suggested')) unreviewed++;

            var descriptions = document.getElementsByName('taskDescription_' + id);
            var dueDates = document.getElementsByName('taskDueDate_' + id);
            for (var taskIndex = 0; taskIndex < descriptions.length; taskIndex++) {
                if (descriptions[taskIndex].value.trim() && (!dueDates[taskIndex] || !dueDates[taskIndex].value)) {
                    missingTaskDueDate = true;
                    break;
                }
            }
        }
        if (missingTaskDueDate) errors.push('Add a due date and time for every entered individual task.');

        showHandoverValidationErrors(errors);
        if (errors.length) {
            saveHandoverDraft();
            return false;
        }

        var confirmed = confirm('Submit handover' + NL + NL + 'Patients: ' + selected.length
                + NL + 'Critical: ' + critical
                + (unreviewed ? NL + NL + unreviewed + ' patient(s) still contain text filled in from records that you have not edited. Make sure it is correct.' : '')
                + NL + NL + 'Submit this handover as one transaction?');
        if (confirmed) {
            saveLocalDraft();
            // The dashboard removes the draft on success; stop autosave so it is not recreated.
            handoverSubmitting = true;
        }
        return confirmed;
    }

    /* ------------------------------------------------------------------ */
    /* SBAR suggestions                                                    */
    /* ------------------------------------------------------------------ */
    function sbarWrapper(textarea) {
        var node = textarea;
        while (node && !(node.classList && node.classList.contains('ph-sbar-input'))) node = node.parentNode;
        return node;
    }

    function refreshSuggestionMarker(textarea) {
        refreshSbarHighlight(textarea);
        var wrapper = sbarWrapper(textarea);
        var suggestion = textarea.getAttribute('data-suggestion');
        if (!wrapper || suggestion === null) return;
        var unchanged = textarea.value === suggestion && suggestion !== '';
        if (unchanged) wrapper.classList.add('is-suggested'); else wrapper.classList.remove('is-suggested');
    }

    function refreshAllSuggestionMarkers() {
        var fields = document.querySelectorAll('.ph-sbar-input textarea');
        for (var i = 0; i < fields.length; i++) refreshSuggestionMarker(fields[i]);
    }

    /* Titles of the configured SBAR concepts (chief complaints, subjective, clinical summary, ..., vital
       signs, diagnoses, plan, health education), sent by the server. */
    var sbarConfiguredTitles = (function() {
        try { return JSON.parse(${ sbarTitlesLiteral }) || []; } catch (e) { return []; }
    })();
    /* Titles that always stay: numeric concepts such as vital signs ("Pulse: 68.0"). */
    var sbarKeptTitles = (function() {
        try { return JSON.parse(${ sbarKeptTitlesLiteral }) || []; } catch (e) { return []; }
    })();

    /** Empties the four SBAR boxes of one patient so the user can write their own text. */
    function clearSbar(patientId) {
        var names = ['situation', 'background', 'assessment', 'recommendation'];
        var hasText = false;
        for (var i = 0; i < names.length; i++) {
            var box = document.getElementById(names[i] + '_' + patientId);
            if (box && box.value.trim()) hasText = true;
        }
        if (hasText && !confirm('Clear Situation, Background, Assessment and Recommendation for this patient?')) return;
        var before = {};
        for (var j = 0; j < names.length; j++) {
            var field = document.getElementById(names[j] + '_' + patientId);
            if (!field) continue;
            before[names[j]] = field.value;
            field.value = '';
            refreshSuggestionMarker(field);
        }
        sbarBeforeClear[patientId] = before;
        // Remembered in the draft so the boxes are not filled from records again when the page reloads.
        var marker = document.getElementById('sbarCleared_' + patientId);
        if (marker) marker.value = 'true';
        refreshClearedState(patientId);
        var first = document.getElementById('situation_' + patientId);
        if (first) first.focus();
        scheduleHandoverDraftSave();
    }

    var sbarBeforeClear = {};

    /** Brings back the text removed by Clear SBAR; after a page reload, the text from the patient's records. */
    function restoreSbar(patientId) {
        var names = ['situation', 'background', 'assessment', 'recommendation'];
        var before = sbarBeforeClear[patientId];
        var hasText = false;
        for (var i = 0; i < names.length; i++) {
            var box = document.getElementById(names[i] + '_' + patientId);
            if (box && box.value.trim()) hasText = true;
        }
        if (hasText && !confirm('Replace the current SBAR text of this patient with the original text?')) return;
        for (var j = 0; j < names.length; j++) {
            var field = document.getElementById(names[j] + '_' + patientId);
            if (!field) continue;
            field.value = before ? (before[names[j]] || '') : (field.getAttribute('data-suggestion') || '');
            refreshSuggestionMarker(field);
        }
        delete sbarBeforeClear[patientId];
        var marker = document.getElementById('sbarCleared_' + patientId);
        if (marker) marker.value = '';
        refreshClearedState(patientId);
        var cleanedCard = marker;
        while (cleanedCard && !(cleanedCard.classList && cleanedCard.classList.contains('ph-patient-card'))) cleanedCard = cleanedCard.parentNode;
        if (cleanedCard) cleanedCard.classList.remove('is-sbar-cleaned');
        scheduleHandoverDraftSave();
    }

    /** Shows Restore SBAR only on patients whose SBAR was cleared. */
    function refreshClearedState(patientId) {
        var marker = document.getElementById('sbarCleared_' + patientId);
        var card = marker;
        while (card && !(card.classList && card.classList.contains('ph-patient-card'))) card = card.parentNode;
        if (!card) return;
        if (marker.value === 'true') card.classList.add('is-sbar-cleared'); else card.classList.remove('is-sbar-cleared');
    }

    function refreshAllClearedStates() {
        var markers = document.querySelectorAll('input[id^="sbarCleared_"]');
        for (var i = 0; i < markers.length; i++) refreshClearedState(markers[i].id.substring('sbarCleared_'.length));
    }

    /** Opens a dialog listing which records were used for the patient's SBAR text and why others were not. */
    function explainSbar(patientId, patientName) {
        var dialog = document.getElementById('sbarExplainDialog');
        var body = document.getElementById('sbarExplainBody');
        var title = document.getElementById('sbarExplainTitle');
        title.innerHTML = '';
        title.appendChild(document.createTextNode('Where the SBAR text comes from: ' + patientName));
        body.innerHTML = '';
        body.appendChild(document.createTextNode('Loading...'));
        if (dialog.showModal) dialog.showModal();
        var xhr = new XMLHttpRequest();
        xhr.open('POST', sbarExplainUrl, true);
        xhr.setRequestHeader('Content-Type', 'application/x-www-form-urlencoded;charset=UTF-8');
        xhr.onreadystatechange = function() {
            if (xhr.readyState !== 4) return;
            body.innerHTML = '';
            var lines = null;
            try { lines = JSON.parse(xhr.responseText).lines; } catch (e) {}
            if (!lines) {
                body.appendChild(document.createTextNode('The explanation could not be loaded.'));
                return;
            }
            var list = null;
            for (var i = 0; i < lines.length; i++) {
                var line = lines[i];
                if (line.indexOf('# ') === 0) {
                    var heading = document.createElement('h4');
                    heading.appendChild(document.createTextNode(line.substring(2)));
                    body.appendChild(heading);
                    list = null;
                    continue;
                }
                if (!list) { list = document.createElement('ul'); body.appendChild(list); }
                var item = document.createElement('li');
                if (line.indexOf('NOT USED') >= 0 || line.indexOf('NOT FOUND') >= 0) item.className = 'is-excluded';
                else if (line.indexOf('-> used (') >= 0) item.className = 'is-used';
                item.appendChild(document.createTextNode(line));
                list.appendChild(item);
            }
        };
        xhr.send('patientId=' + encodeURIComponent(patientId));
    }

    /* ------------------------------------------------------------------ */
    /* Draft: kept only in this browser tab (sessionStorage) while the     */
    /* user moves between worklist pages, so entries on another page are   */
    /* not lost. It is gone after a page refresh, in a new tab or another  */
    /* browser, on submit or discard, and after draftMaxAgeHours. It holds */
    /* only what the user entered: SBAR text still equal to the suggestion */
    /* is recalculated from records.                                       */
    /* ------------------------------------------------------------------ */
    var DRAFT_VERSION = 2;
    var draftMaxAgeMillis = ${ draftMaxAgeHours ?: 12 } * 3600000;
    var handoverDraftKey = '${ draftStorageKey ? ui.escapeJs(draftStorageKey) : "" }';
    var sbarExplainUrl = '${ ui.actionLink("patienthandover", "sbarCheck", "explain") }';
    var draftBase = [];            // fields of the restored draft, kept for patients on other pages
    var handoverDraftTimer = null;
    var unchangedSuggestionNames = {};

    function getDraftForm() { return document.getElementById('handoverBatchForm'); }

    function draftStore() {
        try { return window.sessionStorage || null; } catch (e) { return null; }
    }

    function setDraftStatus(state, text) {
        var status = document.getElementById('draftStatus');
        if (!status) return;
        status.setAttribute('data-state', state);
        status.innerHTML = '';
        status.appendChild(document.createTextNode(text));
    }

    /** True when this page load is a refresh (F5 / reload button). */
    function isPageReload() {
        try {
            var entries = window.performance && performance.getEntriesByType ? performance.getEntriesByType('navigation') : [];
            if (entries && entries.length) return entries[0].type === 'reload';
            return !!(window.performance && performance.navigation && performance.navigation.type === 1);
        } catch (e) {
            return false;
        }
    }

    function collectDraftFields() {
        var form = getDraftForm();
        var fields = [];
        if (!form) return fields;
        var controls = form.querySelectorAll('input[name], select[name], textarea[name]');
        unchangedSuggestionNames = {};
        for (var i = 0; i < controls.length; i++) {
            var control = controls[i];
            if (control.type === 'submit' || control.type === 'button') continue;
            if (control.hasAttribute('data-suggestion') && control.value === control.getAttribute('data-suggestion')) {
                unchangedSuggestionNames[control.name] = true; // pre-filled and not edited: not part of the draft
                continue;
            }
            fields.push({ name: control.name, value: control.value, type: control.type, checked: control.checked });
        }
        return fields;
    }

    /** Current form values plus values of patients that are not rendered on this page. */
    function buildDraft() {
        var currentFields = collectDraftFields();
        var currentNames = {};
        for (var i = 0; i < currentFields.length; i++) currentNames[currentFields[i].name] = true;
        for (var name in unchangedSuggestionNames) currentNames[name] = true;
        var merged = [];
        for (var j = 0; j < draftBase.length; j++) if (!currentNames[draftBase[j].name]) merged.push(draftBase[j]);
        for (var k = 0; k < currentFields.length; k++) merged.push(currentFields[k]);
        return { v: DRAFT_VERSION, savedAt: new Date().getTime(), fields: merged };
    }

    function saveLocalDraft() {
        if (!getDraftForm()) return null;
        var draft = buildDraft();
        draftBase = draft.fields;
        var store = draftStore();
        try { if (handoverDraftKey && store) store.setItem(handoverDraftKey, JSON.stringify(draft)); } catch (e) {}
        return draft;
    }

    function saveHandoverDraft() {
        if (handoverSubmitting) return;
        saveLocalDraft();
    }

    function scheduleHandoverDraftSave() {
        if (handoverDraftTimer) window.clearTimeout(handoverDraftTimer);
        handoverDraftTimer = window.setTimeout(saveHandoverDraft, 400);
    }

    function removeHandoverDraft() {
        var store = draftStore();
        try { if (store && handoverDraftKey) store.removeItem(handoverDraftKey); } catch (e) {}
        // Drafts of earlier versions were kept in localStorage; remove them as well.
        try { if (window.localStorage && handoverDraftKey) localStorage.removeItem(handoverDraftKey); } catch (e) {}
    }

    function parseDraft(raw) {
        if (!raw) return null;
        try {
            var draft = JSON.parse(raw);
            if (!draft || !draft.fields || draft.fields.length === undefined) return null;
            if (draft.v !== DRAFT_VERSION) return null;
            if (!draft.savedAt || new Date().getTime() - draft.savedAt > draftMaxAgeMillis) return null;
            return draft;
        } catch (e) {
            return null;
        }
    }

    function applyDraft(draft) {
        var requiredTaskRows = {};
        var requiredSenderRows = 0;
        var requiredReceiverRows = 0;
        for (var teamIndex = 0; teamIndex < draft.fields.length; teamIndex++) {
            if (draft.fields[teamIndex].name === 'outgoingProviderIds') requiredSenderRows++;
            if (draft.fields[teamIndex].name === 'receivingProviderIds') requiredReceiverRows++;
        }
        while (document.getElementsByName('outgoingProviderIds').length < requiredSenderRows)
            addPatientTeamRow('patientSendingTeam', 'outgoingProviderIds', false);
        while (document.getElementsByName('receivingProviderIds').length < requiredReceiverRows)
            addPatientTeamRow('patientReceivingTeam', 'receivingProviderIds', true);
        for (var countIndex = 0; countIndex < draft.fields.length; countIndex++) {
            var fieldName = draft.fields[countIndex].name;
            if (fieldName.indexOf('taskDescription_') === 0) {
                var taskPatientId = fieldName.substring('taskDescription_'.length);
                if (taskPatientId) requiredTaskRows[taskPatientId] = (requiredTaskRows[taskPatientId] || 0) + 1;
            }
        }
        for (var patientId in requiredTaskRows) {
            if (!document.getElementById('taskList_' + patientId)) continue;
            while (document.getElementsByName('taskDescription_' + patientId).length < requiredTaskRows[patientId])
                addTaskRow(patientId);
        }
        var cleared = {};
        for (var c = 0; c < draft.fields.length; c++) {
            var marker = draft.fields[c];
            if (marker.name.indexOf('sbarCleared_') === 0 && marker.value === 'true')
                cleared[marker.name.substring('sbarCleared_'.length)] = true;
        }
        var occurrences = {};
        for (var i = 0; i < draft.fields.length; i++) {
            var saved = draft.fields[i];
            var controls = document.getElementsByName(saved.name);
            var occurrence = occurrences[saved.name] || 0;
            occurrences[saved.name] = occurrence + 1;
            if (saved.type === 'checkbox' || saved.type === 'radio') {
                for (var j = 0; j < controls.length; j++)
                    if (controls[j].value === saved.value) controls[j].checked = saved.checked;
            } else if (controls.length > occurrence) {
                var control = controls[occurrence];
                // An empty saved value must not wipe text filled in from the patient's records
                // (older drafts saved every SBAR box empty).
                var owner = saved.name.substring(saved.name.indexOf('_') + 1);
                if (saved.value === '' && control.hasAttribute && control.hasAttribute('data-suggestion') && !cleared[owner]) continue;
                control.value = saved.value;
            }
        }
        draftBase = draft.fields;
    }

    /** Restores this tab's draft, unless the page was refreshed. */
    function restoreHandoverDraft() {
        if (!getDraftForm()) return;
        if (isPageReload()) { removeHandoverDraft(); return; }
        var store = draftStore();
        var draft = null;
        try { draft = store && handoverDraftKey ? parseDraft(store.getItem(handoverDraftKey)) : null; } catch (e) {}
        try { if (window.localStorage && handoverDraftKey) localStorage.removeItem(handoverDraftKey); } catch (e) {}
        if (!draft) { removeHandoverDraft(); return; }
        try {
            applyDraft(draft);
            setDraftStatus('restored', 'Your entries on other pages are kept until you submit, refresh or close this tab');
        } catch (e) {
            removeHandoverDraft();
        }
    }

    function discardHandoverDraft() {
        if (!confirm('Discard all entered handover details and selections?')) return;
        removeHandoverDraft();
        draftBase = [];
        var form = getDraftForm();
        if (form) form.reset();
        sbarBeforeClear = {};
        refreshAllSuggestionMarkers();
        refreshAllClearedStates();
        updateSelectAllPatients();
        updateStatusStyles();
        updateAllTaskCounts();
        setDraftStatus('idle', 'Draft discarded');
    }

    (function initialiseHandoverDraft() {
        var form = getDraftForm();
        if (!form) return;
        restoreHandoverDraft();
        refreshAllSuggestionMarkers();
        refreshAllClearedStates();
        updateAllTaskCounts();
        updateSelectAllPatients();
        updateStatusStyles();
        form.addEventListener('submit', saveLocalDraft, true);
        form.addEventListener('invalid', saveHandoverDraft, true);
        form.addEventListener('input', function(event) {
            if (event.target && event.target.tagName === 'TEXTAREA' && sbarWrapper(event.target))
                refreshSuggestionMarker(event.target);
            updateAllTaskCounts();
            scheduleHandoverDraftSave();
        });
        form.addEventListener('change', function(event) {
            if (event.target && event.target.classList) {
                if (event.target.classList.contains('patientSelection')) updateSelectAllPatients();
                if (event.target.classList.contains('ph-status-select')) updateStatusStyles();
            }
            scheduleHandoverDraftSave();
        });
        // Saved when leaving through a page link; a refresh then discards it on load.
        window.addEventListener('pagehide', function() { if (!handoverSubmitting) saveLocalDraft(); });
    })();
    </script>
</div>
<% if (viewAllPatients) { %>
<div class="ph-page"><a class="ph-page-link" href="${ ui.pageLink('patienthandover', 'locationHandover', [careSetting: careSetting, admittedFrom: admittedFrom, admittedTo: admittedTo, worklistQuery: worklistQuery]) }">Show paginated patients</a></div>
<% } else { %>
<div class="ph-page">
${ ui.includeFragment("patienthandover", "pagination", [
 pageName: "locationHandover",
 currentPage: binding.hasVariable('patientCurrentPage') ? patientCurrentPage : 1,
 totalPages: binding.hasVariable('patientTotalPages') ? patientTotalPages : 1,
 totalItems: binding.hasVariable('patientTotalItems') ? patientTotalItems : 0,
 itemLabel: 'patient',
 parameters: [careSetting: careSetting, admittedFrom: admittedFrom, admittedTo: admittedTo, worklistQuery: worklistQuery]
]) }
</div>
<% } %>
