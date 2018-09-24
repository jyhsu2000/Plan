/*
 * License is provided in the jar as LICENSE also here:
 * https://github.com/Rsl1122/Plan-PlayerAnalytics/blob/master/Plan/src/main/resources/LICENSE
 */
package com.djrapitops.plan.utilities.html.pages;

import com.djrapitops.plan.api.exceptions.ParseException;
import com.djrapitops.plan.data.store.containers.NetworkContainer;
import com.djrapitops.plan.data.store.keys.NetworkKeys;
import com.djrapitops.plan.system.file.PlanFiles;
import com.djrapitops.plan.system.info.server.properties.ServerProperties;
import com.djrapitops.plan.system.webserver.cache.PageId;
import com.djrapitops.plan.system.webserver.cache.ResponseCache;
import com.djrapitops.plan.system.webserver.response.pages.parts.NetworkPageContent;
import com.djrapitops.plan.utilities.formatting.PlaceholderReplacer;

import static com.djrapitops.plan.data.store.keys.NetworkKeys.*;

/**
 * Html String parser for /network page.
 *
 * @author Rsl1122
 */
public class NetworkPage implements Page {

    private final NetworkContainer networkContainer;

    private final PlanFiles planFiles;
    private final ServerProperties serverProperties;

    public NetworkPage(
            NetworkContainer networkContainer,
            PlanFiles planFiles,
            ServerProperties serverProperties
    ) {
        this.networkContainer = networkContainer;
        this.planFiles = planFiles;
        this.serverProperties = serverProperties;
    }

    @Override
    public String toHtml() throws ParseException {
        try {
            networkContainer.putSupplier(NetworkKeys.PLAYERS_ONLINE, serverProperties::getOnlinePlayers);

            PlaceholderReplacer placeholderReplacer = new PlaceholderReplacer();
            placeholderReplacer.addAllPlaceholdersFrom(networkContainer,
                    VERSION, NETWORK_NAME, TIME_ZONE,
                    PLAYERS_ONLINE, PLAYERS_ONLINE_SERIES, PLAYERS_TOTAL, PLAYERS_GRAPH_COLOR,
                    REFRESH_TIME_F, RECENT_PEAK_TIME_F, ALL_TIME_PEAK_TIME_F,
                    PLAYERS_ALL_TIME_PEAK, PLAYERS_RECENT_PEAK,
                    PLAYERS_DAY, PLAYERS_WEEK, PLAYERS_MONTH,
                    PLAYERS_NEW_DAY, PLAYERS_NEW_WEEK, PLAYERS_NEW_MONTH,
                    WORLD_MAP_SERIES, WORLD_MAP_HIGH_COLOR, WORLD_MAP_LOW_COLOR,
                    COUNTRY_CATEGORIES, COUNTRY_SERIES,
                    HEALTH_INDEX, HEALTH_NOTES,
                    ACTIVITY_PIE_SERIES, ACTIVITY_STACK_SERIES, ACTIVITY_STACK_CATEGORIES
            );
            NetworkPageContent networkPageContent = (NetworkPageContent)
                    ResponseCache.loadResponse(PageId.NETWORK_CONTENT.id(), NetworkPageContent::new);
            placeholderReplacer.put("tabContentServers", networkPageContent.getContents());

            return placeholderReplacer.apply(planFiles.readCustomizableResourceFlat("web/network.html"));
        } catch (Exception e) {
            throw new ParseException(e);
        }
    }
}