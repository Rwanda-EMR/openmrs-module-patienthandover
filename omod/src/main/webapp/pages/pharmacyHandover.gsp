<%
ui.decorateWith("appui", "standardEmrPage")
ui.includeCss("patienthandover", "patienthandover.css")
def locations=binding.hasVariable('pharmacyLocations')?pharmacyLocations:[]
def selected=binding.hasVariable('selectedPharmacyLocation')?selectedPharmacyLocation:null
def balances=binding.hasVariable('inventoryBalances')?inventoryBalances:[]
def displayedBalances=binding.hasVariable('displayedInventoryBalances')?displayedInventoryBalances:[]
def handovers=binding.hasVariable('pharmacyHandovers')?pharmacyHandovers:[]
def ages=binding.hasVariable('pharmacyHandoverAges')?pharmacyHandoverAges:[:]
def senderProgress=binding.hasVariable('pharmacySenderProgress')?pharmacySenderProgress:[:]
def receiverProgress=binding.hasVariable('pharmacyReceiverProgress')?pharmacyReceiverProgress:[:]
def signatureActions=binding.hasVariable('pharmacySignatureActions')?pharmacySignatureActions:[:]
def editBalances=binding.hasVariable('pharmacyEditBalances')?pharmacyEditBalances:[:]
def currentPersonId=outgoingUser?.person?.personId
%>
<script>var breadcrumbs=[{icon:"icon-home",link:'/'+OPENMRS_CONTEXT_PATH+'/index.htm'},{label:"Pharmacy Handover"}];function togglePendingItems(id){var row=document.getElementById(id);row.style.display=row.style.display==='table-row'?'none':'table-row';}

function syncProviderId(input){var options=document.getElementById("pharmacyProviderChoices").options,id="",i;for(i=0;i<options.length;i++){if(options[i].value===input.value){id=options[i].getAttribute("data-provider-id");break;}}input.nextElementSibling.value=id;if(!id)input.setCustomValidity("Select a provider from the suggestions.");else input.setCustomValidity("");}
function addProviderRow(containerId,name,required){var box=document.getElementById(containerId),row=document.createElement('div');row.className='ph-team-row';row.innerHTML='<input type="text" list="pharmacyProviderChoices" class="ph-provider-search" placeholder="Type provider name" onchange="syncProviderId(this)" '+(required?'required':'')+'/><input type="hidden" name="'+name+'"/><button type="button" onclick="removeProviderRow(this)">Remove</button>';box.appendChild(row);row.querySelector('input').focus();}
function removeProviderRow(button){var row=button.parentNode,box=row.parentNode;if(box.id==='newReceivingTeam'&&box.children.length===1){row.querySelector('input[type=text]').value='';row.querySelector('input[type=hidden]').value='';}else box.removeChild(row);}
</script>
<div class="ph-page">
${ui.includeFragment("patienthandover","handoverNavigation",[activeTab:"pharmacy"])}
<% if(binding.hasVariable('pharmacyHandoverMessage')){%><div class="ph-alert-success">${pharmacyHandoverMessage}</div><%}%>
<% if(binding.hasVariable('pharmacyHandoverError')){%><div class="ph-alert-error">${pharmacyHandoverError}</div><%}%>
<datalist id="pharmacyProviderChoices"><%providers.each{p->if(!p.retired){%><option value="${p.name}" data-provider-id="${p.providerId}"></option><%}}%></datalist>

