<%
def currentPage = config.currentPage ?: 1
def totalPages = config.totalPages ?: 1
def totalItems = config.totalItems ?: 0
def pageParameter = config.pageParameter ?: "page"
def parameters = new LinkedHashMap(config.parameters ?: [:])
%>
<% if (totalPages > 1) { %>
<nav class="ph-pagination" aria-label="Pagination">
    <span class="ph-pagination-summary">${ totalItems } item(s) &mdash; page ${ currentPage } of ${ totalPages }</span>
    <% if (currentPage > 1) { parameters[pageParameter] = 1 %>
        <a class="ph-page-link" href="${ ui.pageLink('patienthandover', config.pageName, parameters) }">First</a>
    <% } %>
    <% if (currentPage > 1) { parameters[pageParameter] = currentPage - 1 %>
        <a class="ph-page-link" href="${ ui.pageLink('patienthandover', config.pageName, parameters) }">&laquo; Previous</a>
    <% } %>
    <%
    int firstPage = Math.max(1, currentPage - 2)
    int lastPage = Math.min(totalPages, currentPage + 2)
    for (int number = firstPage; number <= lastPage; number++) {
        parameters[pageParameter] = number
    %>
        <% if (number == currentPage) { %><strong class="ph-page-current">${ number }</strong>
        <% } else { %><a class="ph-page-link" href="${ ui.pageLink('patienthandover', config.pageName, parameters) }">${ number }</a><% } %>
    <% } %>
    <% if (currentPage < totalPages) { parameters[pageParameter] = currentPage + 1 %>
        <a class="ph-page-link" href="${ ui.pageLink('patienthandover', config.pageName, parameters) }">Next &raquo;</a>
    <% } %>
    <% if (currentPage < totalPages) { parameters[pageParameter] = totalPages %>
        <a class="ph-page-link" href="${ ui.pageLink('patienthandover', config.pageName, parameters) }">Last</a>
    <% } %>
</nav>
<% } %>
