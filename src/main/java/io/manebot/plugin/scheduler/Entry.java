package io.manebot.plugin.scheduler;

import io.manebot.database.Database;
import io.manebot.plugin.Plugin;
import io.manebot.plugin.PluginException;
import io.manebot.plugin.PluginType;
import io.manebot.plugin.java.PluginEntry;
import io.manebot.plugin.scheduler.command.ScheduleCommand;
import io.manebot.plugin.scheduler.database.ScheduledCommand;

public class Entry implements PluginEntry {
    @Override
    public void instantiate(Plugin.Builder builder) {
        builder.setType(PluginType.FEATURE);
        Database database = builder.addDatabase("scheduler", (databaseBuilder) -> {
            databaseBuilder.addDependency(databaseBuilder.getSystemDatabase());
            databaseBuilder.registerEntity(ScheduledCommand.class);
        });
        builder.setInstance(Scheduler.class, (plugin) -> new Scheduler(plugin, database));
        builder.addCommand("schedule", (future) ->
                new ScheduleCommand(future.getPlugin().getInstance(Scheduler.class), database));
    }
}
