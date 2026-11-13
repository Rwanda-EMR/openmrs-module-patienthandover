<%
ui.decorateWith("appui", "standardEmrPage")
ui.includeCss("patienthandover", "patienthandover.css")
ui.includeJavascript("patienthandover", "sbar-highlight.js")
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
${ ui.includeFragment("patienthandover", "handoverNavigation", [activeTab: "saved"]) }
<% if (message != null) { %><div class="ph-alert ph-alert-success">${ message }</div><% } %>
<% if (error != null) { %><div class="ph-alert ph-alert-error">${ error }</div><% } %>
<div class="handover-header">
 <div class="handover-meta"><strong>Transaction</strong><br/>${ firstHandover.transactionReference }</div>
 <div class="handover-meta"><strong>Date</strong><br/>${ firstHandover.dateCreated }</div>
 <div class="handover-meta"><strong>From / To</strong><br/>${ firstHandover.location?.name } &rarr; ${ firstHandover.destinationLocation?.name ?: firstHandover.location?.name }</div>
 <div class="handover-meta"><strong>Setting / Shift</strong><br/>${ firstHandover.careSetting } / ${ firstHandover.shift }</div>
 <div class="handover-meta"><strong>Patients</strong><br/>${ handovers.size() }</div>
 <% if (teamMode) { %><div class="handover-meta"><strong>Team progress</strong><br/>Senders: ${ sendersSigned }/${ sendersTotal } &nbsp; Receivers: ${ receiversSigned }/${ receiversTotal }<br/><button type="button" onclick="togglePatientTeam()">View members</button></div><% } else { %>
 <div class="handover-meta"><strong>Outgoing Staff</strong><br/>${ firstHandover.creator?.person?.personName?.fullName ?: '-' }</div>
 <div class="handover-meta"><strong>Receiving Provider</strong><br/>${ firstHandover.receivingProvider?.name ?: '-' }</div><% } %>
 <div class="handover-meta handover-status-card">
  <div><strong>Status</strong><br/>${ firstHandover.status }</div>
  <% if (firstHandover.cancelled) { %><div><strong>Cancelled time</strong><br/>${ firstHandover.dateCancelled }</div><div><strong>Cancelled by</strong><br/>${ firstHandover.cancelledBy?.person?.personName?.fullName ?: '-' }</div><div><strong>Reason</strong><br/>${ firstHandover.cancellationReason }</div><% } else if (firstHandover.acknowledged) { %>
   <div><strong>Received time</strong><br/>${ firstHandover.dateAcknowledged }</div>
   <div><strong>Final receipt signed by</strong><br/>${ firstHandover.acknowledgedBy?.person?.personName?.fullName ?: '-' }</div>
  <% } %>
 </div>

</div>
<% if (teamMode) { %><div id="patientTeamMembers" class="ph-team-members ph-complete-team-dialog" style="display:none"><button type="button" class="ph-team-popover-close" onclick="togglePatientTeam()">Close</button><div><h4>Sending team</h4><% handoverTeam.findAll{it.role=='OUTGOING' && !it.voided}.each{m-> %><div>${m.person?.personName?.fullName} — ${m.signed ? 'Signed ' + (m.dateSigned ?: '') : 'Pending signature'}</div><% } %></div><div><h4>Receiving team</h4><% handoverTeam.findAll{it.role=='RECEIVING' && !it.voided}.each{m-> %><div>${m.person?.personName?.fullName} — ${m.signed ? 'Signed ' + (m.dateSigned ?: '') : 'Pending signature'}</div><% } %></div></div><% } %>
<% if (!firstHandover.acknowledged
 && !firstHandover.cancelled) { %><div class="safety-alert">UNACKNOWLEDGED HANDOVER: Clinical responsibility has not yet been accepted by the receiving provider.</div><% } %>
