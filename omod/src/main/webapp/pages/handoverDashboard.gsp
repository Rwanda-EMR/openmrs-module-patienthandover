<%
    ui.decorateWith("appui", "standardEmrPage")
    ui.includeCss("patienthandover", "patienthandover.css")
%>

<script type="text/javascript">
    var breadcrumbs = [
        { icon: "icon-home", link: '/' + OPENMRS_CONTEXT_PATH + '/index.htm' },
        { label: "${ ui.message("patienthandover.dashboard.title") }" }
    ];
</script>

<div class="ph-page">
    ${ ui.includeFragment("patienthandover", "handoverNavigation", [ activeTab: "dashboard" ]) }

    <%
    def completedDraftStorageKey = session.getAttribute("completedDraftStorageKey", String.class)
    if (completedDraftStorageKey != null) {
%>
    <script type="text/javascript">
        try { window.localStorage.removeItem('${ completedDraftStorageKey }'); } catch (e) {}
    </script>
    <%
        session.setAttribute("completedDraftStorageKey", null)
    %>
<% } %>

<%
    def dashboardMessage = session.getAttribute("dashboardMessage", String.class)
    if (dashboardMessage != null) {
%>
    <div class="ph-alert ph-alert-success">${ dashboardMessage }</div>
    <%
        session.setAttribute("dashboardMessage", null)
    %>
<% } %>

