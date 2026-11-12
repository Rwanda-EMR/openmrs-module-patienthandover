<% ui.decorateWith("appui","standardEmrPage"); ui.includeCss("patienthandover","patienthandover.css"); def items=binding.hasVariable('equipmentItems')?equipmentItems:[] %>
<script>var breadcrumbs=[{icon:"icon-home",link:'/'+OPENMRS_CONTEXT_PATH+'/index.htm'},{label:"Equipment Items"}];</script>
<div class="ph-page">${ui.includeFragment("patienthandover","handoverNavigation",[activeTab:"equipmentItems"])}
<h2>Equipment Items</h2>
<p class="ph-location-card">
<strong>Login location:</strong> ${currentLocation?.name?:'Select a location'}</p>
<%if(binding.hasVariable('equipmentMessage')){%>
<div class="ph-alert-success">${equipmentMessage}</div>
<%}%>
<%if(binding.hasVariable('equipmentError')){%>
<div class="ph-alert-error">${equipmentError}</div>
<%}%>
<section class="ph-equipment-admin">
<h3>Add or upload equipment</h3>
<form action="${ui.pageLink('patienthandover','equipmentHandover')}" method="post" enctype="multipart/form-data" class="ph-equipment-add-form">
<input type="hidden" name="action" value="addItem"/>
<input type="hidden" name="returnPage" value="equipmentItems"/>
<label>Equipment name<input name="equipmentName"/>
</label>
<label>Quantity<input type="number" step="0.01" min="0" name="quantity"/>
</label>
<label>Status<select name="equipmentStatus">
<option value="">Select status</option>
<option>FUNCTIONAL</option>
<option>NEEDS MAINTENANCE</option>
<option>OUT OF SERVICE</option>
</select>
</label>
<label>Observation<input name="observation"/>
</label>
<button type="submit">Add item</button>
<label>Upload CSV<input type="file" name="equipmentCsv" accept=".csv"/>
</label>
<button type="submit">Upload CSV</button>
<a class="ph-button-link" href="${ui.resourceLink('patienthandover','templates/equipment-items-template.csv')}">Download template</a>
</form>
</section>
<div class="ph-table-scroll">
<table class="ph-table ph-compact-table ph-equipment-catalog-table">
<thead>
<tr>
<th>Equipment</th>
<th>Quantity</th>
<th>Status</th>
<th>Observation</th>
<th>Action</th>
</tr>
</thead>
<tbody>
<%if(!items){%>
<tr>
<td colspan="5">No equipment configured.</td>
</tr>
<%}else{items.each{i->%>
<tr>
<td>${i.equipmentName}</td>
<td>${i.quantity}</td>
<td>${i.equipmentStatus}</td>
<td>${i.observation?:'-'}</td>
<td>
<form action="${ui.pageLink('patienthandover','equipmentHandover')}" method="post" class="ph-inline-form">
<input type="hidden" name="action" value="removeItem"/>
<input type="hidden" name="returnPage" value="equipmentItems"/>
<input type="hidden" name="equipmentItemId" value="${i.id}"/>
<button type="submit" class="ph-icon-action ph-icon-remove" title="Remove equipment" aria-label="Remove equipment" onclick="return confirm('Remove this equipment from future handovers?')"><i class="icon-trash"></i></button>
</form>
</td>
</tr>
<%}}%>
</tbody>
</table>
</div>
</div>
