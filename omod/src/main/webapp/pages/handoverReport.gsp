<%
    ui.decorateWith("appui", "standardEmrPage")
    ui.includeCss("patienthandover", "patienthandover.css")
    def reportError = binding.hasVariable('reportError') ? binding.getVariable('reportError') : null
    def currentLocation = binding.hasVariable('currentLocation') ? binding.getVariable('currentLocation') : null
    def fromDate = binding.hasVariable('fromDate') ? binding.getVariable('fromDate') : ''
    def toDate = binding.hasVariable('toDate') ? binding.getVariable('toDate') : ''
    def careSetting = binding.hasVariable('careSetting') ? binding.getVariable('careSetting') : 'ALL'
    def shift = binding.hasVariable('shift') ? binding.getVariable('shift') : 'ALL'
    def status = binding.hasVariable('status') ? binding.getVariable('status') : 'ALL'
    def batches = binding.hasVariable('batches') ? binding.getVariable('batches') : [:]
    def batchCount = binding.hasVariable('batchCount') ? binding.getVariable('batchCount') : 0
    def patientCount = binding.hasVariable('patientCount') ? binding.getVariable('patientCount') : 0
    def pendingCount = binding.hasVariable('pendingCount') ? binding.getVariable('pendingCount') : 0
    def receivedCount = binding.hasVariable('receivedCount') ? binding.getVariable('receivedCount') : 0
    def criticalCount = binding.hasVariable('criticalCount') ? binding.getVariable('criticalCount') : 0
    def canExportReports = binding.hasVariable('canExportReports') ? binding.getVariable('canExportReports') : false
    def openmrsContextPath = binding.hasVariable('openmrsContextPath') ? binding.getVariable('openmrsContextPath') : ''
%>

<script type="text/javascript">
    var breadcrumbs = [
        { icon: "icon-home", link: '/' + OPENMRS_CONTEXT_PATH + '/index.htm' },
        { label: "${ ui.message("patienthandover.audit.title") }" }
    ];
</script>

