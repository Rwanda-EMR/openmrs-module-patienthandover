<%
    ui.decorateWith("appui", "standardEmrPage")
    ui.includeCss("patienthandover", "patienthandover.css")
    def rows = binding.hasVariable('laboratoryWorklist') ? laboratoryWorklist : []
    def handovers = binding.hasVariable('laboratoryHandovers') ? laboratoryHandovers : []
    def recentHandovers = binding.hasVariable('recentLaboratoryHandovers') ? recentLaboratoryHandovers : []
    def teams = binding.hasVariable('laboratoryTeams') ? laboratoryTeams : [:]
    def h = { value -> value == null ? '' : ui.escapeHtml(value.toString()) }
    def attr = { value -> value == null ? '' : ui.escapeAttribute(value.toString()) }
    def when = { date -> date == null ? '-' : ui.formatDatetimePretty(date) }
    def day = { date -> date == null ? '-' : ui.formatDatePretty(date) }
%>
<script type="text/javascript">
var breadcrumbs = [{ icon: "icon-home", link: '/' + OPENMRS_CONTEXT_PATH + '/index.htm' }, { label: "Laboratory Handover" }];
// No backslash escapes in this block: the Groovy page template would turn them into raw characters.
function labSync(input) {
    var options = document.getElementById('labProviders').options, id = '';
    for (var i = 0; i < options.length; i++) if (options[i].value === input.value) { id = options[i].getAttribute('data-id'); break; }
    input.nextElementSibling.value = id;
    input.setCustomValidity(id ? '' : 'Select a provider from the suggestions.');
}
function labAdd(box, name) {
    var row = document.createElement('div');
    row.className = 'ph-team-row';
    row.innerHTML = '<input type="text" list="labProviders" placeholder="Type provider name" onchange="labSync(this)" required/>'
            + '<input type="hidden" name="' + name + '"/>'
            + '<button type="button" class="ph-btn-ghost" onclick="this.parentNode.parentNode.removeChild(this.parentNode)">Remove</button>';
    document.getElementById(box).appendChild(row);
    row.firstElementChild.focus();
}
function labCount() {
    var boxes = document.querySelectorAll('input[name=laboratoryRowKeys]');
    var checked = 0;
    for (var i = 0; i < boxes.length; i++) {
        var row = boxes[i].parentNode;
        while (row && row.tagName && row.tagName.toLowerCase() !== 'tr') row = row.parentNode;
        if (boxes[i].checked) { checked++; if (row) row.classList.add('is-selected'); }
        else if (row) row.classList.remove('is-selected');
    }
    var all = document.getElementById('labSelectAll');
    if (all) { all.checked = boxes.length > 0 && checked === boxes.length; all.indeterminate = checked > 0 && checked < boxes.length; }
    var counters = document.querySelectorAll('.ph-lab-selected-count');
    for (var c = 0; c < counters.length; c++) counters[c].innerHTML = checked;
}
function labAll(source) {
    var boxes = document.querySelectorAll('input[name=laboratoryRowKeys]');
    for (var i = 0; i < boxes.length; i++) boxes[i].checked = source.checked;
    labCount();
}
function labClose(button) {
    var node = button;
    while (node && node.tagName && node.tagName.toLowerCase() !== 'dialog' && node.tagName.toLowerCase() !== 'details') node = node.parentNode;
    if (!node) return;
    if (node.tagName.toLowerCase() === 'dialog') node.close(); else node.removeAttribute('open');
}
</script>

<div class="ph-page ph-make-page ph-lab-page">
${ ui.includeFragment("patienthandover", "handoverNavigation", [activeTab: "laboratory"]) }

<section class="ph-card ph-setup-card">
    <div class="ph-card-header">
        <div>
            <h2 class="ph-card-title">Laboratory handover</h2>
            <p class="ph-card-subtitle">Signed in at <strong>${ h(currentLocation?.name ?: 'no location selected') }</strong></p>
        </div>
        <% if (laboratoryLocationAllowed) { %>
        <div class="ph-lab-header-forms">
            <form method="get" class="ph-setup-form ph-lab-period">
                <label>From <input type="date" name="fromDate" value="${ attr(fromDate) }" required/></label>
                <label>To <input type="date" name="toDate" value="${ attr(toDate) }" required/></label>
                <input type="submit" value="Load worklist"/>
            </form>
            <form method="get" class="ph-setup-form ph-lab-search">
                <input type="hidden" name="fromDate" value="${ attr(fromDate) }"/>
                <input type="hidden" name="toDate" value="${ attr(toDate) }"/>
                <label>Find and add a patient <input type="text" name="search" value="${ attr(search) }" placeholder="Patient, identifier or lab code"/></label>
                <button type="submit" class="ph-btn-secondary">Search</button>
            </form>
        </div>
        <% } %>
    </div>
