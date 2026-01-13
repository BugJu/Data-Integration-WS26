package uni.dj;

import org.apache.maven.plugin.logging.Log;

public class MojoMavenLogger implements MavenLogger {
    private final Log log;

    public MojoMavenLogger(Log log) {
        this.log = log;
    }

    @Override
    public void info(String msg) {
        log.info(msg);
    }

    @Override
    public void debug(String msg) {
        log.debug(msg);
    }

    @Override
    public void warn(String msg) {
        log.warn(msg);
    }

    @Override
    public void error(String msg) {
        log.error(msg);
    }
}