<div class="handover-toolbar"><label><strong>Find patient:</strong></label> <input id="patientFilter" type="text" placeholder="Name or identifier" onkeyup="applyPatientFilter()"/> <label><input id="criticalOnly" type="checkbox" onclick="applyPatientFilter()"/> Critical only</label> <span class="ph-detail-export-actions"><a class="ph-button-link" href="${ openmrsContextPath }/module/patienthandover/handoverPdf.form?type=PATIENT&amp;key=${ batchKey }">Download PDF</a><a class="ph-button-link" href="${ openmrsContextPath }/module/patienthandover/handoverPdf.form?type=PATIENT&amp;summary=true&amp;key=${ batchKey }">Summarized Report</a></span></div>
<table class="ph-table handover-table"><thead><tr><th>Patient</th><th>Identifier</th><th>Patient status</th><th>Transferred Tasks</th><th>Task Status</th><th>Clinical Details</th></tr></thead><tbody>
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
<% if (canEdit) { %><details class="ph-edit-panel"><summary>Edit this patient handover</summary><form method="post" action="${ openmrsContextPath }/module/patienthandover/batchHandover.form"><input type="hidden" name="batchKey" value="${ batchKey }"/><input type="hidden" name="handoverUuid" value="${ handover.uuid }"/><label>Patient status<select name="priority"><option value="STABLE" ${handover.priority=='STABLE'?'selected':''}>STABLE</option><option value="URGENT" ${handover.priority=='URGENT'?'selected':''}>URGENT</option><option value="CRITICAL" ${handover.priority=='CRITICAL'?'selected':''}>CRITICAL</option><option value="DIED" ${handover.priority=='DIED'?'selected':''}>DIED</option><option value="CURED" ${handover.priority=='CURED'?'selected':''}>CURED / RECOVERED</option><option value="DEFAULTED" ${handover.priority=='DEFAULTED'?'selected':''}>DEFAULTED / LEFT CARE</option><option value="TRANSFERRED" ${handover.priority=='TRANSFERRED'?'selected':''}>TRANSFERRED</option></select></label><label>Situation<textarea name="situation">${ handover.situation }</textarea></label><label>Background<textarea name="background">${ handover.background }</textarea></label><label>Assessment<textarea name="assessment">${ handover.assessment }</textarea></label><label>Recommendation<textarea name="recommendation">${ handover.recommendation }</textarea></label><label>Edit reason<input name="editReason" maxlength="1000" required/></label><button name="editHandover" value="true">Save Patient Correction</button></form></details><% } %>
</td></tr>
<% } %>
</tbody></table>
<div class="receive-panel">
<% if (firstHandover.cancelled) { %><strong>This handover was cancelled and cannot be received or completed.</strong>
<% } else if (firstHandover.acknowledged) { %><strong>This complete handover has been received.</strong>
<% } else { %>
 <% if (canAcknowledge) { %><form method="post" action="${ openmrsContextPath }/module/patienthandover/batchHandover.form"><input type="hidden" name="batchKey" value="${ batchKey }"/><button name="acknowledgeBatch" value="true">${ teamMode ? (teamSignatureRole == 'OUTGOING' ? 'Sign as sending team member' : 'Mark my receipt') : 'Mark Entire Handover as Received' }</button></form><% } else if (teamMode && waitingForSenders) { %><strong>Receiving signatures open after all sending team members sign.</strong><% } else if (teamMode) { %><strong>Awaiting remaining assigned team members.</strong><% } else { %><strong>Awaiting the designated receiving provider.</strong><% } %>
 <% if (canEdit && !teamMode) { %><details class="ph-edit-panel ph-reassign-panel"><summary>Reassign Receiving Provider</summary><form method="post" action="${ openmrsContextPath }/module/patienthandover/batchHandover.form"><input type="hidden" name="batchKey" value="${ batchKey }"/><label><strong>New receiving provider</strong><select name="receivingProviderId" required><option value="">Select</option><% providers.each { p -> if (!p.retired) { %><option value="${ p.providerId }">${ p.name }</option><% } } %></select></label><label><strong>Reason</strong><input name="editReason" maxlength="1000" required/></label><button name="reassignHandover" value="true">Reassign Handover</button></form></details><% } %> <%if(canEdit&&teamMode){%><button type="button" onclick="document.getElementById('editPatientTeam').showModal()">Edit team members</button><dialog id="editPatientTeam" class="ph-edit-dialog ph-team-dialog"><form method="post" action="${openmrsContextPath}/module/patienthandover/batchHandover.form"><input type="hidden" name="batchKey" value="${batchKey}"/><div class="ph-dialog-header"><h3>Edit team members</h3><button type="button" class="ph-dialog-close" onclick="this.closest('dialog').close()">&times;</button></div><p>Signed members and signature times remain in the audit record. Unsigned assignments can be replaced.</p><fieldset><legend>Sending team</legend><div id="editPatientSenders"><%handoverTeam.findAll{it.role=='OUTGOING'&&!it.voided}.each{member->if(member.signed){%><div class="ph-team-row ph-signed-member"><span>${member.person?.personName?.fullName}</span><strong>Signed ${member.dateSigned?:''}</strong></div><%}else{def provider=providers.find{it.person?.personId==member.person?.personId};if(provider){%><div class="ph-team-row"><select name="outgoingProviderIds" required><%providers.each{p->if(!p.retired){%><option value="${p.providerId}" ${p.providerId==provider.providerId?'selected':''}>${p.name}</option><%}}%></select><button type="button" onclick="this.parentNode.parentNode.removeChild(this.parentNode)">Remove</button></div><%}}}%></div><button type="button" onclick="addPatientTeamRow('editPatientSenders','outgoingProviderIds')">Add sender</button></fieldset><fieldset><legend>Receiving team</legend><div id="editPatientReceivers"><%handoverTeam.findAll{it.role=='RECEIVING'&&!it.voided}.each{member->if(member.signed){%><div class="ph-team-row ph-signed-member"><span>${member.person?.personName?.fullName}</span><strong>Signed ${member.dateSigned?:''}</strong></div><%}else{def provider=providers.find{it.person?.personId==member.person?.personId};if(provider){%><div class="ph-team-row"><select name="receivingProviderIds" required><%providers.each{p->if(!p.retired){%><option value="${p.providerId}" ${p.providerId==provider.providerId?'selected':''}>${p.name}</option><%}}%></select><button type="button" onclick="this.parentNode.parentNode.removeChild(this.parentNode)">Remove</button></div><%}}}%></div><button type="button" onclick="addPatientTeamRow('editPatientReceivers','receivingProviderIds')">Add receiver</button></fieldset><label>Reason for change<textarea name="editReason" required maxlength="1000"></textarea></label><div class="ph-dialog-actions"><button type="button" class="cancel" onclick="this.closest('dialog').close()">Close</button><button name="editTeam" value="true">Save team members</button></div></form></dialog><%}%>
<% if (canCancel) { %><form method="post" class="ph-cancel-form" action="${ openmrsContextPath }/module/patienthandover/batchHandover.form" onsubmit="return confirm('Cancel this complete handover? This cannot be undone.');"><input type="hidden" name="batchKey" value="${ batchKey }"/><label><strong>Cancellation reason</strong><input type="text" name="cancellationReason" maxlength="1000" required/></label><button class="ph-cancel-button" name="cancelHandover" value="true">Cancel Handover</button></form><% } %>
<% } %></div>
</div>
<script type="text/javascript">
function addPatientTeamRow(containerId,name){var box=document.getElementById(containerId),row=document.createElement("div"),select=document.createElement("select");row.className="ph-team-row";select.name=name;select.required=true;var blank=document.createElement("option");blank.value="";blank.innerHTML="Select provider";select.appendChild(blank);<%providers.each{p->if(!p.retired){%>var option=document.createElement("option");option.value="${p.providerId}";option.innerHTML="${p.name?.replace('"','\\\"')}";select.appendChild(option);<%}}%>var remove=document.createElement("button");remove.type="button";remove.innerHTML="Remove";remove.onclick=function(){box.removeChild(row);};row.appendChild(select);row.appendChild(remove);box.appendChild(row);}
function togglePatientTeam(){var box=document.getElementById("patientTeamMembers");if(box)box.style.display=box.style.display==="none"?"grid":"none";}
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
