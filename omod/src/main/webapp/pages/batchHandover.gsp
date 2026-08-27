<%
ui.decorateWith("appui", "standardEmrPage")
ui.includeCss("patienthandover", "patienthandover.css")
def message = session.getAttribute("batchHandoverMessage", String.class)
def error = session.getAttribute("batchHandoverError", String.class)
def openmrsContextPath = binding.hasVariable('openmrsContextPath') ? binding.getVariable('openmrsContextPath') : ''
if (message != null) session.setAttribute("batchHandoverMessage", null)
if (error != null) session.setAttribute("batchHandoverError", null)
%>
<script type="text/javascript">
var breadcrumbs = [{ icon: "icon-home", link: '/' + OPENMRS_CONTEXT_PATH + '/index.htm' },
{ label: "Handover Details" }];
</script>
<div class="ph-page">
<h2>Handover Details</h2>
${ ui.includeFragment("patienthandover", "handoverNavigation", [activeTab: "saved"]) }
<% if (message != null) { %><div class="ph-alert ph-alert-success">${ message }</div><% } %>
<% if (error != null) { %><div class="ph-alert ph-alert-error">${ error }</div><% } %>
<div class="handover-header">
 <div class="handover-meta"><strong>Transaction</strong><br/>${ firstHandover.transactionReference }</div>
 <div class="handover-meta"><strong>Date</strong><br/>${ firstHandover.dateCreated }</div>
 <div class="handover-meta"><strong>Location</strong><br/>${ firstHandover.location?.name }</div>
 <div class="handover-meta"><strong>Setting / Shift</strong><br/>${ firstHandover.careSetting } / ${ firstHandover.shift }</div>
 <div class="handover-meta"><strong>Patients</strong><br/>${ handovers.size() }</div>
 <div class="handover-meta"><strong>Outgoing Staff</strong><br/>${ firstHandover.creator?.person?.personName?.fullName ?: '-' }</div>
 <div class="handover-meta"><strong>Receiving Provider</strong><br/>${ firstHandover.receivingProvider?.name ?: '-' }</div>
 <div class="handover-meta handover-status-card">
  <div><strong>Status</strong><br/>${ firstHandover.status }</div>
  <% if (firstHandover.cancelled) { %><div><strong>Cancelled time</strong><br/>${ firstHandover.dateCancelled }</div><div><strong>Cancelled by</strong><br/>${ firstHandover.cancelledBy?.person?.personName?.fullName ?: '-' }</div><div><strong>Reason</strong><br/>${ firstHandover.cancellationReason }</div><% } else if (firstHandover.acknowledged) { %>
   <div><strong>Received time</strong><br/>${ firstHandover.dateAcknowledged }</div>
   <div><strong>Received by</strong><br/>${ firstHandover.acknowledgedBy?.person?.personName?.fullName ?: '-' }</div>
  <% } %>
 </div>