</section>

<% if (!laboratoryLocationAllowed) { %>
    <div class="ph-alert ph-alert-error">Laboratory handovers are available only when signed in at a laboratory service location. Administrators may explicitly configure location IDs in <code>patienthandover.laboratoryLocationIds</code>.</div>
</div>
<% return } %>

<% if (binding.hasVariable('laboratoryMessage')) { %><div class="ph-alert ph-alert-success" role="status">${ h(laboratoryMessage) }</div><% } %>
<% if (binding.hasVariable('laboratoryError')) { %><div class="ph-alert ph-alert-error" role="alert">${ h(laboratoryError) }</div><% } %>
<% if (!laboratoryConfigured) { %><div class="ph-alert ph-alert-error">Laboratory worklist is unavailable. Configure the global property <code>laboratorymanagement.orderType.labOrderTypeId</code>.</div><% } %>
<datalist id="labProviders"><% providers.each { p -> if (!p.retired) { %><option value="${ attr(p.name) }" data-id="${ p.providerId }"></option><% } } %></datalist>

<% if (binding.hasVariable('laboratorySearchResults')) { %>
<section class="ph-card">
    <h3 class="ph-section-title">Search results</h3>
    <% if (!laboratorySearchResults) { %>
        <div class="ph-empty-state">No patient with a pending coded laboratory order was found. Patients whose results are already recorded cannot be added.</div>
    <% } else { %>
        <div class="ph-table-scroll"><table class="ph-table ph-lab-table">
            <thead><tr><th>Patient</th><th>Identifier</th><th>Lab code</th><th></th></tr></thead>
            <tbody><% laboratorySearchResults.each { r -> %>
                <tr><td><strong>${ h(r.patientName) }</strong></td><td>${ h(r.identifier) }</td><td><span class="ph-lab-code">${ h(r.labCode) }</span></td>
                    <td class="ph-lab-actions"><form method="post"><input type="hidden" name="action" value="addManual"/><input type="hidden" name="rowKey" value="${ attr(r.key) }"/><button type="submit" class="ph-btn-secondary">+ Add to worklist</button></form></td></tr>
            <% } %></tbody>
        </table></div>
    <% } %>
</section>
<% } %>

