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
</script>

<div class="ph-page">
    <h2>Make Handover</h2>
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
        def draftStorageKey         = binding.hasVariable('draftStorageKey')         ? draftStorageKey         : null
    %>

    <% if (locationHandoverMessage != null) { %>
        <div style="color: green; margin-bottom: 15px;">
            ${ locationHandoverMessage }
        </div>
    <% } %>

    <% if (locationHandoverError != null) { %>
        <div style="color: red; margin-bottom: 15px;">
            ${ locationHandoverError }
        </div>
    <% } %>

    <% if (worklistError != null) { %>
        <div style="color: red; margin-bottom: 15px;">${ worklistError }</div>
    <% } %>

    <% if (currentLocation != null) { %>
        <p class="ph-location-card"><strong>Current login location:</strong> ${ currentLocation.name }</p>

        <form method="get" class="ph-make-control-row" action="${ ui.pageLink('patienthandover', 'locationHandover') }">
            <label><strong>Care Setting:</strong></label>
            <select name="careSetting">
                <option value="IPD" ${ careSetting == 'IPD' ? 'selected="selected"' : '' }>IPD</option>
                <option value="OPD" ${ careSetting == 'OPD' ? 'selected="selected"' : '' }>OPD</option>
            </select>
            <input type="submit" value="Load Patients"/>
        </form>

        <hr/>

        <h3>Add Patient Manually</h3>

        <form method="get" class="ph-make-control-row" action="${ ui.pageLink('patienthandover', 'locationHandover') }">
            <input type="hidden" name="careSetting" value="${ careSetting }"/>
            <label>Name or Identifier:</label>
            <input type="text" name="query" size="40" value="${ searchQuery ?: '' }" placeholder="Search all patients by name or identifier" required="required"/>
            <input type="submit" value="Search"/>
        </form>

        <% if (searchQuery != null) { %>
            <h4>Search Results</h4>
            <% if (searchResults == null || searchResults.isEmpty()) { %>
                <p>No patients found for <strong>${ searchQuery }</strong>.</p>
            <% } else { %><table class="ph-table">
                    <tr>
                        <th>Name</th>
                        <th>Identifier</th>
                        <th>Gender</th>
                        <th>Action</th>
                    </tr>
                    <% searchResults.each { result -> %>
                        <tr>
                            <td>${ result.personName.fullName }</td>
                            <td>${ result.patientIdentifier?.identifier }</td>
                            <td>${ result.gender }</td>
                            <td>
                                <a href="${ ui.pageLink('patienthandover', 'locationHandover', [careSetting: careSetting, manualPatientId: result.patientId]) }">
                                    Add to Worklist
                                </a>
                            </td>
                        </tr>
                    <% } %>
                </table>
            <% } %>
        <% } %>

        <p>
            <a href="${ ui.pageLink('patienthandover', 'locationHandover', [careSetting: careSetting, clearManual: true]) }">
                Clear Manually Added Patients
            </a>
        </p>

        <hr/>

        <form method="get" class="handover-toolbar ph-worklist-search" action="${ ui.pageLink('patienthandover', 'locationHandover') }">
            <input type="hidden" name="careSetting" value="${ careSetting }"/>
            <label for="worklistPatientFilter"><strong>Find patient in this worklist:</strong></label>
            <span class="ph-search-controls">
                <input id="worklistPatientFilter" type="text" name="worklistQuery" value="${ worklistQuery ?: '' }" placeholder="Name or identifier"/>
                <button type="submit">Search</button>
            </span>
            <% if (worklistQuery != null && !worklistQuery.isEmpty()) { %>
                <a href="${ ui.pageLink('patienthandover', 'locationHandover', [careSetting: careSetting]) }">Clear</a>
            <% } %>
        </form>
        <% if (patients == null || patients.isEmpty()) { %>
            <p>No active ${ careSetting } patients were found at ${ currentLocation.name }.</p>
            <% if (careSetting == 'OPD') { %>
                <p>OPD only includes patients whose latest encounter at this location occurred today.</p>
            <% } %>
            <p>Use the manual patient search above when the patient is not displayed automatically.</p>
        <% } else { %>
            <form id="handoverBatchForm" method="post"
                  action="${ ui.pageLink('patienthandover', 'locationHandover') }"
                  onsubmit="saveHandoverDraft(); return reviewBatch();">

                <input type="hidden" name="careSetting" value="${ careSetting }"/>

                <div class="ph-draft-toolbar" style="margin:12px 0;padding:10px;border:1px solid #c8ded9;background:#f3f8f7;">
                    <strong>Draft:</strong> <span id="draftStatus">Changes are saved automatically on this browser.</span>
                    <button type="button" style="margin-left:15px;" onclick="discardHandoverDraft()">Discard Draft</button>
                </div>
                <div class="ph-handover-meta-row">
                    <label><strong>Shift:</strong>
                        <select name="shift" required="required">
                            <option value="">-- Select Shift --</option>
                            <option value="DAY">DAY</option>
                            <option value="NIGHT">NIGHT</option>
                        </select>
                    </label>
                    <label><strong>Receiving Provider:</strong>
                        <select name="receivingProviderId" required="required">
                            <option value="">-- Select Receiving Provider --</option>
                            <% providers.each { provider -> %>
                                <option value="${ provider.providerId }">${ provider.name }</option>
                            <% } %>
                        </select>
                    </label>
                </div><table class="ph-table">
                    <tr>
                        <th>
                            <input type="checkbox" id="selectAllPatients" onclick="toggleAllPatients(this)"/>
                            <label for="selectAllPatients">Select All</label>
                        </th>
                        <th>Patient</th>
                        <th>Identifier</th>
                        <th>Priority</th>
                        <th>Situation</th>
                        <th>Background</th>
                        <th>Assessment</th>
                        <th>Recommendation</th>
                        <th>Individual Tasks</th>
                        <th>Action</th>
                    </tr>

                    <% patients.each { patient -> %>
                        <tr class="handover-patient-row" data-patient-search="${ (patient.personName.fullName + ' ' + (patient.patientIdentifier?.identifier ?: '')).toLowerCase() }">
                            <td>
                                <input type="checkbox" class="patientSelection" name="patientIds" value="${ patient.patientId }"/>
                            </td>
                            <td>${ patient.personName.fullName }</td>
                            <td>${ patient.patientIdentifier?.identifier }</td>
                            <td>
                                <select name="priority_${ patient.patientId }">
                                    <option value="STABLE">STABLE</option>
                                    <option value="URGENT">URGENT</option>
                                    <option value="CRITICAL">CRITICAL</option>
                                </select>
                            </td>
                            <td><textarea name="situation_${ patient.patientId }" rows="3" cols="20"></textarea></td>
                            <td><textarea name="background_${ patient.patientId }" rows="3" cols="20"></textarea></td>
                            <td><textarea name="assessment_${ patient.patientId }" rows="3" cols="20"></textarea></td>
                            <td><textarea name="recommendation_${ patient.patientId }" rows="3" cols="20"></textarea></td>
                            <td>
                                <details class="ph-task-editor">
                                    <summary><span id="taskCount_${ patient.patientId }">0</span> task(s) - Add / View</summary>
                                    <div id="taskList_${ patient.patientId }" class="structured-task-list">
                                        <div class="structured-task-row" style="border-bottom:1px solid #ddd;padding:5px 0;margin-bottom:5px;">
                                            <textarea name="taskDescription_${ patient.patientId }" rows="2" cols="24" placeholder="Task description"></textarea><br/>
                                            <label>Due:</label> <input type="datetime-local" name="taskDueDate_${ patient.patientId }"/><br/>
                                            <small>Assigned automatically to the receiving provider.</small>
                                            <button type="button" class="remove-task-button">Remove</button>
                                        </div>
                                    </div>
                                    <button type="button" class="add-task-button" data-patient-id="${ patient.patientId }">Add Another Task</button>
                                </details>
                            </td>
                            <td>
                                <a href="${ ui.pageLink('patienthandover', 'locationHandover', [careSetting: careSetting, removePatientId: patient.patientId]) }">
                                    Remove
                                </a>
                            </td>
                        </tr>
                    <% } %>
                </table>

                <p><input type="submit" value="Submit Handover"/></p>
            </form>
        <% } %>
    <% } %>

    <script type="text/javascript">
    function updateTaskCount(patientId) {
        var fields = document.getElementsByName('taskDescription_' + patientId);
        var count = 0;
        for (var i = 0; i < fields.length; i++) {
            var value = fields[i].value;
            if (value != null && value.trim() != '') {
                count++;
            }
        }
        var label = document.getElementById('taskCount_' + patientId);
        if (label) label.innerHTML = count;
    }

    function updateAllTaskCounts() {
        var lists = document.getElementsByClassName('structured-task-list');
        for (var i = 0; i < lists.length; i++) {
            var id = lists[i].id;
            var patientId = id.replace('taskList_', '');
            updateTaskCount(patientId);
        }
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
            if (fields[i].tagName.toLowerCase() === 'select') {
                fields[i].selectedIndex = 0;
            } else {
                fields[i].value = '';
            }
        }
        list.appendChild(row);
        scheduleHandoverDraftSave();
    }

    function removeTaskRow(button) {
        var row = button.parentNode;
        while (row && !((' ' + row.className + ' ').indexOf(' structured-task-row ') >= 0)) {
            row = row.parentNode;
        }
        if (!row) return;
        var list = row.parentNode;
        if (list.children.length === 1) {
            var fields = row.querySelectorAll('textarea, input, select');
            for (var i = 0; i < fields.length; i++) {
                if (fields[i].tagName.toLowerCase() === 'select') {
                    fields[i].selectedIndex = 0;
                } else {
                    fields[i].value = '';
                }
            }
            row.style.display = 'none';
        } else {
            list.removeChild(row);
        }
        updateTaskCount(list.id.replace('taskList_', ''));
        scheduleHandoverDraftSave();
    }
    
    function toggleAllPatients(source) {
        var checkboxes = document.getElementsByClassName("patientSelection");
        for (var i = 0; i < checkboxes.length; i++) {
            checkboxes[i].checked = source.checked;
        }
    }

    function reviewBatch() {
        var selected = document.querySelectorAll('.patientSelection:checked');
        if (!selected.length) {
            alert('Select at least one patient.');
            return false;
        }
        var critical = 0;
        for (var i = 0; i < selected.length; i++) {
            var id = selected[i].value;
            var fields = ['situation_', 'background_', 'assessment_', 'recommendation_'];
            for (var j = 0; j < fields.length; j++) {
                var input = document.getElementsByName(fields[j] + id)[0];
                if (!input || !input.value || !input.value.trim()) {
                    alert('Complete all SBAR fields for every selected patient.');
                    if (input) { input.focus(); }
                    return false;
                }
            }
            var prioritySelect = document.getElementsByName('priority_' + id)[0];
            if (prioritySelect && prioritySelect.value === 'CRITICAL') {
                critical++;
            }
        }
        return confirm('Submit handover\n\nPatients: ' + selected.length
                + '\nCritical: ' + critical
                + '\n\nSubmit this handover as one transaction?');
    }

    var handoverDraftKey = '${ draftStorageKey ?: "" }';
    var handoverDraftTimer = null;

    function getDraftForm() { return document.getElementById('handoverBatchForm'); }

    function collectDraftFields() {
        var form = getDraftForm();
        var fields = [];
        if (!form) return fields;
        var controls = form.querySelectorAll('input[name], select[name], textarea[name]');
        for (var i = 0; i < controls.length; i++) {
            var control = controls[i];
            if (control.type === 'submit' || control.type === 'button') continue;
            fields.push({
                name: control.name,
                value: control.value,
                type: control.type,
                checked: control.checked
            });
        }
        return fields;
    }

    function saveHandoverDraft() {
        if (!handoverDraftKey || !window.localStorage || !getDraftForm()) return;
        try {
            localStorage.setItem(handoverDraftKey, JSON.stringify({
                savedAt: new Date().getTime(),
                fields: collectDraftFields()
            }));
            var status = document.getElementById('draftStatus');
            if (status) status.innerHTML = 'Saved automatically at ' + new Date().toLocaleTimeString() + '.';
        } catch (e) {
            var failedStatus = document.getElementById('draftStatus');
            if (failedStatus) failedStatus.innerHTML = 'Automatic saving is unavailable in this browser.';
        }
    }

    function scheduleHandoverDraftSave() {
        if (handoverDraftTimer) window.clearTimeout(handoverDraftTimer);
        handoverDraftTimer = window.setTimeout(saveHandoverDraft, 400);
    }

    function restoreHandoverDraft() {
        if (!handoverDraftKey || !window.localStorage || !getDraftForm()) return;
        try {
            var raw = localStorage.getItem(handoverDraftKey);
            if (!raw) return;
            var draft = JSON.parse(raw);
            if (!draft || !draft.fields) return;
            var requiredTaskRows = {};
            for (var countIndex = 0; countIndex < draft.fields.length; countIndex++) {
                var fieldName = draft.fields[countIndex].name;
                if (fieldName.indexOf('taskDescription_') === 0) {
                    var patientId = fieldName.substring('taskDescription_'.length);
                    if (patientId) {
                        requiredTaskRows[patientId] = (requiredTaskRows[patientId] || 0) + 1;
                    }
                }
            }
            for (var patientId in requiredTaskRows) {
                while (document.getElementsByName('taskDescription_' + patientId).length < requiredTaskRows[patientId]) {
                    addTaskRow(patientId);
                }
            }
            var occurrences = {};
            for (var i = 0; i < draft.fields.length; i++) {
                var saved = draft.fields[i];
                var controls = document.getElementsByName(saved.name);
                var occurrence = occurrences[saved.name] || 0;
                occurrences[saved.name] = occurrence + 1;
                if (saved.type === 'checkbox' || saved.type === 'radio') {
                    for (var j = 0; j < controls.length; j++) {
                        if (controls[j].value === saved.value) {
                            controls[j].checked = saved.checked;
                        }
                    }
                } else if (controls.length > occurrence) {
                    controls[occurrence].value = saved.value;
                }
            }
            var status = document.getElementById('draftStatus');
            if (status) status.innerHTML = 'Restored an autosaved draft from this browser.';
        } catch (e) {
            localStorage.removeItem(handoverDraftKey);
        }
    }

    function discardHandoverDraft() {
        if (!confirm('Discard all entered handover details and selections?')) return;
        if (window.localStorage && handoverDraftKey) localStorage.removeItem(handoverDraftKey);
        var form = getDraftForm();
        if (form) form.reset();
        var status = document.getElementById('draftStatus');
        if (status) status.innerHTML = 'Draft discarded.';
    }


    (function initialiseHandoverDraft() {
        var form = getDraftForm();
        if (!form) return;
        restoreHandoverDraft();
        updateAllTaskCounts();
        if (form.addEventListener) {

            form.addEventListener('input', function() {
                updateAllTaskCounts();
                scheduleHandoverDraftSave();
            });
            form.addEventListener('change', scheduleHandoverDraftSave);
            window.addEventListener('beforeunload', saveHandoverDraft);
        }
    })();
</script>
</div>
<div class="ph-page">
${ ui.includeFragment("patienthandover", "pagination", [
 pageName: "locationHandover",
 currentPage: binding.hasVariable('patientCurrentPage') ? patientCurrentPage : 1,
 totalPages: binding.hasVariable('patientTotalPages') ? patientTotalPages : 1,
 totalItems: binding.hasVariable('patientTotalItems') ? patientTotalItems : 0,
 parameters: [careSetting: careSetting, worklistQuery: worklistQuery]
]) }
</div>