</div><% if (!firstHandover.acknowledged && !firstHandover.cancelled) { %><div class="safety-alert">UNACKNOWLEDGED HANDOVER: Clinical responsibility has not yet been accepted by the receiving provider.</div><% } %>
<div class="handover-toolbar"><label><strong>Find patient:</strong></label> <input id="patientFilter" type="text" placeholder="Name or identifier" onkeyup="applyPatientFilter()"/> <label><input id="criticalOnly" type="checkbox" onclick="applyPatientFilter()"/> Critical only</label> <button type="button" style="float:right" onclick="window.print()">Print</button></div>
<table class="ph-table handover-table"><thead><tr><th>Patient</th><th>Identifier</th><th>Priority</th><th>Transferred Tasks</th><th>Task Status</th><th>Clinical Details</th></tr></thead><tbody>
<% handovers.each { handover -> %>
<tr class="patient-summary ${ handover.priority == 'CRITICAL' ? 'critical-patient' : '' }" data-priority="${ handover.priority }">
 <td>${ handover.patient?.personName?.fullName ?: '-' }</td><td>${ handover.patient?.patientIdentifier?.identifier ?: '-' }</td><td class="priority-${ handover.priority }">${ handover.priority }</td>
 <td><% if (handover.tasks != null && !handover.tasks.isEmpty()) { %><ol><% handover.tasks.each { task -> %><li>${ task.description }<br/><small>Due: ${ task.dueDate } | Assigned: ${ task.assignee?.name ?: '-' }</small></li><% } %></ol><% } else { %>${ handover.pendingTasks ?: 'None' }<% } %></td>
 <td><% if (handover.tasks != null && !handover.tasks.isEmpty()) { handover.tasks.each { task -> %>
  <div class="${ overdueTasks[task.uuid] ? 'task-overdue' : '' }"><strong>${ task.completed ? 'Completed' : (overdueTasks[task.uuid] ? 'OVERDUE' : 'Pending') }</strong>
  <% if (!task.completed && !handover.cancelled && handover.acknowledged && canCompleteTasks && task.assignee?.person?.personId == currentUserPersonId) { %>
  <form method="post" action="${ openmrsContextPath }/module/patienthandover/batchHandover.form"><input type="hidden" name="batchKey" value="${ batchKey }"/><input type="hidden" name="taskUuid" value="${ task.uuid }"/><button name="completeTask" value="true">Mark Complete</button></form><% } %></div>
 <% } } else { %>${ handover.tasksCompleted ? 'Completed' : (handover.pendingTasks ? 'Pending' : 'Not applicable') }<% } %></td>
 <td><button type="button" onclick="togglePatientDetails('details_${ handover.id }')">View SBAR</button></td>
</tr>
<tr id="details_${ handover.id }" class="patient-detail" style="display:none"><td colspan="6"><div class="sbar-grid"><div class="sbar-cell"><strong>Situation</strong><br/>${ handover.situation }</div><div class="sbar-cell"><strong>Background</strong><br/>${ handover.background }</div><div class="sbar-cell"><strong>Assessment</strong><br/>${ handover.assessment }</div><div class="sbar-cell"><strong>Recommendation</strong><br/>${ handover.recommendation }</div></div>
<% if (canEdit) { %><details class="ph-edit-panel"><summary>Edit this patient handover</summary><form method="post" action="${ openmrsContextPath }/module/patienthandover/batchHandover.form"><input type="hidden" name="batchKey" value="${ batchKey }"/><input type="hidden" name="handoverUuid" value="${ handover.uuid }"/><label>Priority<select name="priority"><option value="STABLE" ${handover.priority=='STABLE'?'selected':''}>STABLE</option><option value="URGENT" ${handover.priority=='URGENT'?'selected':''}>URGENT</option><option value="CRITICAL" ${handover.priority=='CRITICAL'?'selected':''}>CRITICAL</option></select></label><label>Situation<textarea name="situation" required>${ handover.situation }</textarea></label><label>Background<textarea name="background" required>${ handover.background }</textarea></label><label>Assessment<textarea name="assessment" required>${ handover.assessment }</textarea></label><label>Recommendation<textarea name="recommendation" required>${ handover.recommendation }</textarea></label><label>Edit reason<input name="editReason" maxlength="1000" required/></label><button name="editHandover" value="true">Save Patient Correction</button></form></details><% } %>
</td></tr>
<% } %>
</tbody></table>
<div class="receive-panel">
<% if (firstHandover.cancelled) { %><strong>This handover was cancelled and cannot be received or completed.</strong>
<% } else if (firstHandover.acknowledged) { %><strong>This complete handover has been received.</strong>
<% } else { %>
 <% if (canAcknowledge) { %><form method="post" action="${ openmrsContextPath }/module/patienthandover/batchHandover.form"><input type="hidden" name="batchKey" value="${ batchKey }"/><button name="acknowledgeBatch" value="true">Mark Entire Handover as Received</button></form><% } else { %><strong>Awaiting the designated receiving provider.</strong><% } %>
 <% if (canEdit) { %><details class="ph-edit-panel ph-reassign-panel"><summary>Reassign Receiving Provider</summary><form method="post" action="${ openmrsContextPath }/module/patienthandover/batchHandover.form"><input type="hidden" name="batchKey" value="${ batchKey }"/><label><strong>New receiving provider</strong><select name="receivingProviderId" required><option value="">Select</option><% providers.each { p -> if (!p.retired) { %><option value="${ p.providerId }">${ p.name }</option><% } } %></select></label><label><strong>Reason</strong><input name="editReason" maxlength="1000" required/></label><button name="reassignHandover" value="true">Reassign Handover</button></form></details><% } %> <% if (canCancel) { %><form method="post" class="ph-cancel-form" action="${ openmrsContextPath }/module/patienthandover/batchHandover.form" onsubmit="return confirm('Cancel this complete handover? This cannot be undone.');"><input type="hidden" name="batchKey" value="${ batchKey }"/><label><strong>Cancellation reason</strong><input type="text" name="cancellationReason" maxlength="1000" required/></label><button class="ph-cancel-button" name="cancelHandover" value="true">Cancel Handover</button></form><% } %>
<% } %></div>
</div>
<script type="text/javascript">
function togglePatientDetails(id){var row=document.getElementById(id);row.style.display=row.style.display==="table-row"?"none":"table-row";}
function applyPatientFilter(){var q=document.getElementById("patientFilter").value.toLowerCase(), critical=document.getElementById("criticalOnly").checked, rows=document.getElementsByClassName("patient-summary");for(var i=0;i<rows.length;i++){var show=rows[i].textContent.toLowerCase().indexOf(q)>=0&&(!critical||rows[i].getAttribute("data-priority")==="CRITICAL");rows[i].style.display=show?"":"none";if(rows[i].nextElementSibling)rows[i].nextElementSibling.style.display="none";}}
</script>

<div class="ph-page">
${ ui.includeFragment("patienthandover", "pagination", [
 pageName: "batchHandover",
 currentPage: binding.hasVariable('patientCurrentPage') ? patientCurrentPage : 1,
 totalPages: binding.hasVariable('patientTotalPages') ? patientTotalPages : 1,
 totalItems: binding.hasVariable('patientTotalItems') ? patientTotalItems : 0,
 parameters: [batchKey: batchKey]
]) }
</div>
