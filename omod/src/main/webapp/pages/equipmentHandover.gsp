<% ui.decorateWith("appui","standardEmrPage"); ui.includeCss("patienthandover","patienthandover.css"); def items=binding.hasVariable('equipmentItems')?equipmentItems:[]; def handovers=binding.hasVariable('equipmentHandovers')?equipmentHandovers:[]; def teams=binding.hasVariable('equipmentTeams')?equipmentTeams:[:] %>
<script>var breadcrumbs=[{icon:"icon-home",link:'/'+OPENMRS_CONTEXT_PATH+'/index.htm'},{label:"Equipment Handover"}];function eqSync(i){var o=document.getElementById('equipmentProviders').options,id='';for(var x=0;x<o.length;x++)if(o[x].value===i.value){id=o[x].dataset.id;break;}i.nextElementSibling.value=id;i.setCustomValidity(id?'':'Select a provider from the suggestions.');}function eqAdd(box,name,required){var d=document.createElement('div');d.className='ph-team-row';d.innerHTML='<input type="text" list="equipmentProviders" placeholder="Type provider name" onchange="eqSync(this)" '+(required?'required':'')+'/>'+'<input type="hidden" name="'+name+'"/>'+'<button type="button" onclick="this.parentNode.remove()">Remove</button>';document.getElementById(box).appendChild(d);d.firstElementChild.focus();}function eqAll(c){document.querySelectorAll('input[name=equipmentItemIds]').forEach(function(x){x.checked=c.checked;});}</script>
<div class="ph-page">${ui.includeFragment("patienthandover","handoverNavigation",[activeTab:"equipment"])}
<%if(binding.hasVariable('equipmentMessage')){%>
<div class="ph-alert-success">${equipmentMessage}</div>
<%}%>
<%if(binding.hasVariable('equipmentError')){%>
<div class="ph-alert-error">${equipmentError}</div>
<%}%>
<datalist id="equipmentProviders">
<%providers.each{p->if(!p.retired){%>
<option value="${p.name}" data-id="${p.providerId}">
</option>
<%}}%>
</datalist>
<h2>Equipment Handover</h2>
<p class="ph-location-card">
<strong>Login location:</strong> ${currentLocation?.name?:'Select a location'}</p>
<p><a class="ph-button-link" href="${ui.pageLink('patienthandover','equipmentItems')}">Manage equipment items</a></p>
<%if(handovers){%>
<h3>Pending equipment handovers</h3>
<div class="ph-table-scroll">
<table class="ph-table">
<thead>
<tr>
<th>Reference</th>
<th>Submitted</th>
<th>Shift</th>
<th>Items</th>
<th>Team progress</th>
<th>Action</th>
</tr>
</thead>
<tbody>
<%handovers.eachWithIndex{h,n->def team=teams[h.uuid]?:[];def senders=team.findAll{it.role=='OUTGOING'&&!it.voided};def receivers=team.findAll{it.role=='RECEIVING'&&!it.voided};def assignment=team.find{!it.signed&&it.person?.personId==currentUser?.person?.personId};%>
<tr>
<td>${h.transactionReference}</td>
<td>${h.dateSubmitted}</td>
<td>${h.shift}</td>
<td>${h.items.size()}</td>
<td>
<details class="ph-members-details">
<summary>View members</summary>
<div class="ph-team-popover">
<button type="button" class="ph-team-popover-close" onclick="this.closest('details').removeAttribute('open')">Close</button>
<section>
<h4>Sending Team</h4>
<%senders.each{m->%>
<div class="ph-member-status ${m.signed?'is-signed':'is-pending'}">
<span>${m.person?.personName}</span>
<small>${m.signed?'Signed '+m.dateSigned:'Pending signature'}</small>
</div>
<%}%>
</section>
<section>
<h4>Receiving Team</h4>
<%receivers.each{m->%>
<div class="ph-member-status ${m.signed?'is-signed':'is-pending'}">
<span>${m.person?.personName}</span>
<small>${m.signed?'Signed '+m.dateSigned:'Pending signature'}</small>
</div>
<%}%>
</section>
</div>
</details>
<div>Senders: ${senders.count{it.signed}}/${senders.size()} Receivers: ${receivers.count{it.signed}}/${receivers.size()}</div>
</td>
<td>
<button type="button" onclick="document.getElementById('eq-${n}').showModal()">View items</button>
<%if(assignment&&(assignment.role=='OUTGOING'||senders.every{it.signed})){%>
<form method="post" class="ph-inline-form">
<input type="hidden" name="action" value="sign"/>
<input type="hidden" name="handoverUuid" value="${h.uuid}"/>
<button type="submit">${assignment.role=='OUTGOING'?'Sign as sender':'Mark my receipt'}</button>
</form>
<%}%>
<dialog id="eq-${n}" class="ph-edit-dialog ph-team-dialog">
<div class="ph-dialog-header">
<h3>${h.transactionReference} equipment</h3>
<button type="button" class="ph-dialog-close" onclick="this.closest('dialog').close()">&times;</button>
</div>
<table class="ph-table">
<thead>
<tr>
<th>Equipment</th>
<th>Quantity</th>
<th>Status</th>
<th>Observation</th>
</tr>
</thead>
<tbody>
<%h.items.each{i->%>
<tr>
<td>${i.equipmentName}</td>
<td>${i.quantity}</td>
<td>${i.equipmentStatus}</td>
<td>${i.observation?:'-'}</td>
</tr>
<%}%>
</tbody>
</table>
<div class="ph-dialog-actions">
<button onclick="this.closest('dialog').close()">Close</button>
</div>
</dialog>
</td>
</tr>
<%}%>
</tbody>
</table>
</div>
<%}%>
<h3>Make equipment handover</h3>
<%if(!items){%>
<p>Add equipment items manually or upload a CSV file first.</p>
<%}else{%>
<form method="post" class="ph-equipment-handover-form">
<div class="ph-handover-team-row">
<div class="ph-equipment-shift">
<label for="equipmentShift">Shift</label>
<select id="equipmentShift" name="shift" required>
<option value="">Select shift</option>
<option value="DAY">Day</option>
<option value="NIGHT">Night</option>
</select>
</div>
<fieldset class="ph-team-builder">
<legend>Sending team</legend>
<p>Current logged-in user is included and signed automatically.</p>
<div id="eqSenders">
</div>
<button type="button" onclick="eqAdd('eqSenders','outgoingProviderIds',false)">Add sender</button>
</fieldset>
<fieldset class="ph-team-builder">
<legend>Receiving team</legend>
<div id="eqReceivers">
</div>
<button type="button" onclick="eqAdd('eqReceivers','receivingProviderIds',true)">Add receiver</button>
</fieldset>
</div>
<div class="ph-table-scroll ph-equipment-items-table">
<table class="ph-table">
<thead>
<tr>
<th>
<input type="checkbox" onclick="eqAll(this)"/> Select All</th>
<th>Equipment name</th>
<th>Available quantity</th>
<th>Handed quantity</th>
<th>Status</th>
<th>Observation</th>
</tr>
</thead>
<tbody>
<%items.each{i->%>
<tr>
<td>
<input type="checkbox" name="equipmentItemIds" value="${i.id}"/>
</td>
<td>${i.equipmentName}</td>
<td>${i.quantity}</td>
<td>
<input class="ph-equipment-quantity" type="number" name="equipmentQuantity_${i.id}" value="${i.quantity}" min="0.01" max="${i.quantity}" step="0.01" aria-label="Handed quantity for ${i.equipmentName}"/>
</td>
<td><select name="equipmentStatus_${i.id}" required><option value="FUNCTIONAL" ${i.equipmentStatus=='FUNCTIONAL'?'selected':''}>FUNCTIONAL</option><option value="NEEDS MAINTENANCE" ${i.equipmentStatus=='NEEDS MAINTENANCE'?'selected':''}>NEEDS MAINTENANCE</option><option value="OUT OF SERVICE" ${i.equipmentStatus=='OUT OF SERVICE'?'selected':''}>OUT OF SERVICE</option></select></td>
<td><input name="equipmentObservation_${i.id}" maxlength="1000" value="${i.observation?:''}" aria-label="Observation for ${i.equipmentName}"/></td>
</tr>
<%}%>
</tbody>
</table>
</div>
<div class="ph-form-actions">
<button type="submit" name="submitMode" value="selected">Submit selected items</button>
<button type="submit" name="submitMode" value="all">Submit all equipment items</button>
</div>
</form>
<%}%>
</div>
