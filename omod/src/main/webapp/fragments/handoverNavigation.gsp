<%
    def activeTab = config.activeTab ?: "dashboard"
%>

<div class="ph-navigation">
    <ul class="ph-nav-tabs">
        <li class="${activeTab == 'dashboard' ? 'active' : ''}">
            <a href="${ui.pageLink("patienthandover", "handoverDashboard")}">
                ${ ui.message("patienthandover.dashboard.title") }
            </a>
        </li>
        <li class="${activeTab == 'make' ? 'active' : ''}">
            <a href="${ui.pageLink("patienthandover", "locationHandover")}">
                ${ ui.message("patienthandover.make.title") }
            </a>
        </li>
        <li class="${activeTab == 'saved' ? 'active' : ''}">
            <a href="${ui.pageLink("patienthandover", "savedHandovers")}">
                ${ ui.message("patienthandover.saved.title") }
            </a>
        </li>
        <li class="${activeTab == 'trolley' ? 'active' : ''}">
            <a href="${ui.pageLink("patienthandover", "emergencyTrolley")}">Make Emergency Trolley Handover</a>
        </li>
        <li class="${activeTab == 'trolleyItems' ? 'active' : ''}">
            <a href="${ui.pageLink("patienthandover", "emergencyTrolleyItems")}">Emergency Trolley Items</a>
        </li>        <li class="${activeTab == 'trolleyReport' ? 'active' : ''}">
            <a href="${ui.pageLink("patienthandover", "emergencyTrolleyReport")}">Trolley Report</a>
        </li>        <li class="${activeTab == 'audit' ? 'active' : ''}">
            <a href="${ui.pageLink("patienthandover", "handoverReport")}">
                ${ ui.message("patienthandover.audit.title") }
            </a>
        </li>
    </ul>
</div>