<form method="get" action="${ui.pageLink('patienthandover','pharmacyHandoverReport')}" class="ph-filter-bar ph-report-filters ph-pharmacy-date-link"><label>From <input type="date" name="fromDate"/></label><label>To <input type="date" name="toDate"/></label><button type="submit">View pharmacy handovers</button></form>
<h3 id="pending-pharmacy-handovers">Pending pharmacy team assignments</h3>
<% if(!handovers){%><p>No pending pharmacy handover signature is assigned to you at this login location.</p><%}else{%>
<div class="ph-table-scroll"><table class="ph-table"><thead><tr><th>Submitted</th><th>Age</th><th>Pharmacy</th><th>Shift</th><th>Items</th><th>Outgoing team</th><th>Receiving team</th><th>Action</th></tr></thead><tbody>
<%handovers.eachWithIndex{h,rowIndex-> def assigned=h.receivingProvider?.person?.personId==currentPersonId; def action=signatureActions[h.uuid]%>
<tr><td>${h.dateSubmitted?ui.formatDatePretty(h.dateSubmitted):''}</td><td>${ages[h.uuid]}</td><td>${h.pharmacyName}</td><td>${h.shift}</td><td>${h.items?.size()?:0}</td>
<td><%if(h.participants){h.participants.findAll{it.role=='OUTGOING'}.each{p->%><div>${p.person?.personName}  ${p.signed?'Signed':'Waiting'}</div><%}}else{%>${h.creator?.person?.personName?:h.creator?.username}<%}%><strong>${senderProgress[h.uuid]}</strong></td>
<td><%if(h.participants){h.participants.findAll{it.role=='RECEIVING'}.each{p->%><div>${p.person?.personName}  ${p.signed?'Received':'Waiting'}</div><%}}else{%>${h.receivingProvider?.name?:'Unassigned'}<%}%><strong>${receiverProgress[h.uuid]}</strong></td>
<td><button type="button" onclick="togglePendingItems('pending-items-${rowIndex}')">View items</button>
<%if(action){%><form method="post" class="ph-inline-form" onsubmit="return confirm('Record your ${action.toLowerCase()} team signature?')"><input type="hidden" name="signHandoverUuid" value="${h.uuid}"/><button type="submit">${action=='OUTGOING'?'Sign as sender':'Mark my receipt'}</button></form>
<%}else if(!h.participants && !h.acknowledged && canReceive && assigned){%><form method="post" class="ph-inline-form" onsubmit="return confirm('Mark this pharmacy handover as received?')"><input type="hidden" name="receiveHandoverUuid" value="${h.uuid}"/><button type="submit">Mark as received</button></form>
<%}else if(h.participants && !h.allSigned('OUTGOING')){%><span>Waiting for sending team</span><%}%>
<%def mayModify=h.creator?.userId==outgoingUser?.userId || canManagePharmacy; if(mayModify){%>
<div class="ph-action-buttons"><button type="button" onclick="document.getElementById('edit-handover-${rowIndex}').showModal()">Edit handover</button><button type="button" onclick="document.getElementById('edit-team-${rowIndex}').showModal()">Edit team members</button></div>
<dialog id="edit-handover-${rowIndex}" class="ph-edit-dialog ph-pharmacy-items-dialog"><form method="post"><input type="hidden" name="editPharmacyHandoverUuid" value="${h.uuid}"/><input type="hidden" name="editMode" value="handover"/><div class="ph-dialog-header"><h3>Edit handover</h3><button type="button" class="ph-dialog-close" onclick="this.closest('dialog').close()" aria-label="Close">&times;</button></div><p>Change the shift and choose the stock lines included in this pending handover.</p><label>Shift<select name="shift" required><option value="DAY" ${h.shift=='DAY'?'selected':''}>DAY</option><option value="NIGHT" ${h.shift=='NIGHT'?'selected':''}>NIGHT</option></select></label><fieldset class="ph-edit-stock-list"><legend>Selected stock items</legend><%def availableForEdit=editBalances[h.pharmacyLocationId]?:[]; if(!availableForEdit){%><p>No currently available stock lines were found for this pharmacy.</p><%}else{availableForEdit.each{b->def alreadySelected=h.items.any{it.inventoryBalanceId==b.inventoryBalanceId};%><label class="ph-edit-stock-option"><input type="checkbox" name="editBalanceIds" value="${b.inventoryBalanceId}" ${alreadySelected?'checked="checked"':''}/><span><strong>${b.item?.name?:''}</strong><small>Code: ${b.item?.code?:'-'} &nbsp; Batch: ${b.batchNumber?:'-'} &nbsp; Expiry: ${b.expiryDate?ui.formatDatePretty(b.expiryDate):'-'} &nbsp; Available: ${b.quantityOnHand}</small></span></label><%}}%></fieldset><label>Reason for change<textarea name="editReason" required maxlength="1000"></textarea></label><div class="ph-dialog-actions"><button type="button" class="cancel" onclick="this.closest('dialog').close()">Close</button><button type="submit">Save handover</button></div></form></dialog>
<dialog id="edit-team-${rowIndex}" class="ph-edit-dialog ph-team-dialog"><form method="post"><input type="hidden" name="editPharmacyHandoverUuid" value="${h.uuid}"/><input type="hidden" name="editMode" value="team"/><div class="ph-dialog-header"><h3>Edit team members</h3><button type="button" class="ph-dialog-close" onclick="this.closest('dialog').close()" aria-label="Close">&times;</button></div><p>Signed members remain in the handover audit record. Unsigned assignments can be replaced.</p><fieldset><legend>Sending team</legend><div id="editSenders-${rowIndex}"><%h.participants.findAll{it.role=='OUTGOING'}.each{p-> def provider=providers.find{it.person?.personId==p.person?.personId}; if(p.signed){%><div class="ph-team-row ph-signed-member"><span>${p.person?.personName}</span><strong>Signed</strong></div><%}else if(provider){%><div class="ph-team-row"><input type="text" list="pharmacyProviderChoices" value="${provider.name}" onchange="syncProviderId(this)" required/><input type="hidden" name="outgoingProviderIds" value="${provider.providerId}"/><button type="button" onclick="removeProviderRow(this)">Remove</button></div><%}}%></div><button type="button" onclick="addProviderRow('editSenders-${rowIndex}','outgoingProviderIds',false)">Add sender</button></fieldset><fieldset><legend>Receiving team</legend><div id="editReceivers-${rowIndex}"><%h.participants.findAll{it.role=='RECEIVING'}.each{p-> def provider=providers.find{it.person?.personId==p.person?.personId}; if(p.signed){%><div class="ph-team-row ph-signed-member"><span>${p.person?.personName}</span><strong>Signed</strong></div><%}else if(provider){%><div class="ph-team-row"><input type="text" list="pharmacyProviderChoices" value="${provider.name}" onchange="syncProviderId(this)" required/><input type="hidden" name="receivingProviderIds" value="${provider.providerId}"/><button type="button" onclick="removeProviderRow(this)">Remove</button></div><%}}%></div><button type="button" onclick="addProviderRow('editReceivers-${rowIndex}','receivingProviderIds',true)">Add receiver</button></fieldset><label>Reason for change<textarea name="editReason" required maxlength="1000"></textarea></label><div class="ph-dialog-actions"><button type="button" class="cancel" onclick="this.closest('dialog').close()">Close</button><button type="submit">Save team members</button></div></form></dialog>
<%}%>
<%if(mayModify){%><details class="ph-edit-panel"><summary>Cancel handover</summary><form method="post" onsubmit="return confirm('Cancel this pharmacy handover?')"><input type="hidden" name="cancelPharmacyHandoverUuid" value="${h.uuid}"/><label>Reason<input name="cancellationReason" required maxlength="1000"/></label><button class="ph-cancel-button">Cancel handover</button></form></details><%}%></td></tr>
<tr id="pending-items-${rowIndex}" class="ph-handover-detail-row" style="display:none"><td colspan="8"><div class="ph-handover-detail"><h4>Handed stock items</h4><table class="ph-table ph-compact-table"><thead><tr><th>Item</th><th>Code</th><th>Batch</th><th>Expiry date</th><th>Handed quantity</th></tr></thead><tbody>
<%h.items.each{i->%><tr><td>${i.itemName}</td><td>${i.itemCode?:'-'}</td><td>${i.batchNumber?:'-'}</td><td>${i.expiryDate?ui.formatDatePretty(i.expiryDate):'-'}</td><td><strong>${i.availableQuantity}</strong></td></tr><%}%>
</tbody></table></div></td></tr>
<%}%></tbody></table></div>
${ui.includeFragment("patienthandover","pagination",[pageName:"pharmacyHandover",pageParameter:"handoverPage",currentPage:pharmacyHandoverCurrentPage,totalPages:pharmacyHandoverTotalPages,totalItems:pharmacyHandoverTotalItems,itemLabel:"handover"])}
<%}%>