<div class="ph-page">
    <h2>Patient Handover Audit Report</h2>
    ${ ui.includeFragment("patienthandover", "handoverNavigation", [ activeTab: "audit" ]) }

    <% if (reportError != null) { %>
        <div class="ph-alert ph-alert-error">${ reportError }</div>
    <% } %>

    <% if (currentLocation != null) { %>
        <p><strong>Location:</strong> ${ currentLocation.name }</p>

        <form method="get" class="ph-filter-bar ph-report-filters" action="${ ui.pageLink('patienthandover', 'handoverReport') }">
            <div class="ph-filter-field">
                <label for="reportFromDate">From</label>
                <input id="reportFromDate" type="date" name="fromDate" value="${ fromDate }"/>
            </div>
            <div class="ph-filter-field">
                <label for="reportToDate">To</label>
                <input id="reportToDate" type="date" name="toDate" value="${ toDate }"/>
            </div>
            <div class="ph-filter-field">
                <label for="reportCareSetting">Care Setting</label>
                <select id="reportCareSetting" name="careSetting">
                    <option value="ALL" ${ careSetting == 'ALL' ? 'selected="selected"' : '' }>ALL</option>
                    <option value="IPD" ${ careSetting == 'IPD' ? 'selected="selected"' : '' }>IPD</option>
                    <option value="OPD" ${ careSetting == 'OPD' ? 'selected="selected"' : '' }>OPD</option>
                </select>
            </div>
            <div class="ph-filter-field">
                <label for="reportShift">Shift</label>
                <select id="reportShift" name="shift">
                    <option value="ALL" ${ shift == 'ALL' ? 'selected="selected"' : '' }>ALL</option>
                    <option value="DAY" ${ shift == 'DAY' ? 'selected="selected"' : '' }>DAY</option>
                    <option value="NIGHT" ${ shift == 'NIGHT' ? 'selected="selected"' : '' }>NIGHT</option>
                </select>
            </div>
            <div class="ph-filter-field">
                <label for="reportStatus">Status</label>
                <select id="reportStatus" name="status">
                    <option value="ALL" ${ status == 'ALL' ? 'selected="selected"' : '' }>ALL</option>
                    <option value="PENDING" ${ status == 'PENDING' ? 'selected="selected"' : '' }>PENDING</option>
                    <option value="RECEIVED" ${ status == 'RECEIVED' ? 'selected="selected"' : '' }>RECEIVED</option><option value="CANCELLED" ${ status == 'CANCELLED' ? 'selected="selected"' : '' }>CANCELLED</option>
                </select>
            </div>
            <div class="ph-filter-field ph-filter-field-submit ph-report-actions">
                <button type="submit">Run Report</button>
                <% if (canExportReports) { %><button type="submit" name="export" value="true" formaction="${ openmrsContextPath }/module/patienthandover/handoverReport.form">Download CSV</button><% } %>
            </div>
        </form>

        <% if (reportError == null) { %>
            <div style="margin:20px 0;">
                <strong>Handover transactions:</strong> ${ batchCount } &nbsp;|&nbsp;
                <strong>Patients:</strong> ${ patientCount } &nbsp;|&nbsp;
                <strong>Pending:</strong> ${ pendingCount } &nbsp;|&nbsp;
                <strong>Received:</strong> ${ receivedCount } &nbsp;|&nbsp;
                <strong style="color:red;">Critical:</strong> ${ criticalCount }
            </div>

            <% if (batches == null || batches.isEmpty()) { %>
                <p>No handovers match the selected report filters.</p>
            <% } else { %>
                <table class="ph-table">
                    <tr>
                        <th>No</th><th>Transaction</th><th>Date</th><th>Patients</th><th>Setting</th>
                        <th>Shift</th><th>Outgoing Staff</th><th>Receiving Provider</th><th>Status</th><th>Received Time</th><th>Action</th>
                    </tr>
                    <%
                        int batchNumber = 0
                        batches.each { batchEntry ->
                            batchNumber++
                            def firstHandover = batchEntry.value[0]
                    %>
                        <tr>
                            <td>${ batchNumber }</td>
                            <td><strong>${ firstHandover.transactionReference }</strong></td>
                            <td>${ firstHandover.dateCreated }</td>
                            <td>${ batchEntry.value.size() }</td>
                            <td>${ firstHandover.careSetting }</td>
                            <td>${ firstHandover.shift }</td>
                            <td>${ firstHandover.creator?.person?.personName?.fullName ?: '-' }</td>
                            <td>${ firstHandover.receivingProvider?.name ?: 'Unassigned' }</td>
                            <td>
                                ${ firstHandover.status }
                            </td>
                            <td>${ firstHandover.cancelled ? firstHandover.dateCancelled : (firstHandover.acknowledged ? firstHandover.dateAcknowledged : '-') }</td>
                            <td>
                                <a href="${ ui.pageLink('patienthandover', 'batchHandover', [batchKey: batchEntry.key]) }">View</a>
                            </td>
                        </tr>
                        <tr>
                            <td></td>
                            <td colspan="10">
                                <details>
                                    <summary>View ${ batchEntry.value.size() } patient(s) and SBAR</summary>
                                    <table class="ph-table" style="margin-top:8px;">
                                        <tr>
                                            <th>Patient</th><th>Identifier</th><th>Priority</th><th>Situation</th>
                                            <th>Background</th><th>Assessment</th><th>Recommendation</th>
                                            <th>Transferred Tasks</th><th>Task Due</th><th>Assignee</th>
                                            <th>Task Status</th><th>Completed By</th><th>Completed Date</th>
                                        </tr>
                                        <% batchEntry.value.each { handover -> %>
                                            <tr>
                                                <td>${ handover.patient?.personName?.fullName ?: '-' }</td>
                                                <td>${ handover.patient?.patientIdentifier?.identifier ?: '-' }</td>
                                                <td>${ handover.priority }</td>
                                                <td>${ handover.situation }</td>
                                                <td>${ handover.background }</td>
                                                <td>${ handover.assessment }</td>
                                                <td>${ handover.recommendation }</td>
                                                <td>
                                                    <% if (handover.tasks != null && !handover.tasks.isEmpty()) { %>
                                                        <% handover.tasks.each { task -> %>
                                                            ${ task.description }<br/>
                                                        <% } %>
                                                    <% } else { %>
                                                        ${ handover.pendingTasks }
                                                    <% } %>
                                                </td>
                                                <td>
                                                    <% if (handover.tasks != null && !handover.tasks.isEmpty()) { %>
                                                        <% handover.tasks.each { task -> %>
                                                            ${ task.dueDate }<br/>
                                                        <% } %>
                                                    <% } else { %>
                                                        ${ handover.taskDueDate }
                                                    <% } %>
                                                </td>
                                                <td>
                                                    <% if (handover.tasks != null && !handover.tasks.isEmpty()) { %>
                                                        <% handover.tasks.each { task -> %>
                                                            ${ task.assignee?.name ?: '-' }<br/>
                                                        <% } %>
                                                    <% } else { %>
                                                        ${ handover.receivingProvider?.name ?: '-' }
                                                    <% } %>
                                                </td>
                                                <td>
                                                    <% if (handover.tasks != null && !handover.tasks.isEmpty()) { %>
                                                        <% handover.tasks.each { task -> %>
                                                            <% if (task.completed) { %>Completed<% } else { %>Pending<% } %><br/>
                                                        <% } %>
                                                    <% } else { %>
                                                        <% if (handover.pendingTasks == null || handover.pendingTasks.isEmpty()) { %>
                                                            Not applicable
                                                        <% } else if (handover.tasksCompleted) { %>
                                                            Completed
                                                        <% } else { %>
                                                            Pending
                                                        <% } %>
                                                    <% } %>
                                                </td>
                                                <td>
                                                    <% if (handover.tasks != null && !handover.tasks.isEmpty()) { %>
                                                        <% handover.tasks.each { task -> %>
                                                            ${ task.completedBy?.person?.personName?.fullName ?: '-' }<br/>
                                                        <% } %>
                                                    <% } else { %>
                                                        ${ handover.tasksCompletedBy?.person?.personName?.fullName ?: '-' }
                                                    <% } %>
                                                </td>
                                                <td>
                                                    <% if (handover.tasks != null && !handover.tasks.isEmpty()) { %>
                                                        <% handover.tasks.each { task -> %>
                                                            ${ task.dateCompleted }<br/>
                                                        <% } %>
                                                    <% } else { %>
                                                        ${ handover.dateTasksCompleted }
                                                    <% } %>
                                                </td>
                                            </tr>
                                        <% } %>
                                    </table>
                                </details>
                            </td>
                        </tr>
                    <% } %>
                </table>
            <% } %>
        <% } %>
    <% } %>
</div>
<div class="ph-page">
${ ui.includeFragment("patienthandover", "pagination", [
 pageName: "handoverReport", pageParameter: "page",
 currentPage: binding.hasVariable('batchCurrentPage') ? batchCurrentPage : 1,
 totalPages: binding.hasVariable('batchTotalPages') ? batchTotalPages : 1,
 totalItems: binding.hasVariable('batchTotalItems') ? batchTotalItems : 0,
 parameters: [fromDate: fromDate, toDate: toDate, careSetting: careSetting, shift: shift, status: status]
]) }
</div>
