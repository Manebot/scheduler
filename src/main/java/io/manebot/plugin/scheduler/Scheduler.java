package io.manebot.plugin.scheduler;

import io.manebot.chat.BasicTextChatMessage;
import io.manebot.chat.ChatMessage;
import io.manebot.command.CommandMessage;
import io.manebot.command.CommandSender;
import io.manebot.command.DefaultCommandSender;
import io.manebot.conversation.Conversation;
import io.manebot.database.Database;
import io.manebot.plugin.Plugin;
import io.manebot.plugin.PluginReference;
import io.manebot.plugin.scheduler.database.ScheduledCommand;
import io.manebot.user.User;
import io.manebot.virtual.Virtual;

import java.sql.SQLException;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Level;
import java.util.stream.Collectors;

public class Scheduler implements PluginReference, Runnable {
    private final Plugin plugin;
    private final Database database;
    private final Object lock = new Object();

    private ExecutorService executorService;

    public Scheduler(Plugin plugin, Database database) {
        this.plugin = plugin;
        this.database = database;
    }

    public List<ScheduledCommand> getCommands() {
        return database.execute(s -> {
            return s.createQuery(
                    "SELECT x FROM " + ScheduledCommand.class.getName() + " x", ScheduledCommand.class)
                    .getResultStream()
                    .collect(Collectors.toList());
        });
    }

    public int getNextCommandDueTime() {
        return database.execute(s -> {
            return s.createQuery(
                    "SELECT x.nextRun FROM " + ScheduledCommand.class.getName() + " x " +
                            "ORDER BY x.nextRun", Integer.class)
                    .setMaxResults(1)
                    .getResultStream()
                    .findFirst()
                    .orElse(Integer.MAX_VALUE);
        });
    }

    public Collection<ScheduledCommand> getCommandsDue(int time) {
        return database.execute(s -> {
            return s.createQuery(
                    "SELECT x FROM " + ScheduledCommand.class.getName() + " x " +
                            "WHERE x.nextRun <= :now", ScheduledCommand.class)
                    .setParameter("now", time)
                    .getResultStream()
                    .collect(Collectors.toList());
        });
    }

    @Override
    public void load(Plugin.Future future) {
        executorService = Executors.newCachedThreadPool(Virtual.getInstance());
        future.after((registration) -> executorService.submit(Scheduler.this));
    }

    @Override
    public void unload(Plugin.Future future) {
        executorService.shutdown();
    }

    private static int now() {
        return Math.toIntExact(System.currentTimeMillis() / 1000L);
    }

    @Override
    public void run() {
        try {
            synchronized (lock) {
                while (plugin.isEnabled()) {
                    int now = now();
                    int due = getNextCommandDueTime();
                    int wait = due - now;
                    if (wait > 0)
                        lock.wait(wait * 1000L);

                    getCommandsDue(now()).forEach(command -> {
                        int next = command.getNextRun() + command.getInterval();
                        next = Math.max(now - command.getInterval(), next); // prevent drift
                        command.setNextRun(next);

                        executorService.submit(() -> {
                            Conversation conversation = plugin.getBot().getConversationProvider().getNullConversation();
                            CommandSender sender = new DefaultCommandSender(conversation, null, command.getUser());
                            ChatMessage chatMessage = new BasicTextChatMessage(sender, command.getCommand());
                            CommandMessage commandMessage = new CommandMessage(chatMessage, sender);

                            try {
                                plugin.getLogger().log(Level.FINE, "Executing scheduled command: " + commandMessage);
                                plugin.getBot().getCommandDispatcher().execute(commandMessage);
                            } catch (Throwable ex) {
                                plugin.getLogger().log(Level.WARNING, "Problem executing scheduled command \"" +
                                        command.getCommand() + "\"", ex);
                            }
                        });
                    });
                }
            }
        } catch (InterruptedException ex) {
            // softly exit
        } catch (Throwable ex) {
            plugin.getLogger().log(Level.SEVERE, "Problem in command scheduler thread", ex);
        }
    }

    public ScheduledCommand schedule(User user, String command, double seconds) throws SQLException {
        ScheduledCommand result = database.executeTransaction((s) -> {
            ScheduledCommand scheduledCommand = new ScheduledCommand(database, user, command, (int) seconds);
            s.persist(scheduledCommand);
            return scheduledCommand;
        });

        synchronized (lock) {
            lock.notifyAll();
        }

        return result;
    }

    public Collection<ScheduledCommand> remove(String command) throws SQLException {
        Collection<ScheduledCommand> commands = database.execute(s -> {
            return s.createQuery(
                    "SELECT x FROM " + ScheduledCommand.class.getName() + " x " +
                            "WHERE x.command = :command", ScheduledCommand.class)
                    .setParameter("command", command)
                    .getResultStream()
                    .collect(Collectors.toList());
        });

        database.executeTransaction(s -> {
            commands.forEach(s::remove);
        });

        return commands;
    }
}
