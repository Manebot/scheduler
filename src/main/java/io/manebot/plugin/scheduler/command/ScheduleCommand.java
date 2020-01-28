package io.manebot.plugin.scheduler.command;

import io.manebot.chat.TextStyle;
import io.manebot.command.CommandSender;
import io.manebot.command.exception.CommandArgumentException;
import io.manebot.command.exception.CommandExecutionException;
import io.manebot.command.executor.chained.AnnotatedCommandExecutor;
import io.manebot.command.executor.chained.argument.CommandArgumentFollowing;
import io.manebot.command.executor.chained.argument.CommandArgumentInterval;
import io.manebot.command.executor.chained.argument.CommandArgumentLabel;
import io.manebot.command.executor.chained.argument.CommandArgumentPage;
import io.manebot.command.response.CommandListResponse;
import io.manebot.command.search.CommandArgumentSearch;
import io.manebot.database.Database;
import io.manebot.database.search.Search;
import io.manebot.database.search.SearchHandler;
import io.manebot.database.search.SearchOperator;
import io.manebot.database.search.handler.ComparingSearchHandler;
import io.manebot.database.search.handler.SearchHandlerPropertyContains;
import io.manebot.database.search.handler.SearchHandlerPropertyEquals;
import io.manebot.plugin.scheduler.Scheduler;
import io.manebot.plugin.scheduler.database.ScheduledCommand;

import java.sql.SQLException;
import java.util.Collection;
import java.util.EnumSet;

public class ScheduleCommand extends AnnotatedCommandExecutor {
    private static final CommandListResponse.ListElementFormatter<ScheduledCommand> formatter =
            (textBuilder, command) -> textBuilder.append(command.getCommand(), EnumSet.of(TextStyle.ITALICS))
                    .append(" (run by " + command.getUser().getDisplayName() + "," +
                            " " + command.getInterval() + "sec)");

    private final Scheduler scheduler;
    private final SearchHandler<ScheduledCommand> searchHandler;

    public ScheduleCommand(Scheduler scheduler, Database database) {
        this.scheduler = scheduler;

        this.searchHandler = database.createSearchHandler(ScheduledCommand.class)
                .string(new SearchHandlerPropertyContains("command"))
                .argument("user", new ComparingSearchHandler(
                        new SearchHandlerPropertyEquals(root -> root.get("user").get("displayName")),
                        new SearchHandlerPropertyEquals(root -> root.get("user").get("username")),
                        SearchOperator.INCLUDE))
                .sort("command", "command")
                .defaultSort("command")
                .build();
    }

    @Command(description = "Searches scheduled commands", permission = "system.scheduler.search")
    public void search(CommandSender sender,
                       @CommandArgumentLabel.Argument(label = "search") String search,
                       @CommandArgumentSearch.Argument Search query)
            throws CommandExecutionException {
        try {
            sender.sendList(ScheduledCommand.class, searchHandler.search(query, sender.getChat().getDefaultPageSize()),
                    formatter);
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    @Command(description = "Lists scheduled commands", permission = "system.scheduler.list")
    public void list(CommandSender sender,
                     @CommandArgumentLabel.Argument(label = "list") String list,
                     @CommandArgumentPage.Argument int page)
            throws CommandExecutionException {
        sender.sendList(
                ScheduledCommand.class,
                builder -> builder.direct(scheduler.getCommands()).page(page).responder(formatter).build()
        );
    }

    @Command(description = "Adds a scheduled command", permission = "system.scheduler.add")
    public void add(CommandSender sender,
                    @CommandArgumentLabel.Argument(label = "add") String add,
                    @CommandArgumentInterval.Argument double seconds,
                    @CommandArgumentFollowing.Argument String command) throws CommandExecutionException {
        try {
            scheduler.schedule(sender.getUser(), command, seconds);
        } catch (SQLException e) {
            throw new CommandExecutionException(e);
        }

        sender.sendMessage("Command scheduled.");
    }

    @Command(description = "Removes a scheduled command", permission = "system.scheduler.remove")
    public void remove(CommandSender sender,
                        @CommandArgumentLabel.Argument(label = "remove") String remove,
                        @CommandArgumentFollowing.Argument String command) throws CommandExecutionException {
        Collection<ScheduledCommand> removed;

        try {
            removed = scheduler.remove(command);
        } catch (SQLException e) {
            throw new CommandExecutionException(e);
        }

        if (removed.size() <= 0)
            throw new CommandArgumentException("Scheduled commands not found.");

        sender.sendMessage(removed.size() + " scheduled command(s) removed.");
    }
}