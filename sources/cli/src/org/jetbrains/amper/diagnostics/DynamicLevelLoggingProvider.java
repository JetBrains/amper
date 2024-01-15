package org.jetbrains.amper.diagnostics;

import org.tinylog.Level;
import org.tinylog.core.TinylogLoggingProvider;
import org.tinylog.format.MessageFormatter;

public class DynamicLevelLoggingProvider extends TinylogLoggingProvider {
    private volatile Level myActiveLevel = Level.INFO;

    public void setActiveLevel(Level activeLevel) {
        myActiveLevel = activeLevel;
    }

    @Override
    public boolean isEnabled(int depth, String tag, Level level) {
        return myActiveLevel.ordinal() <= level.ordinal() && super.isEnabled(depth + 1, tag, level);
    }

    @Override
    public void log(int depth, String tag, Level level, Throwable exception, MessageFormatter formatter, Object obj, Object... arguments) {
        if (myActiveLevel.ordinal() <= level.ordinal()) {
            super.log(depth + 1, tag, level, exception, formatter, obj, arguments);
        }
    }

    @Override
    public void log(String loggerClassName, String tag, Level level, Throwable exception, MessageFormatter formatter, Object obj, Object... arguments) {
        if (myActiveLevel.ordinal() <= level.ordinal()) {
            super.log(loggerClassName, tag, level, exception, formatter, obj, arguments);
        }
    }
}