<%if(canCreate){%>
<p class="ph-location-card"><strong>Outgoing staff:</strong> ${outgoingUser?.person?.personName ?: outgoingUser?.username}</p>
<form method="get" class="ph-make-control-row" action="${ui.pageLink('patienthandover','pharmacyHandover')}">
<label><strong>Pharmacy location:</strong></label><select name="pharmacyLocationId" required><option value="">Select pharmacy</option>
<% locations.each{p->%><option value="${p.pharmacyLocationId}" ${selected?.pharmacyLocationId==p.pharmacyLocationId?'selected="selected"':''}>${p.name}${p.mainStore?' - Main Store':''}</option><%}%>
</select><input type="submit" value="Load Items"/></form>
<% if(selected!=null){%><h3>Available Stock at ${selected.name}</h3>
<% if(!balances){%><p>No available stock found.</p><%}else{%>
<form method="post" id="pharmacyHandoverForm" onsubmit="return confirmPharmacyHandover(this)">
<input type="hidden" name="pharmacyLocationId" value="${selected.pharmacyLocationId}"/>
<div class="ph-make-control-row"><div class="ph-shift-control"><label><strong>Shift:</strong></label><select name="shift" required><option value="">Select shift</option><option value="DAY">Day</option><option value="NIGHT">Night</option></select></div>
<fieldset class="ph-team-builder"><legend>Outgoing team</legend><p>Current logged-in user is included and signed automatically.</p><div id="newOutgoingTeam"></div><button type="button" onclick="addProviderRow('newOutgoingTeam','outgoingProviderIds',false)">Add sender</button></fieldset><fieldset class="ph-team-builder"><legend>Receiving team</legend><div id="newReceivingTeam"><div class="ph-team-row"><input type="text" list="pharmacyProviderChoices" class="ph-provider-search" placeholder="Type provider name" onchange="syncProviderId(this)" required/><input type="hidden" name="receivingProviderIds"/><button type="button" onclick="removeProviderRow(this)">Remove</button></div></div><button type="button" onclick="addProviderRow('newReceivingTeam','receivingProviderIds',true)">Add receiver</button></fieldset></div>
<section class="ph-pharmacy-selection"><h3>Selected stock items</h3><div id="pharmacyHandoverRows">
<div class="ph-pharmacy-item-row"><select name="handoverBalanceId" onchange="showStock(this)" required><option value="">Select item</option>
<%balances.each{b->%><option value="${b.inventoryBalanceId}" data-code="${b.item?.code?:''}" data-batch="${b.batchNumber?:''}" data-expiry="${b.expiryDate?ui.formatDatePretty(b.expiryDate):''}" data-quantity="${b.quantityOnHand}">${b.item?.name?:''}${b.batchNumber?' - '+b.batchNumber:''}</option><%}%></select>
<span class="stock-code"></span><span class="stock-batch"></span><span class="stock-expiry"></span><strong class="stock-quantity"></strong><button type="button" onclick="removeRow(this)">Remove</button></div>
</div><button type="button" onclick="addRow()">Add another item</button>
<button type="submit" name="submitMode" value="selected">Submit selected items</button>
<button type="submit" name="submitMode" value="all" formnovalidate>Submit all available items</button></section>
<details open><summary><strong>Available stock (${balances.size()} stock lines)</strong></summary>
<p><%if(showAll){%><a href="${ui.pageLink('patienthandover','pharmacyHandover',[pharmacyLocationId:selected.pharmacyLocationId])}">Show paginated items</a><%}else{%><a href="${ui.pageLink('patienthandover','pharmacyHandover',[pharmacyLocationId:selected.pharmacyLocationId,showAll:true])}">Show all items</a><%}%></p>
<table class="ph-table"><thead><tr><th>Item</th><th>Code</th><th>Batch</th><th>Expiry Date</th><th>Available Quantity</th></tr></thead><tbody>
<%displayedBalances.each{b->%><tr><td>${b.item?.name?:''}</td><td>${b.item?.code?:''}</td><td>${b.batchNumber?:''}</td><td>${b.expiryDate?ui.formatDatePretty(b.expiryDate):''}</td><td><strong>${b.quantityOnHand}</strong></td></tr><%}%>
</tbody></table>
<%if(!showAll){%>${ui.includeFragment("patienthandover","pagination",[pageName:"pharmacyHandover",pageParameter:"stockPage",currentPage:pharmacyStockCurrentPage,totalPages:pharmacyStockTotalPages,totalItems:pharmacyStockTotalItems,itemLabel:"stock line",parameters:[pharmacyLocationId:selected.pharmacyLocationId]])}<%}%>
</details></form><%}%><%}%>
<%}%></div>
<script>
function showStock(s){var o=s.options[s.selectedIndex],r=s.parentNode;r.querySelector('.stock-code').innerHTML=o.value?'Code: '+o.dataset.code:'';r.querySelector('.stock-batch').innerHTML=o.value?'Batch: '+o.dataset.batch:'';r.querySelector('.stock-expiry').innerHTML=o.value?'Expiry: '+o.dataset.expiry:'';r.querySelector('.stock-quantity').innerHTML=o.value?'Available: '+o.dataset.quantity:'';}
function addRow(){var c=document.getElementById('pharmacyHandoverRows'),r=c.firstElementChild.cloneNode(true);r.querySelector('select').selectedIndex=0;r.querySelectorAll('span,strong').forEach(function(x){x.innerHTML=''});c.appendChild(r);}
function removeRow(b){var c=document.getElementById('pharmacyHandoverRows');if(c.children.length>1)c.removeChild(b.parentNode);else{b.parentNode.querySelector('select').selectedIndex=0;showStock(b.parentNode.querySelector('select'));}}
function confirmPharmacyHandover(f){var m=document.activeElement.value;if(m==='selected'&&!f.querySelector('select[name=handoverBalanceId]').value){alert('Select at least one item.');return false;}return confirm(m==='all'?'Submit all available pharmacy items?':'Submit the selected pharmacy items?');}
</script>