<% if (handovers) { %>
<section class="ph-card">
    <h3 class="ph-section-title">Pending laboratory handovers <span class="ph-chip">${ handovers.size() }</span></h3>
    <div class="ph-lab-pending-list">
    <% handovers.eachWithIndex { ho, n ->
        def team = teams[ho.uuid] ?: []
        def senders = team.findAll { it.role == 'OUTGOING' && !it.voided }
        def receivers = team.findAll { it.role == 'RECEIVING' && !it.voided }
        def assignment = team.find { !it.signed && it.person?.personId == currentUser?.person?.personId }
        def canEdit = (ho.creator?.userId == currentUser?.userId) || canManageLaboratory
        def sendersSigned = senders.count { it.signed }
        def receiversSigned = receivers.count { it.signed }
    %>
        <div class="ph-lab-pending">
            <div class="ph-lab-pending-main">
                <strong>${ h(ho.transactionReference) }</strong>
                <span class="ph-hint">${ h(when(ho.dateSubmitted)) } &middot; ${ h(ho.shift) } shift &middot; ${ ho.items.size() } patient(s)</span>
            </div>
            <div class="ph-lab-progress">
                <span class="ph-status-pill ${ sendersSigned == senders.size() ? 'is-done' : 'is-waiting' }">Senders ${ sendersSigned }/${ senders.size() }</span>
                <span class="ph-status-pill ${ receivers && receiversSigned == receivers.size() ? 'is-done' : 'is-waiting' }">Receivers ${ receiversSigned }/${ receivers.size() }</span>
                <details class="ph-members-details"><summary>View members</summary><div class="ph-team-popover">
                    <button type="button" class="ph-team-popover-close" onclick="labClose(this)">Close</button>
                    <section><h4>Sending team</h4><% senders.each { m -> %><div class="ph-member-status ${ m.signed ? 'is-signed' : 'is-pending' }"><span>${ h(m.person?.personName) }</span><small>${ m.signed ? 'Signed ' + h(when(m.dateSigned)) : 'Pending signature' }</small></div><% } %></section>
                    <section><h4>Receiving team</h4><% receivers.each { m -> %><div class="ph-member-status ${ m.signed ? 'is-signed' : 'is-pending' }"><span>${ h(m.person?.personName) }</span><small>${ m.signed ? 'Signed ' + h(when(m.dateSigned)) : 'Pending signature' }</small></div><% } %></section>
                </div></details>
            </div>
            <div class="ph-lab-actions">
                <% if (assignment && (assignment.role == 'OUTGOING' || senders.every { it.signed })) { %>
                    <form method="post"><input type="hidden" name="action" value="sign"/><input type="hidden" name="handoverUuid" value="${ attr(ho.uuid) }"/><button type="submit" class="ph-btn-primary ph-btn-compact">${ assignment.role == 'OUTGOING' ? 'Sign as sender' : 'Mark my receipt' }</button></form>
                <% } %>
                <button type="button" class="ph-btn-secondary" onclick="document.getElementById('lab-${ n }').showModal()">View patients</button>
                <% if (canEdit) { %><button type="button" class="ph-btn-secondary" onclick="document.getElementById('lab-edit-${ n }').showModal()">Edit</button><% } %>
                <a class="ph-btn-secondary" href="${ openmrsContextPath }/module/patienthandover/handoverPdf.form?type=LABORATORY&amp;key=${ attr(ho.uuid) }">PDF</a>
            </div>

            <dialog id="lab-${ n }" class="ph-edit-dialog ph-team-dialog">
                <div class="ph-dialog-header"><h3>${ h(ho.transactionReference) }</h3><button type="button" class="ph-dialog-close" onclick="labClose(this)">&times;</button></div>
                <div class="ph-table-scroll"><table class="ph-table ph-lab-table"><thead><tr><th>Patient</th><th>Identifier</th><th>Lab code</th><th>Ordered</th><th>Comment</th></tr></thead><tbody>
                    <% ho.items.each { i -> %><tr><td><strong>${ h(i.patientName) }</strong></td><td>${ h(i.patientIdentifier) }</td><td><span class="ph-lab-code">${ h(i.labCode) }</span></td><td>${ h(day(i.orderedOn)) }</td><td>${ h(i.comment ?: '-') }</td></tr><% } %>
                </tbody></table></div>
            </dialog>
            <% if (canEdit) { %>
            <dialog id="lab-edit-${ n }" class="ph-edit-dialog ph-laboratory-edit-dialog">
                <form method="post">
                    <div class="ph-dialog-header"><h3>Edit ${ h(ho.transactionReference) }</h3><button type="button" class="ph-dialog-close" onclick="labClose(this)">&times;</button></div>
                    <input type="hidden" name="action" value="editHandover"/><input type="hidden" name="handoverUuid" value="${ attr(ho.uuid) }"/>
                    <div class="ph-edit-lab-list"><% ho.items.each { i -> %><label><strong>${ h(i.patientName) }</strong><small>${ h(i.patientIdentifier) } &middot; ${ h(i.labCode) }</small><textarea name="editComment_${ i.id }" maxlength="2000" rows="3">${ h(i.comment ?: '') }</textarea></label><% } %></div>
                    <div class="ph-dialog-actions"><button type="button" class="ph-btn-ghost" onclick="labClose(this)">Close</button><button type="submit" class="ph-btn-primary ph-btn-compact">Save changes</button></div>
                </form>
            </dialog>
            <% } %>
        </div>
    <% } %>
    </div>
</section>
<% } %>

