package nhz.util;

import nhz.Nhz;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;
import java.util.logging.LogManager;

/**
 * Handle logging for the Nhz node server
 */
public final class Logger {

    /** Log event types */
    public static enum Event {
        MESSAGE, EXCEPTION
    }

    /** Log levels */
    public static enum Level {
        DEBUG, INFO, WARN, ERROR
    }

    /** Message listeners */
    private static final Listeners<String, Event> messageListeners = new Listeners<>();

    /** Exception listeners */
    private static final Listeners<Exception, Event> exceptionListeners = new Listeners<>();

    /**
     * Initialize the JDK log manager using the Java logging configuration files
     * nhz/conf/logging-default.properties and nhz/conf/logging.properties.  The
     * values specified in logging.properties will override the values specified in
     * logging-default.properties.  The system-wide Java logging configuration file
     * jre/lib/logging.properties will be used if no Nhz configuration file is found.
     *
     * We will provide our own LogManager extension to delay log handler shutdown
     * until we no longer need logging services.
     */
    static {
        System.setProperty("java.util.logging.manager", "nhz.util.NhzLogManager");
        try {
            boolean foundProperties = false;
            Properties loggingProperties = new Properties();
            try (InputStream is = ClassLoader.getSystemResourceAsStream("logging-default.properties")) {
                if (is != null) {
                    loggingProperties.load(is);
                    foundProperties = true;
                }
            }
            try (InputStream is = ClassLoader.getSystemResourceAsStream("logging.properties")) {
                if (is != null) {
                    loggingProperties.load(is);
                    foundProperties = true;
                }
            }
            if (foundProperties) {
                ByteArrayOutputStream outStream = new ByteArrayOutputStream();
                loggingProperties.store(outStream, "logging properties");
                ByteArrayInputStream inStream = new ByteArrayInputStream(outStream.toByteArray());
                java.util.logging.LogManager.getLogManager().readConfiguration(inStream);
                inStream.close();
                outStream.close();
            }
        } catch (IOException e) {
            throw new RuntimeException("Error loading logging properties", e);
        }
        BriefLogFormatter.init();
    }

    /** Our logger instance */
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(nhz.Nhz.class);

    /** Enable stack traces */
    private static final boolean enableStackTraces = Nhz.getBooleanProperty("nhz.enableStackTraces");

    /** Enable log traceback */
    private static final boolean enableLogTraceback = Nhz.getBooleanProperty("nhz.enableLogTraceback");

    /**
     * No constructor
     */
    private Logger() {}

    /**
     * Logger shutdown
     */
    public static void shutdown() {
        ((NhzLogManager)LogManager.getLogManager()).nhzShutdown();
    }

    /**
     * Add a message listener
     *
     * @param       listener            Listener
     * @param       eventType           Notification event type
     * @return                          TRUE if listener added
     */
    public static boolean addMessageListener(Listener<String> listener, Event eventType) {
        return messageListeners.addListener(listener, eventType);
    }

    /**
     * Add an exception listener
     *
     * @param       listener            Listener
     * @param       eventType           Notification event type
     * @return                          TRUE if listener added
     */
    public static boolean addExceptionListener(Listener<Exception> listener, Event eventType) {
        return exceptionListeners.addListener(listener, eventType);
    }

    /**
     * Remove a message listener
     *
     * @param       listener            Listener
     * @param       eventType           Notification event type
     * @return                          TRUE if listener removed
     */
    public static boolean removeMessageListener(Listener<String> listener, Event eventType) {
        return messageListeners.removeListener(listener, eventType);
    }

    /**
     * Remove an exception listener
     *
     * @param       listener            Listener
     * @param       eventType           Notification event type
     * @return                          TRUE if listener removed
     */
    public static boolean removeExceptionListener(Listener<Exception> listener, Event eventType) {
        return exceptionListeners.removeListener(listener, eventType);
    }

    /**
     * Log a message (map to INFO)
     *
     * @param       message             Message
     */
    public static void logMessage(String message) {
        doLog(Level.INFO, message, null);
    }

    /**
     * Log an exception (map to ERROR)
     *
     * @param       message             Message
     * @param       exc                 Exception
     */
    public static void logMessage(String message, Exception exc) {
        doLog(Level.ERROR, message, exc);
    }

    /**
     * Log an ERROR message
     *
     * @param       message             Message
     */
    public static void logErrorMessage(String message) {
        doLog(Level.ERROR, message, null);
    }

    /**
     * Log an ERROR exception
     *
     * @param       message             Message
     * @param       exc                 Exception
     */
    public static void logErrorMessage(String message, Exception exc) {
        doLog(Level.ERROR, message, exc);
    }

    /**
     * Log a WARNING message
     *
     * @param       message             Message
     */
    public static void logWarningMessage(String message) {
        doLog(Level.WARN, message, null);
    }

    /**
     * Log a WARNING exception
     *
     * @param       message             Message
     * @param       exc                 Exception
     */
    public static void logWarningMessage(String message, Exception exc) {
        doLog(Level.WARN, message, exc);
    }

    /**
     * Log an INFO message
     *
     * @param       message             Message
     */
    public static void logInfoMessage(String message) {
        doLog(Level.INFO, message, null);
    }

    /**
     * Log an INFO exception
     *
     * @param       message             Message
     * @param       exc                 Exception
     */
    public static void logInfoMessage(String message, Exception exc) {
        doLog(Level.INFO, message, exc);
    }

    /**
     * Log a debug message
     *
     * @param       message             Message
     */
    public static void logDebugMessage(String message) {
        doLog(Level.DEBUG, message, null);
    }

    /**
     * Log a debug exception
     *
     * @param       message             Message
     * @param       exc                 Exception
     */
    public static void logDebugMessage(String message, Exception exc) {
        doLog(Level.DEBUG, message, exc);
    }

    /**
     * Log the event
     *
     * @param       level               Level
     * @param       message             Message
     * @param       exc                 Exception
     */
    private static void doLog(Level level, String message, Exception exc) {
        String logMessage = message;
        Exception e = exc;
        //
        // Add caller class and method if enabled
        //
        if (enableLogTraceback) {
            StackTraceElement caller = Thread.currentThread().getStackTrace()[3];
            String className = caller.getClassName();
            int index = className.lastIndexOf('.');
            if (index != -1)
                className = className.substring(index+1);
            logMessage = className + "." + caller.getMethodName() + ": " + logMessage;
        }
        //
        // Format the stack trace if enabled
        //
        if (e != null) {
            if (!enableStackTraces) {
                logMessage = logMessage + "\n" + exc.toString();
                e = null;
            }
        }
        //
        // Log the event
        //
        switch (level) {
            case DEBUG:
                log.debug(logMessage, e);
                break;
            case INFO:
                log.info(logMessage, e);
                break;
            case WARN:
                log.warn(logMessage, e);
                break;
            case ERROR:
                log.error(logMessage, e);
                break;
        }
        //
        // Notify listeners
        //
        if (exc != null)
            exceptionListeners.notify(exc, Event.EXCEPTION);
        else
            messageListeners.notify(message, Event.MESSAGE);
    }
}
