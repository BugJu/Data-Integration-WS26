package uni.dj;

import org.apache.maven.plugin.logging.Log;

/*
    Concrete implementation of MavenLogger that wraps Maven's Mojo Log.
 */
public class MojoMavenLogger implements MavenLogger {
    private final Log log;

    public MojoMavenLogger(Log log) {
        this.log = log;
    }

    /*
        Logs an info message.
     */
    @Override
    public void info(String msg) {
        log.info(msg);
    }

    /*
        Logs a debug message.
     */
    @Override
    public void debug(String msg) {
        log.debug(msg);
    }

    /*
        Logs a warning message.
     */
    @Override
    public void warn(String msg) {
        log.warn(msg);
    }

    /*
        Logs an error message.
     */
    @Override
    public void error(String msg) {
        log.error(msg);
    }
}