<form method="post" class="ph-lab-form">
    <input type="hidden" name="fromDate" value="${ attr(fromDate) }"/><input type="hidden" name="toDate" value="${ attr(toDate) }"/>

    <section class="ph-card">
        <h3 class="ph-section-title"><span class="ph-step">1</span> Shift and teams</h3>
        <div class="ph-handover-team-row">
            <label><strong>Shift</strong><select name="shift" required><option value="">-- Select shift --</option><option value="DAY">DAY</option><option value="NIGHT">NIGHT</option></select></label>
            <fieldset class="ph-team-builder"><legend>Sending team</legend><p class="ph-hint">You are included and signed automatically.</p><div id="labSenders"></div><button type="button" class="ph-btn-secondary" onclick="labAdd('labSenders','outgoingProviderIds')">+ Add sender</button></fieldset>
            <fieldset class="ph-team-builder"><legend>Receiving team</legend><div id="labReceivers"></div><button type="button" class="ph-btn-secondary" onclick="labAdd('labReceivers','receivingProviderIds')">+ Add receiver</button></fieldset>
        </div>
    </section>

    <section class="ph-card">
        <div class="ph-section-header">
            <h3 class="ph-section-title"><span class="ph-step">2</span> Patients with a lab code and no result <span class="ph-chip">${ rows.size() } shown</span></h3>
            <div class="ph-table-toolbar">
                <label class="ph-select-all"><input type="checkbox" id="labSelectAll" onchange="labAll(this)"/> Select all</label>
                <span class="ph-selected-count"><strong class="ph-lab-selected-count">0</strong> selected</span>
                <a class="ph-btn-secondary" href="${ ui.pageLink('patienthandover', 'laboratoryHandover', [viewAll: 'true', fromDate: fromDate, toDate: toDate]) }">View all</a>
            </div>
        </div>
        <% if (!rows) { %>
            <div class="ph-empty-state">No pending laboratory orders in this period. Change the dates or use <em>Find and add a patient</em>.</div>
        <% } else { %>
        <div class="ph-table-scroll"><table class="ph-table ph-lab-table ph-lab-worklist">
            <thead><tr><th class="ph-col-select"></th><th>Patient</th><th>Lab code</th><th>Ordered on</th><th>Comment for the next shift</th><th class="ph-col-action"></th></tr></thead>
            <tbody>
            <% rows.each { r -> %>
                <tr>
                    <td class="ph-col-select"><input type="checkbox" name="laboratoryRowKeys" value="${ attr(r.key) }" onchange="labCount()"/></td>
                    <td><strong>${ h(r.patientName) }</strong><span class="ph-lab-sub">${ h(r.identifier ?: 'No identifier') }</span></td>
                    <td><span class="ph-lab-code">${ h(r.labCode) }</span></td>
                    <td>${ h(day(r.orderedOn)) }</td>
                    <td><textarea name="comment_${ r.safeKey }" rows="2" maxlength="2000" placeholder="Optional comment"></textarea></td>
                    <td class="ph-col-action"><button type="submit" name="removeKey" value="${ attr(r.key) }" class="ph-btn-ghost" title="Remove from this worklist">Remove</button></td>
                </tr>
            <% } %>
            </tbody>
        </table></div>
        <% } %>
    </section>

    <div class="ph-submit-bar">
        <span class="ph-submit-summary"><strong class="ph-lab-selected-count">0</strong> patient(s) selected</span>
        <button type="submit" name="action" value="submit" class="ph-btn-primary">Submit selected patients</button>
    </div>
</form>

<% def completedHandovers = recentHandovers.findAll { it.status != 'PENDING' }; if (completedHandovers) { %>
<section class="ph-card">
    <div class="ph-section-header">
        <h3 class="ph-section-title">Recent completed laboratory handovers</h3>
        <a class="ph-btn-secondary" href="${ ui.pageLink('patienthandover', 'laboratoryHandoverReport') }">View full report</a>
    </div>
    <div class="ph-table-scroll"><table class="ph-table ph-lab-table">
        <thead><tr><th>Reference</th><th>Submitted</th><th>Shift</th><th>Status</th><th>Received</th><th>Patients</th><th></th></tr></thead>
        <tbody><% completedHandovers.each { ho -> %>
            <tr>
                <td><strong>${ h(ho.transactionReference) }</strong></td><td>${ h(when(ho.dateSubmitted)) }</td><td>${ h(ho.shift) }</td>
                <td><span class="ph-status-pill ${ ho.status == 'RECEIVED' ? 'is-done' : 'is-waiting' }">${ h(ho.status) }</span></td>
                <td>${ h(when(ho.dateReceived)) }</td><td>${ ho.items.size() }</td>
                <td class="ph-lab-actions">
                    <details class="ph-lab-items"><summary class="ph-btn-secondary">Patients</summary><div class="ph-recent-lab-items"><% ho.items.each { i -> %><div><strong>${ h(i.patientName) }</strong> &middot; ${ h(i.patientIdentifier) } &middot; <span class="ph-lab-code">${ h(i.labCode) }</span><br/><small>${ h(i.comment ?: 'No comment') }</small></div><% } %></div></details>
                    <a class="ph-btn-secondary" href="${ openmrsContextPath }/module/patienthandover/handoverPdf.form?type=LABORATORY&amp;key=${ attr(ho.uuid) }">PDF</a>
                </td>
            </tr>
        <% } %></tbody>
    </table></div>
</section>
<% } %>
</div>
<script type="text/javascript">labCount();</script>
