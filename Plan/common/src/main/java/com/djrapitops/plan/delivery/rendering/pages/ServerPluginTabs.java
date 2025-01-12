/*
 *  This file is part of Player Analytics (Plan).
 *
 *  Plan is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU Lesser General Public License v3 as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  Plan is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU Lesser General Public License for more details.
 *
 *  You should have received a copy of the GNU Lesser General Public License
 *  along with Plan. If not, see <https://www.gnu.org/licenses/>.
 */
package com.djrapitops.plan.delivery.rendering.pages;

import com.djrapitops.plan.delivery.formatting.Formatter;
import com.djrapitops.plan.delivery.formatting.Formatters;
import com.djrapitops.plan.delivery.rendering.html.icon.Icon;
import com.djrapitops.plan.delivery.rendering.html.structure.NavLink;
import com.djrapitops.plan.delivery.rendering.html.structure.TabsElement;
import com.djrapitops.plan.extension.ElementOrder;
import com.djrapitops.plan.extension.FormatType;
import com.djrapitops.plan.extension.implementation.TabInformation;
import com.djrapitops.plan.extension.implementation.results.*;
import com.djrapitops.plugin.utilities.Format;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Responsible for generating /server page plugin tabs based on DataExtension API data.
 * <p>
 * Currently very similar to {@link PlayerPluginTab}.
 * This will become more complex once tables are added, since some big tables will be moved to their own tabs.
 *
 * @author Rsl1122
 */
public class ServerPluginTabs {

    private List<ExtensionData> serverData;
    private List<ExtensionData> extraTabServerData;

    private Map<FormatType, Formatter<Long>> numberFormatters;

    private Formatter<Double> decimalFormatter;
    private Formatter<Double> percentageFormatter;

    private StringBuilder nav;
    private String tab;

    public ServerPluginTabs(String nav, String tab) {
        this.nav = new StringBuilder(nav);
        this.tab = tab;
    }

    public ServerPluginTabs(
            List<ExtensionData> serverData,
            Formatters formatters
    ) {
        this.serverData = serverData;
        this.extraTabServerData = serverData.stream()
                .filter(ExtensionData::doesNeedWiderSpace)
                .collect(Collectors.toList());
        this.serverData.removeAll(extraTabServerData);

        numberFormatters = new EnumMap<>(FormatType.class);
        numberFormatters.put(FormatType.DATE_SECOND, formatters.secondLong());
        numberFormatters.put(FormatType.DATE_YEAR, formatters.yearLong());
        numberFormatters.put(FormatType.TIME_MILLISECONDS, formatters.timeAmount());
        numberFormatters.put(FormatType.NONE, Object::toString);

        this.decimalFormatter = formatters.decimals();
        this.percentageFormatter = formatters.percentage();

        generate();
    }

    public String getNav() {
        return nav.toString();
    }

    public String getTabs() {
        return tab;
    }

    private void generate() {
        if (serverData.isEmpty()) {
            String tabName = "Overview (No Data)";
            nav = new StringBuilder(new NavLink(Icon.called("cubes").build(), tabName).toHtml());
            tab = wrapInTab(
                    tabName,
                    "<div class=\"col-md-12\"><div class=\"card\"><div class=\"card-body\"><p>No Extension Data</p></div></div></div>"
            );
        } else {
            nav = new StringBuilder(new NavLink(Icon.called("cubes").build(), "Overview").toHtml());
            tab = generatePageTabs();
        }
    }

    private String generatePageTabs() {
        Collections.sort(serverData);

        String overviewTab = generateOverviewTab();
        String extraTabs = generateExtraTabs();

        return overviewTab + extraTabs;
    }

    private String generateExtraTabs() {
        StringBuilder tabBuilder = new StringBuilder();

        for (ExtensionData datum : extraTabServerData) {
            ExtensionInformation extensionInformation = datum.getExtensionInformation();

            boolean onlyGeneric = datum.hasOnlyGenericTab();

            String tabsElement;
            if (onlyGeneric) {
                ExtensionTabData genericTabData = datum.getTabs().get(0);
                tabsElement = buildContentHtml(genericTabData);
            } else {
                tabsElement = new TabsElement(
                        datum.getTabs().stream().map(this::wrapToTabElementTab).toArray(TabsElement.Tab[]::new)
                ).toHtmlFull();
            }

            tabBuilder.append(wrapInTab(extensionInformation.getPluginName(), wrapInContainer(extensionInformation, tabsElement)));
            nav.append(new NavLink(Icon.fromExtensionIcon(extensionInformation.getIcon()), extensionInformation.getPluginName()).toHtml());
        }
        return tabBuilder.toString();
    }

