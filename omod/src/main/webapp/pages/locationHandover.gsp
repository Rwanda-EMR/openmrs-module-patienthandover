<%
    ui.decorateWith("appui", "standardEmrPage")
    ui.includeCss("patienthandover", "patienthandover.css")
    ui.includeJavascript("patienthandover", "task-controls.js")
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
        def serverDraftLiteral      = binding.hasVariable('serverDraftLiteral')      ? serverDraftLiteral      : 'null'
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
                                                <button type="button" class="ph-sbar-reload" onclick="reloadSuggestion('${ fieldName }')" title="${ attr('Insert again: ' + suggestion.source) }">Use records</button>
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
                    <span id="draftStatus" class="ph-draft-pill" data-state="idle">Draft: changes are saved automatically</span>
                    <span class="ph-submit-summary"><strong id="selectedPatientCountBar">0</strong> patient(s) selected</span>
                    <button type="button" class="ph-btn-ghost" onclick="discardHandoverDraft()">Discard draft</button>
                    <input type="submit" class="ph-btn-primary" value="Submit handover"/>
                </div>
            </form>
        <% } %>
    <% } %>

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
            // The server deletes the draft on success; stop autosave so a late request cannot recreate it.
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
        var wrapper = sbarWrapper(textarea);
        var suggestion = textarea.getAttribute('data-suggestion');
        if (!wrapper || suggestion === null) return;
        var unchanged = textarea.value === suggestion && suggestion !== '';
        if (unchanged) wrapper.classList.add('is-suggested'); else wrapper.classList.remove('is-suggested');
        if (textarea.value !== suggestion) wrapper.classList.add('can-reload'); else wrapper.classList.remove('can-reload');
    }

    function refreshAllSuggestionMarkers() {
        var fields = document.querySelectorAll('textarea[data-suggestion]');
        for (var i = 0; i < fields.length; i++) refreshSuggestionMarker(fields[i]);
    }

    function reloadSuggestion(fieldName) {
        var textarea = document.getElementById(fieldName);
        if (!textarea) return;
        var suggestion = textarea.getAttribute('data-suggestion') || '';
        if (textarea.value.trim() && textarea.value !== suggestion
                && !confirm("Replace the current text with the text from the patient's records?")) return;
        textarea.value = suggestion;
        refreshSuggestionMarker(textarea);
        textarea.focus();
        scheduleHandoverDraftSave();
    }

    /* ------------------------------------------------------------------ */
    /* Draft: saved instantly in this browser and, a few seconds later, to */
    /* the user's account so it can be continued anywhere.                 */
    /* ------------------------------------------------------------------ */
    var handoverDraftKey = '${ draftStorageKey ? ui.escapeJs(draftStorageKey) : "" }';
    var serverDraftRaw = ${ serverDraftLiteral };
    var draftSaveUrl = '${ ui.actionLink("patienthandover", "handoverDraft", "save") }';
    var draftDiscardUrl = '${ ui.actionLink("patienthandover", "handoverDraft", "discard") }';
    var draftLocationId = '${ currentLocation?.locationId ?: "" }';
    var draftCareSetting = '${ ui.escapeJs(careSetting ?: "") }';
    var draftBase = [];            // fields of the restored draft, kept for patients on other pages
    var handoverDraftTimer = null;
    var serverDraftTimer = null;
    var lastServerFields = null;   // JSON of the fields last accepted by the server
    var serverSaveInFlight = false;

    function getDraftForm() { return document.getElementById('handoverBatchForm'); }

    function setDraftStatus(state, text) {
        var status = document.getElementById('draftStatus');
        if (!status) return;
        status.setAttribute('data-state', state);
        status.innerHTML = '';
        status.appendChild(document.createTextNode(text));
    }

    function timeNow() {
        return new Date().toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' });
    }

    function collectDraftFields() {
        var form = getDraftForm();
        var fields = [];
        if (!form) return fields;
        var controls = form.querySelectorAll('input[name], select[name], textarea[name]');
        for (var i = 0; i < controls.length; i++) {
            var control = controls[i];
            if (control.type === 'submit' || control.type === 'button') continue;
            fields.push({ name: control.name, value: control.value, type: control.type, checked: control.checked });
        }
        return fields;
    }

    /** Current form values plus values of patients that are not rendered on this page. */
    function buildDraft() {
        var currentFields = collectDraftFields();
        var currentNames = {};
        for (var i = 0; i < currentFields.length; i++) currentNames[currentFields[i].name] = true;
        var merged = [];
        for (var j = 0; j < draftBase.length; j++) if (!currentNames[draftBase[j].name]) merged.push(draftBase[j]);
        for (var k = 0; k < currentFields.length; k++) merged.push(currentFields[k]);
        return { savedAt: new Date().getTime(), fields: merged };
    }

    function saveLocalDraft() {
        if (!getDraftForm()) return null;
        var draft = buildDraft();
        draftBase = draft.fields;
        try {
            if (handoverDraftKey && window.localStorage) localStorage.setItem(handoverDraftKey, JSON.stringify(draft));
        } catch (e) {}
        return draft;
    }

    function saveHandoverDraft() {
        if (handoverSubmitting) return;
        var draft = saveLocalDraft();
        if (!draft) return;
        if (JSON.stringify(draft.fields) !== lastServerFields) {
            setDraftStatus('pending', 'Unsaved changes on this device…');
            scheduleServerSave();
        }
    }

    function scheduleHandoverDraftSave() {
        if (handoverDraftTimer) window.clearTimeout(handoverDraftTimer);
        handoverDraftTimer = window.setTimeout(saveHandoverDraft, 400);
    }

    function scheduleServerSave() {
        if (serverDraftTimer) window.clearTimeout(serverDraftTimer);
        serverDraftTimer = window.setTimeout(function() { pushDraftToServer(false); }, 2500);
    }

    function draftRequestBody(draft) {
        return 'locationId=' + encodeURIComponent(draftLocationId)
                + '&careSetting=' + encodeURIComponent(draftCareSetting)
                + '&draft=' + encodeURIComponent(JSON.stringify(draft));
    }

    /** Sends the draft to the user's account. useBeacon is for page unload, where XHR may be cancelled. */
    function pushDraftToServer(useBeacon) {
        if (handoverSubmitting || !draftLocationId || !getDraftForm()) return;
        var draft = { savedAt: new Date().getTime(), fields: draftBase.length ? draftBase : buildDraft().fields };
        var fieldsJson = JSON.stringify(draft.fields);
        if (fieldsJson === lastServerFields) return;
        var body = draftRequestBody(draft);
        if (useBeacon && navigator.sendBeacon) {
            try {
                if (navigator.sendBeacon(draftSaveUrl, new Blob([body], { type: 'application/x-www-form-urlencoded;charset=UTF-8' })))
                    lastServerFields = fieldsJson;
            } catch (e) {}
            return;
        }
        if (serverSaveInFlight) { scheduleServerSave(); return; }
        serverSaveInFlight = true;
        setDraftStatus('saving', 'Saving draft to your account…');
        var xhr = new XMLHttpRequest();
        xhr.open('POST', draftSaveUrl, true);
        xhr.setRequestHeader('Content-Type', 'application/x-www-form-urlencoded;charset=UTF-8');
        xhr.onreadystatechange = function() {
            if (xhr.readyState !== 4) return;
            serverSaveInFlight = false;
            var ok = false;
            try { ok = xhr.status === 200 && JSON.parse(xhr.responseText).saved === true; } catch (e) {}
            if (ok) {
                lastServerFields = fieldsJson;
                setDraftStatus('saved', 'Draft saved to your account at ' + timeNow());
            } else {
                setDraftStatus('local', 'Saved on this device only — will retry');
                window.setTimeout(function() { pushDraftToServer(false); }, 15000);
            }
        };
        xhr.send(body);
    }

    function parseDraft(raw) {
        if (!raw) return null;
        try {
            var draft = JSON.parse(raw);
            return draft && draft.fields && draft.fields.length !== undefined ? draft : null;
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
                if (saved.value === '' && control.hasAttribute && control.hasAttribute('data-suggestion')) continue;
                control.value = saved.value;
            }
        }
        draftBase = draft.fields;
    }

    /** Restores the newer of the browser draft and the account draft. */
    function restoreHandoverDraft() {
        if (!getDraftForm()) return;
        var local = null;
        try { if (handoverDraftKey && window.localStorage) local = parseDraft(localStorage.getItem(handoverDraftKey)); } catch (e) {}
        var server = parseDraft(serverDraftRaw);
        if (server) lastServerFields = JSON.stringify(server.fields);
        var draft = local && (!server || (local.savedAt || 0) > (server.savedAt || 0)) ? local : server;
        if (!draft) return;
        try {
            applyDraft(draft);
            var when = draft.savedAt ? new Date(draft.savedAt).toLocaleString() : 'earlier';
            setDraftStatus('restored', 'Continuing your draft from ' + when);
            if (draft === local && JSON.stringify(local.fields) !== lastServerFields) scheduleServerSave();
        } catch (e) {
            setDraftStatus('local', 'The saved draft could not be fully restored. It was kept for recovery.');
        }
    }

    function discardHandoverDraft() {
        if (!confirm('Discard all entered handover details and selections?')) return;
        try { if (window.localStorage && handoverDraftKey) localStorage.removeItem(handoverDraftKey); } catch (e) {}
        draftBase = [];
        lastServerFields = null;
        if (serverDraftTimer) window.clearTimeout(serverDraftTimer);
        if (draftLocationId) {
            var xhr = new XMLHttpRequest();
            xhr.open('POST', draftDiscardUrl, true);
            xhr.setRequestHeader('Content-Type', 'application/x-www-form-urlencoded;charset=UTF-8');
            xhr.send('locationId=' + encodeURIComponent(draftLocationId) + '&careSetting=' + encodeURIComponent(draftCareSetting));
        }
        var form = getDraftForm();
        if (form) form.reset();
        refreshAllSuggestionMarkers();
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
        updateAllTaskCounts();
        updateSelectAllPatients();
        updateStatusStyles();
        form.addEventListener('submit', saveLocalDraft, true);
        form.addEventListener('invalid', saveHandoverDraft, true);
        form.addEventListener('input', function(event) {
            if (event.target && event.target.hasAttribute && event.target.hasAttribute('data-suggestion'))
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
        var flush = function() {
            if (handoverSubmitting) return;
            saveLocalDraft();
            pushDraftToServer(true);
        };
        window.addEventListener('pagehide', flush);
        window.addEventListener('beforeunload', flush);
        document.addEventListener('visibilitychange', function() {
            if (document.visibilityState === 'hidden') flush();
        });
        window.setInterval(saveHandoverDraft, 10000);
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
