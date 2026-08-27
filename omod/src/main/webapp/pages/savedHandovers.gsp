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
%>
<script type="text/javascript">
var breadcrumbs = [
    { icon: "icon-home", link: '/' + OPENMRS_CONTEXT_PATH + '/index.htm' },
    { label: "${ ui.message('patienthandover.saved.title') }" }
];
</script>
<div class="ph-page">
    <h2>Saved Handover Transactions</h2>
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
            <table class="ph-table"><thead><tr><th>No</th><th>Transaction</th><th>Date</th><th>Patients</th><th>Setting</th><th>Shift</th><th>Outgoing Staff</th><th>Receiving Provider</th><th>Status</th><th>Received Time</th><th>Action</th></tr></thead><tbody>
            <% int row = 0; batches.each { batchEntry -> row++; def first = batchEntry.value[0] %>
                <tr><td>${ row }</td><td><strong>${ first.transactionReference }</strong></td><td>${ first.dateCreated }</td><td>${ batchEntry.value.size() }</td><td>${ first.careSetting }</td><td>${ first.shift }</td><td>${ first.creator?.person?.personName?.fullName ?: '-' }</td><td>${ first.receivingProvider?.name ?: 'Unassigned' }</td><td>${ first.status }</td><td>${ first.cancelled ? first.dateCancelled : (first.acknowledged ? first.dateAcknowledged : '-') }</td><td><a href="${ ui.pageLink('patienthandover', 'batchHandover', [batchKey: batchEntry.key]) }">View</a></td></tr>
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
