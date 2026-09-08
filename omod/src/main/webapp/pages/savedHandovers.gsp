<%
    ui.decorateWith("appui", "standardEmrPage")
    ui.includeCss("patienthandover", "patienthandover.css")
    def savedError = binding.hasVariable('savedError') ? binding.getVariable('savedError') : null
    def currentLocation = binding.hasVariable('currentLocation') ? binding.getVariable('currentLocation') : null
    def careSetting = binding.hasVariable('careSetting') ? binding.getVariable('careSetting') : 'ALL'
    def status = binding.hasVariable('status') ? binding.getVariable('status') : 'ALL'
    def fromDate = binding.hasVariable('fromDate') ? binding.getVariable('fromDate') : ''
    def toDate = binding.hasVariable('toDate') ? binding.getVariable('toDate') : ''
    def batches = binding.hasVariable('batches') ? binding.getVariable('batches') : [:]
    def teamsByBatch = binding.hasVariable('teamsByBatch') ? binding.getVariable('teamsByBatch') : [:]
    def openmrsContextPath = binding.hasVariable('openmrsContextPath') ? binding.getVariable('openmrsContextPath') : '/openmrs'
%>
<script type="text/javascript">
var breadcrumbs = [
    { icon: "icon-home", link: '/' + OPENMRS_CONTEXT_PATH + '/index.htm' },
    { label: "${ ui.message('patienthandover.saved.title') }" }
];
</script>
<div class="ph-page">
    ${ ui.includeFragment("patienthandover", "handoverNavigation", [activeTab: "saved"]) }
    <% if (savedError != null) { %><div class="ph-alert ph-alert-error">${ savedError }</div><% } %>
    <% if (currentLocation != null) { %>
        <p><strong>Location:</strong> ${ currentLocation.name }</p>
        <form method="get" class="ph-filter-bar" action="${ ui.pageLink('patienthandover', 'savedHandovers') }">
            <div class="ph-filter-field"><label>From:</label><input type="date" name="fromDate" value="${ fromDate }"/></div>
            <div class="ph-filter-field"><label>To:</label><input type="date" name="toDate" value="${ toDate }"/></div>
            <div class="ph-filter-field"><label>Care Setting:</label><select name="careSetting">
                <option value="ALL" ${ careSetting == 'ALL' ? 'selected="selected"' : '' }>ALL</option>
                <option value="IPD" ${ careSetting == 'IPD' ? 'selected="selected"' : '' }>IPD</option>
                <option value="OPD" ${ careSetting == 'OPD' ? 'selected="selected"' : '' }>OPD</option>
            </select></div>
            <div class="ph-filter-field"><label>Status:</label><select name="status">
                <option value="ALL" ${ status == 'ALL' ? 'selected="selected"' : '' }>ALL</option>
                <option value="PENDING" ${ status == 'PENDING' ? 'selected="selected"' : '' }>PENDING</option>
                <option value="RECEIVED" ${ status == 'RECEIVED' ? 'selected="selected"' : '' }>RECEIVED</option><option value="CANCELLED" ${ status == 'CANCELLED' ? 'selected="selected"' : '' }>CANCELLED</option>
            </select></div>
            <div class="ph-filter-field ph-filter-field-submit"><input type="submit" value="Apply Filters" class="ph-btn-primary"/></div>
        </form>
        <% if (batches == null || batches.isEmpty()) { %>
            <p>No saved handovers match the selected filters.</p>
        <% } else { %>
            <table class="ph-table"><thead><tr><th>No</th><th>Transaction</th><th>Date</th><th>Patients</th><th>Setting</th><th>Shift</th><th>Team Members</th><th>Status</th><th>Received Time</th><th>Action</th></tr></thead><tbody>
            <% int row = 0; batches.each { batchEntry -> row++; def first = batchEntry.value[0]; def team=teamsByBatch[batchEntry.key]?:[]; def senders=team.findAll{it.role=='OUTGOING'&&!it.voided}; def receivers=team.findAll{it.role=='RECEIVING'&&!it.voided} %>
                <tr><td>${ row }</td><td><strong>${ first.transactionReference }</strong></td><td>${ first.dateCreated }</td><td>${ batchEntry.value.size() }</td><td>${ first.careSetting }</td><td>${ first.shift }</td><td><%if(!team.isEmpty()){%><details class="ph-members-details"><summary>View members</summary><div class="ph-team-popover"><button type="button" class="ph-team-popover-close" onclick="this.closest('details').removeAttribute('open')">Close</button><section><h4>Sending Team <span>${senders.count{it.signed}}/${senders.size()}</span></h4><%senders.each{member->%><div class="ph-member-status ${member.signed?'is-signed':'is-pending'}"><span>${member.person?.personName?.fullName}</span><small>${member.signed?'Signed '+(member.dateSigned?:''):'Remaining'}</small></div><%}%></section><section><h4>Receiving Team <span>${receivers.count{it.signed}}/${receivers.size()}</span></h4><%receivers.each{member->%><div class="ph-member-status ${member.signed?'is-signed':'is-pending'}"><span>${member.person?.personName?.fullName}</span><small>${member.signed?'Signed '+(member.dateSigned?:''):'Remaining'}</small></div><%}%></section></div></details><%}else{%><span class="ph-legacy-team">${first.creator?.person?.personName?.fullName?:'-'} → ${first.receivingProvider?.name?:'Unassigned'}</span><%}%></td><td>${ first.status }</td><td>${ first.cancelled ? first.dateCancelled : (first.acknowledged ? first.dateAcknowledged : '-') }</td><td><div class="ph-report-actions"><a class="ph-button-link" href="${ ui.pageLink('patienthandover', 'batchHandover', [batchKey: batchEntry.key]) }">View</a><a class="ph-button-link" href="${ openmrsContextPath }/module/patienthandover/handoverPdf.form?type=PATIENT&amp;key=${ batchEntry.key }">Download PDF</a></div></td></tr>
            <% } %>
            </tbody></table>
        <% } %>
    <% } %>
</div>

<div class="ph-page">
${ ui.includeFragment("patienthandover", "pagination", [
 pageName: "savedHandovers",
 currentPage: binding.hasVariable('batchCurrentPage') ? batchCurrentPage : 1,
 totalPages: binding.hasVariable('batchTotalPages') ? batchTotalPages : 1,
 totalItems: binding.hasVariable('batchTotalItems') ? batchTotalItems : 0,
 parameters: [careSetting: careSetting, status: status, fromDate: fromDate, toDate: toDate]
]) }
</div>
