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
package com.djrapitops.plan;

import cn.nukkit.Server;
import com.djrapitops.plan.delivery.webserver.cache.JSONCache;
import com.djrapitops.plan.extension.ExtensionServerMethodCallerTask;
import com.djrapitops.plan.gathering.ShutdownHook;
import com.djrapitops.plan.gathering.timed.NukkitPingCounter;
import com.djrapitops.plan.gathering.timed.NukkitTPSCounter;
import com.djrapitops.plan.settings.config.PlanConfig;
import com.djrapitops.plan.settings.config.paths.DataGatheringSettings;
import com.djrapitops.plan.settings.config.paths.TimeSettings;
import com.djrapitops.plan.settings.upkeep.ConfigStoreTask;
import com.djrapitops.plan.storage.upkeep.DBCleanTask;
import com.djrapitops.plan.storage.upkeep.LogsFolderCleanTask;
import com.djrapitops.plugin.api.TimeAmount;
import com.djrapitops.plugin.task.RunnableFactory;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

/**
 * TaskSystem responsible for registering tasks for Nukkit.
 *
 * @author Rsl1122
 */
@Singleton
public class NukkitTaskSystem extends TaskSystem {

    private final PlanNukkit plugin;
    private final PlanConfig config;
    private final ShutdownHook shutdownHook;
    private final JSONCache.CleanTask jsonCacheCleanTask;
    private final LogsFolderCleanTask logsFolderCleanTask;
    private final NukkitPingCounter pingCounter;
    private final ConfigStoreTask configStoreTask;
    private final DBCleanTask dbCleanTask;
    private final ExtensionServerMethodCallerTask extensionServerMethodCallerTask;
    private NukkitTPSCounter tpsCounter;

    @Inject
    public NukkitTaskSystem(
            PlanNukkit plugin,
            PlanConfig config,
            ShutdownHook shutdownHook,
            RunnableFactory runnableFactory,

            NukkitTPSCounter tpsCounter,
            NukkitPingCounter pingCounter,
            ExtensionServerMethodCallerTask extensionServerMethodCallerTask,

            LogsFolderCleanTask logsFolderCleanTask,
            ConfigStoreTask configStoreTask,
            DBCleanTask dbCleanTask,
            JSONCache.CleanTask jsonCacheCleanTask
    ) {
        super(runnableFactory);
        this.plugin = plugin;
        this.config = config;
        this.shutdownHook = shutdownHook;
        this.jsonCacheCleanTask = jsonCacheCleanTask;

        this.tpsCounter = tpsCounter;
        this.pingCounter = pingCounter;
        this.extensionServerMethodCallerTask = extensionServerMethodCallerTask;

        this.logsFolderCleanTask = logsFolderCleanTask;
        this.configStoreTask = configStoreTask;
        this.dbCleanTask = dbCleanTask;
    }

    @Override
    public void enable() {
        registerTPSCounter();
        registerPingCounter();
        registerExtensionDataGatheringTask();
        registerUpkeepTasks();

        shutdownHook.register();
    }

    private void registerUpkeepTasks() {
        // +40 ticks / 2 seconds so that update check task runs first.
        long storeDelay = TimeAmount.toTicks(config.get(TimeSettings.CONFIG_UPDATE_INTERVAL), TimeUnit.MILLISECONDS) + 40;
        registerTask(configStoreTask).runTaskLaterAsynchronously(storeDelay);
        registerTask(logsFolderCleanTask).runTaskLaterAsynchronously(TimeAmount.toTicks(30L, TimeUnit.SECONDS));
        registerTask(dbCleanTask).runTaskTimerAsynchronously(
                TimeAmount.toTicks(20, TimeUnit.SECONDS),
                TimeAmount.toTicks(config.get(TimeSettings.CLEAN_DATABASE_PERIOD), TimeUnit.MILLISECONDS)
        );
        long minute = TimeAmount.toTicks(1, TimeUnit.MINUTES);
        registerTask(jsonCacheCleanTask).runTaskTimerAsynchronously(minute, minute);
    }

    private void registerTPSCounter() {
        registerTask(tpsCounter).runTaskTimer(1000, TimeAmount.toTicks(1L, TimeUnit.SECONDS));
    }

    private void registerPingCounter() {
        Long pingDelay = config.get(TimeSettings.PING_SERVER_ENABLE_DELAY);
        if (pingDelay < TimeUnit.HOURS.toMillis(1L) && config.get(DataGatheringSettings.PING)) {
            plugin.registerListener(pingCounter);
            long startDelay = TimeAmount.toTicks(pingDelay, TimeUnit.MILLISECONDS);
            registerTask(pingCounter).runTaskTimer(startDelay, 40L);
        }
    }

    private void registerExtensionDataGatheringTask() {
        long extensionRefreshPeriod = TimeAmount.toTicks(config.get(TimeSettings.EXTENSION_DATA_REFRESH_PERIOD), TimeUnit.MILLISECONDS);
        registerTask(extensionServerMethodCallerTask).runTaskTimerAsynchronously(
                TimeAmount.toTicks(30, TimeUnit.SECONDS), extensionRefreshPeriod
        );
    }

    @Override
    public void disable() {
        super.disable();
        Optional.ofNullable(Server.getInstance().getScheduler()).ifPresent(scheduler -> scheduler.cancelTask(plugin));
    }
}