    private String generateOverviewTab() {
        StringBuilder tabBuilder = new StringBuilder();

        for (ExtensionData datum : serverData) {
            ExtensionInformation extensionInformation = datum.getExtensionInformation();

            boolean onlyGeneric = datum.hasOnlyGenericTab();

            String tabsElement;
            if (onlyGeneric) {
                ExtensionTabData genericTabData = datum.getTabs().get(0);
                tabsElement = buildContentHtml(genericTabData);
            } else {
                tabsElement = new TabsElement(
                        datum.getTabs().stream().map(this::wrapToTabElementTab).toArray(TabsElement.Tab[]::new)
                ).toHtmlFull();
            }

            tabBuilder.append(wrapInContainer(extensionInformation, tabsElement));
        }

        return wrapInTab("Overview", tabBuilder.toString());
    }

    private String wrapInTab(String tabName, String content) {
        return "<div class=\"tab\" id=\"" + new Format(tabName).justLetters().lowerCase() + "\"><div class=\"container-fluid mt-4\">" +
                // Page heading
                "<div class=\"d-sm-flex align-items-center justify-content-between mb-4\">" +
                "<h1 class=\"h3 mb-0 text-gray-800\"><i class=\"sidebar-toggler fa fa-fw fa-bars\"></i><span class=\"server-name\"></span> &middot; Plugins Overview</h1>${backButton}" +
                "</div>" +
                // End Page heading
                "<div class=\"card-columns\">" + content + "</div></div></div>";
    }

    private TabsElement.Tab wrapToTabElementTab(ExtensionTabData tabData) {
        TabInformation tabInformation = tabData.getTabInformation();
        String tabContentHtml = buildContentHtml(tabData);

        String tabName = tabInformation.getTabName();
        return new TabsElement.Tab(tabName.isEmpty()
                ? Icon.called("info-circle").build().toHtml() + " General"
                : Icon.fromExtensionIcon(tabInformation.getTabIcon()).toHtml() + ' ' + tabName,
                tabContentHtml);
    }

    private String buildContentHtml(ExtensionTabData tabData) {
        TabInformation tabInformation = tabData.getTabInformation();

        ElementOrder[] order = tabInformation.getTabElementOrder().orElse(ElementOrder.values());
        String values = buildValuesHtml(tabData);
        String valuesHtml = values.isEmpty() ? "" : "<div class=\"card-body\">" + values + "</div>";
        String tablesHtml = buildTablesHtml(tabData);

        StringBuilder builder = new StringBuilder();

        for (ElementOrder ordering : order) {
            switch (ordering) {
                case VALUES:
                    builder.append(valuesHtml);
                    break;
                case TABLE:
                    builder.append(tablesHtml);
                    break;
                default:
                    break;
            }
        }

        return builder.toString();
    }

    private String buildTablesHtml(ExtensionTabData tabData) {
        StringBuilder builder = new StringBuilder();
        for (ExtensionTableData tableData : tabData.getTableData()) {
            builder.append(tableData.getHtmlTable().buildHtml());
        }
        return builder.toString();
    }

    private String buildValuesHtml(ExtensionTabData tabData) {
        StringBuilder builder = new StringBuilder();
        for (String key : tabData.getValueOrder()) {
            tabData.getBoolean(key).ifPresent(data -> append(builder, data.getDescriptive(), data.getFormattedValue()));
            tabData.getDouble(key).ifPresent(data -> append(builder, data.getDescriptive(), data.getFormattedValue(decimalFormatter)));
            tabData.getPercentage(key).ifPresent(data -> append(builder, data.getDescriptive(), data.getFormattedValue(percentageFormatter)));
            tabData.getNumber(key).ifPresent(data -> append(builder, data.getDescriptive(), data.getFormattedValue(numberFormatters.get(data.getFormatType()))));
            tabData.getString(key).ifPresent(data -> append(builder, data.getDescriptive(), data.getFormattedValue()));
        }
        return builder.toString();
    }

    private void append(StringBuilder builder, ExtensionDescriptive descriptive, String formattedValue) {
        Optional<String> description = descriptive.getDescription();
        if (description.isPresent()) {
            builder.append("<p title=\"").append(description.get()).append("\">");
        } else {
            builder.append("<p>");
        }
        builder.append(Icon.fromExtensionIcon(descriptive.getIcon()))
                .append(' ').append(descriptive.getText()).append("<span class=\"float-right\"><b>").append(formattedValue).append("</b></span></p>");
    }

    private String wrapInContainer(ExtensionInformation information, String tabsElement) {
        return "<div class=\"card shadow mb-4\">" +
                "<div class=\"card-header py-3\">" +
                "<h6 class=\"m-0 font-weight-bold col-black\">" + Icon.fromExtensionIcon(information.getIcon()) + ' ' + information.getPluginName() + "</h6>" +
                "</div>" +
                tabsElement +
                "</div>";
    }
}