<%
    def dashboardError = session.getAttribute("dashboardError", String.class)
    if (dashboardError != null) {
%>
    <div class="ph-alert ph-alert-error">${ dashboardError }</div>
    <%
        session.setAttribute("dashboardError", null)
    %>
<% } %>

    <%
        // currentLocation is not in the page model - derive it from sessionContext instead
        def currentLocation = sessionContext.sessionLocation
        if (currentLocation != null) {
    %>
        <p><strong>Location:</strong> ${ currentLocation.name }</p>

        <%
            if (myIncomingBatchCount > 0) {
        %>
            <div class="ph-alert ph-alert-warning">
                <strong>${ myIncomingBatchCount } incoming handover(s) are waiting for you.</strong>
                <a href="${ ui.pageLink('patienthandover', 'handoverDashboard', [assignedToMe: true, status: 'PENDING']) }">Open My Incoming Handovers</a>
            </div>
        <% } else { %>
            <div class="ph-alert ph-alert-info">No incoming handovers are currently waiting for you.</div>
        <% } %>

        <form method="get" class="ph-filter-bar" action="${ ui.pageLink('patienthandover', 'handoverDashboard') }">
    <div class="ph-filter-field">
        <label for="fromDate">From:</label>
        <input type="date" id="fromDate" name="fromDate" value="${ fromDate }"/>
    </div>

    <div class="ph-filter-field">
        <label for="toDate">To:</label>
        <input type="date" id="toDate" name="toDate" value="${ toDate }"/>
    </div>

    <div class="ph-filter-field">
        <label for="careSetting">Care Setting:</label>
        <select id="careSetting" name="careSetting">
            <option value="ALL" ${ careSetting == 'ALL' ? 'selected="selected"' : '' }>ALL</option>
            <option value="IPD" ${ careSetting == 'IPD' ? 'selected="selected"' : '' }>IPD</option>
            <option value="OPD" ${ careSetting == 'OPD' ? 'selected="selected"' : '' }>OPD</option>
        </select>
    </div>

    <div class="ph-filter-field">
        <label for="shift">Shift:</label>
        <select id="shift" name="shift">
            <option value="ALL" ${ shift == 'ALL' ? 'selected="selected"' : '' }>ALL</option>
            <option value="DAY" ${ shift == 'DAY' ? 'selected="selected"' : '' }>DAY</option>
            <option value="NIGHT" ${ shift == 'NIGHT' ? 'selected="selected"' : '' }>NIGHT</option>
        </select>
    </div>

    <div class="ph-filter-field">
        <label for="status">Status:</label>
        <select id="status" name="status">
            <option value="PENDING" ${ status == 'PENDING' ? 'selected="selected"' : '' }>PENDING</option>
            <option value="OVERDUE" ${ status == 'OVERDUE' ? 'selected="selected"' : '' }>OVERDUE</option>
            <option value="RECEIVED" ${ status == 'RECEIVED' ? 'selected="selected"' : '' }>RECEIVED</option><option value="CANCELLED" ${ status == 'CANCELLED' ? 'selected="selected"' : '' }>CANCELLED</option>
            <option value="ALL" ${ status == 'ALL' ? 'selected="selected"' : '' }>ALL</option>
        </select>
    </div>

    <div class="ph-filter-field ph-filter-field-checkbox">
        <label class="ph-checkbox-label">
            <input type="checkbox" name="assignedToMe" value="true" ${ assignedToMe ? 'checked="checked"' : '' }/>
            My Incoming Handovers
        </label>
    </div>

    <div class="ph-filter-field ph-filter-field-submit">
        <input type="submit" value="Apply Filters" class="ph-btn-primary"/>
    </div>
</form>

        <p class="ph-hint">Pending handovers become overdue after <strong>${ overdueHours } hours</strong>.</p>

        <!-- Stat cards -->
        <div class="ph-stat-grid">
            <div class="ph-stat-card">
                <div class="ph-stat-label">Pending handovers</div>
                <div class="ph-stat-value">${ pendingBatchCount }</div>
            </div>
            <div class="ph-stat-card ph-stat-card-overdue">
                <div class="ph-stat-label">Overdue handovers</div>
                <div class="ph-stat-value">${ overdueBatchCount }</div>
            </div>
            <div class="ph-stat-card">
                <div class="ph-stat-label">Pending patients</div>
                <div class="ph-stat-value">${ pendingPatientCount }</div>
            </div>
            <div class="ph-stat-card ph-stat-card-critical">
                <div class="ph-stat-label">Critical pending patients</div>
                <div class="ph-stat-value">${ criticalPatientCount }</div>
            </div>
            <div class="ph-stat-card ph-stat-card-overdue">
                <div class="ph-stat-label">Overdue tasks</div>
                <div class="ph-stat-value">${ overdueTaskCount }</div>
            </div>
        </div>

        <%
            if (batches == null || batches.isEmpty()) {
        %>
            <p>No handovers match the selected filters${ assignedToMe ? ' assigned to you' : '' }.</p>
        <% } else { %>
            <table class="ph-table">
                <thead>
                    <tr>
                        <th>Transaction</th>
                        <th>Date</th>
                        <th>Age</th>
                        <th>Setting</th>
                        <th>Shift</th>
                        <th>Patients</th>
                        <th>Critical</th>
                        <th>Outgoing Staff</th>
                        <th>Receiving Provider</th>
                        <th>Status</th>
                        <th>Received Time</th>
                        <th>Action</th>
                    </tr>
                </thead>
                <tbody>
                    <%
                        batches.each { batchEntry ->
                            def firstHandover = batchEntry.value[0]
                            def batchCritical = 0
                            batchEntry.value.each { item ->
                                if (item.priority == 'CRITICAL') {
                                    batchCritical++
                                }
                            }
                    %>
                        <tr ${ overdueByBatch[batchEntry.key] ? 'class="ph-row-overdue"' : '' }>

                            <td><strong>${ firstHandover.transactionReference }</strong></td>
                            <td>${ firstHandover.dateCreated }</td>
                            <td>${ batchAgeHours[batchEntry.key] } hour(s)</td>
                            <td>${ firstHandover.careSetting }</td>
                            <td>${ firstHandover.shift }</td>
                            <td>${ batchEntry.value.size() }</td>
                            <td>${ batchCritical }</td>
                           <td>${ firstHandover.creator?.person?.personName?.fullName ?: '-' }</td>
                            <td>${ firstHandover.receivingProvider?.name ?: 'Unassigned' }</td>
                            <td>
                                <% if (firstHandover.acknowledged) { %>
                                    Received
                                <% } else if (overdueByBatch[batchEntry.key]) { %>
                                    <strong class="ph-text-overdue">OVERDUE</strong>
                                <% } else { %>
                                    Pending
                                <% } %>
                            </td>
                            <td>${ firstHandover.cancelled ? firstHandover.dateCancelled : (firstHandover.acknowledged ? firstHandover.dateAcknowledged : '-') }</td>
                            <td>
                                <a href="${ ui.pageLink('patienthandover', 'batchHandover', [batchKey: batchEntry.key]) }">View</a>
                            </td>

                        </tr>
                    <% } %>
                </tbody>
            </table>
        <% } %>
    <% } %>
</div>
<div class="ph-page">
${ ui.includeFragment("patienthandover", "pagination", [
 pageName: "handoverDashboard",
 currentPage: binding.hasVariable('batchCurrentPage') ? batchCurrentPage : 1,
 totalPages: binding.hasVariable('batchTotalPages') ? batchTotalPages : 1,
 totalItems: binding.hasVariable('batchTotalItems') ? batchTotalItems : 0,
 parameters: [careSetting: careSetting, shift: shift, status: status, assignedToMe: assignedToMe, fromDate: fromDate, toDate: toDate]
]) }
</div>
