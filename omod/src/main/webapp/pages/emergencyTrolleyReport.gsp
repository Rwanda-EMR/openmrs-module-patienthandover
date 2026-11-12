<%
ui.decorateWith("appui","standardEmrPage")
ui.includeCss("patienthandover","patienthandover.css")
def openmrsContextPath=binding.hasVariable("openmrsContextPath")?binding.getVariable("openmrsContextPath"):"/openmrs"
def teamsByHandover=binding.hasVariable("teamsByHandover")?binding.getVariable("teamsByHandover"):[:]
%>
<script>var breadcrumbs=[{icon:"icon-home",link:'/'+OPENMRS_CONTEXT_PATH+'/index.htm'},{label:"Emergency Trolley Report"}];</script>
<div class="ph-page">${ui.includeFragment("patienthandover","handoverNavigation",[activeTab:"trolleyReport"])}
<%if(currentLocation==null){%>
<div class="ph-alert ph-alert-error">Select a login location.</div>
<%}else{%>
<div class="ph-location-card">
<strong>Location:</strong> ${currentLocation.name}</div>
<form method="get" class="ph-report-filters">
<label>From <input type="date" name="fromDate" value="${fromDate?:''}"/>
</label>
<label>To <input type="date" name="toDate" value="${toDate?:''}"/>
</label>
<label>Shift <select name="shift">
<option ${shift=='ALL'?'selected':''}>ALL</option>
<option ${shift=='DAY'?'selected':''}>DAY</option>
<option ${shift=='NIGHT'?'selected':''}>NIGHT</option>
</select>
</label>
<label>Status <select name="status">
<option ${status=='ALL'?'selected':''}>ALL</option>
<option ${status=='PENDING'?'selected':''}>PENDING</option>
<option ${status=='RECEIVED'?'selected':''}>RECEIVED</option>
<option ${status=='CANCELLED'?'selected':''}>CANCELLED</option>
</select>
</label>
<button type="submit">Run Report</button>
<button type="submit" formaction="${openmrsContextPath}/module/patienthandover/handoverPdf.form" name="type" value="TROLLEY">Download PDF</button>
<button type="submit" formaction="${openmrsContextPath}/module/patienthandover/emergencyTrolleyReport.form">Download CSV</button>
</form>
<div class="ph-report-summary">
<div class="ph-report-summary-card">
<strong>${handovers.size()}</strong>
<span>Filtered trolley handovers</span>
</div>
<div class="ph-report-summary-card">
<strong>${handovers.count{it.status=='PENDING'}}</strong>
<span>Pending</span>
</div>
<div class="ph-report-summary-card">
<strong>${handovers.count{it.status=='RECEIVED'}}</strong>
<span>Received</span>
</div>
</div>
<div class="ph-table-scroll">
<table class="ph-table ph-compact-table">
<thead>
<tr>
<th>Transaction</th>
<th>Submitted</th>
<th>Trolley type</th>
<th>Shift</th>
<th>Team progress</th>
<th>Status</th>
<th>Received / Cancelled Time</th>
<th>Items</th>
<th>Details</th>
</tr>
</thead>
<tbody>
<%if(handovers.isEmpty()){%>
<tr>
<td colspan="9">No trolley handovers match these filters.</td>
</tr>
<%}else{handovers.each{h-> def team=teamsByHandover[h.uuid]?:[]; def senders=team.findAll{it.role=='OUTGOING'&&!it.voided}; def receivers=team.findAll{it.role=='RECEIVING'&&!it.voided};%>
<tr class="ph-report-batch-row">
<td>${h.transactionReference}</td>
<td>${h.dateSubmitted}</td>
<td>${h.trolley?.trolleyName ?: 'Default Emergency Trolley'}</td>
<td>${h.shift}</td>
<td>
<%if(!team.isEmpty()){%>Senders: ${senders.count{it.signed}}/${senders.size()} &nbsp; Receivers: ${receivers.count{it.signed}}/${receivers.size()}<%}else{%>Sender: ${h.creator?.person?.personName?.fullName?:'-'} &nbsp; Receiver: ${h.receivingProvider?.name?:'-'}<%}%>
</td>
<td>${h.status}</td>
<td>${h.status=='CANCELLED' ? h.dateCancelled : (h.dateReceived?:'-')}</td>
<td>${h.items.size()}</td>
<td>
<button type="button" onclick="toggleTrolleyReport('${h.uuid}')">View</button> <a class="ph-button-link" href="${openmrsContextPath}/module/patienthandover/emergencyTrolleyReport.form?handoverUuid=${h.uuid}">Export CSV</a> <a class="ph-button-link" href="${openmrsContextPath}/module/patienthandover/handoverPdf.form?type=TROLLEY&amp;key=${h.uuid}">Download PDF</a>
</td>
</tr>
<tr id="${h.uuid}" class="ph-print-detail-row" style="display:none">
<td colspan="9">
<div class="ph-team-members">
<div>
<h4>Sending team</h4>
<%if(!senders.isEmpty()){%>
<%senders.each{x->%>
<div>${x.person?.personName?.fullName} — ${x.signed?'Signed '+(x.dateSigned?:''):'Pending signature'}</div>
<%}%>
<%}else{%>
<div>${h.creator?.person?.personName?.fullName?:'-'} — Signed ${h.dateSubmitted?:''}</div>
<%}%>
</div>
<div>
<h4>Receiving team</h4>
<%if(!receivers.isEmpty()){%>
<%receivers.each{x->%>
<div>${x.person?.personName?.fullName} — ${x.signed?'Signed '+(x.dateSigned?:''):'Pending signature'}</div>
<%}%>
<%}else{%>
<div>${h.receivingProvider?.name?:'-'} — ${h.status=='RECEIVED'?'Signed '+(h.dateReceived?:''):'Pending signature'}</div>
<%}%>
</div>
</div>
<table class="ph-table ph-compact-table">
<thead>
<tr>
<th>Item</th>
<th>Previous</th>
<th>Handed</th>
<th>Minimum</th><th>Maximum</th><th>Expiration Date</th>
<th>Note</th>
</tr>
</thead>
<tbody>
<%h.items.each{i->%>
<tr>
<td>${i.itemNameSnapshot}</td>
<td>${i.previousQuantity}</td>
<td>${i.handedQuantity}</td>
<td>${i.minimumQuantitySnapshot?:'-'}</td><td>${i.maximumQuantitySnapshot?:'-'}</td><td>${i.expirationDateSnapshot?ui.formatDatePretty(i.expirationDateSnapshot):'No expiry'}</td>
<td>${i.note?:'-'}</td>
</tr>
<%}%>
</tbody>
</table>
<%if(h.status=='CANCELLED'){%>
<p>
<strong>Cancelled by:</strong> ${h.cancelledBy?.person?.personName?.fullName?:'-'} &nbsp; <strong>Reason:</strong> ${h.cancellationReason?:'-'}</p>
<%}%>
</td>
</tr>
<%}}%>
</tbody>
</table>
</div>${ui.includeFragment("patienthandover","pagination",[pageName:"emergencyTrolleyReport",currentPage:binding.hasVariable('trolleyCurrentPage')?trolleyCurrentPage:1,totalPages:binding.hasVariable('trolleyTotalPages')?trolleyTotalPages:1,totalItems:binding.hasVariable('trolleyTotalItems')?trolleyTotalItems:0,parameters:[fromDate:fromDate,toDate:toDate,shift:shift,status:status]])}<%}%>
</div>
<script>function toggleTrolleyReport(id){var r=document.getElementById(id);r.style.display=r.style.display==='table-row'?'none':'table-row';}</script>
