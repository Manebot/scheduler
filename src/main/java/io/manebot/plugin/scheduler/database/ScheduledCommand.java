package io.manebot.plugin.scheduler.database;

import io.manebot.database.Database;
import io.manebot.database.model.TimedRow;
import io.manebot.user.User;

import javax.persistence.*;
import java.sql.SQLException;

@javax.persistence.Entity
@Table(
        indexes = {
                @Index(columnList = "command"),
                @Index(columnList = "interval"),
                @Index(columnList = "next"),
                @Index(columnList = "userId")
        }
)
public class ScheduledCommand extends TimedRow {
    @Transient
    private final Database database;

    public ScheduledCommand(Database database) {
        this.database = database;
    }

    public ScheduledCommand(Database database, User user, String command, int interval) {
        this(database);

        this.user = (io.manebot.database.model.User) user;
        this.command = command;
        this.nextRun =  Math.toIntExact(System.currentTimeMillis() / 1000L) + interval;
        this.interval = interval;
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column()
    private int scheduledCommandId;

    @ManyToOne(optional = false)
    @JoinColumn(name = "userId")
    private io.manebot.database.model.User user;

    @Column(length = 1024, nullable = false)
    public String command;

    @Column(nullable = false)
    public int nextRun;

    @Column(nullable = false)
    public int interval;

    public String getCommand() {
        return command;
    }

    public void setCommand(String command) {
        try {
            this.command = database.executeTransaction(s -> {
                ScheduledCommand scheduledCommand = s.find(ScheduledCommand.class, scheduledCommandId);
                scheduledCommand.command = command;
                scheduledCommand.setUpdated(System.currentTimeMillis());
                return scheduledCommand.command;
            });
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public int getNextRun() {
        return nextRun;
    }

    public void setNextRun(int nextRun) {
        try {
            this.nextRun = database.executeTransaction(s -> {
                ScheduledCommand scheduledCommand = s.find(ScheduledCommand.class, scheduledCommandId);
                scheduledCommand.nextRun = nextRun;
                scheduledCommand.setUpdated(System.currentTimeMillis());
                return scheduledCommand.nextRun;
            });
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public int getInterval() {
        return interval;
    }

    public void setInterval(int interval) {
        try {
            this.interval = database.executeTransaction(s -> {
                ScheduledCommand scheduledCommand = s.find(ScheduledCommand.class, scheduledCommandId);
                scheduledCommand.interval = interval;
                scheduledCommand.setUpdated(System.currentTimeMillis());
                return scheduledCommand.interval;
            });
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public io.manebot.database.model.User getUser() {
        return user;
    }

    public void setUser(io.manebot.database.model.User user) {
        try {
            this.user = database.executeTransaction(s -> {
                ScheduledCommand scheduledCommand = s.find(ScheduledCommand.class, scheduledCommandId);
                scheduledCommand.user = user;
                scheduledCommand.setUpdated(System.currentTimeMillis());
                return scheduledCommand.user;
            });
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }
}
