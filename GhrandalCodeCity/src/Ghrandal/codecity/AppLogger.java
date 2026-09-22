package Ghrandal.codecity;

import java.io.IOException;
import java.nio.file.*;
import java.time.*;
import java.util.logging.*;

/** Central application logging. Production mode writes to the user's CodeCity log directory. */
final class AppLogger {
    private static final Logger LOG = Logger.getLogger("GhrandalCodeCity");
    static { configure(); }
    private AppLogger() {}
    static Logger get() { return LOG; }
    private static void configure() {
        LOG.setUseParentHandlers(false);
        if (!LOG.getHandlers().equals(new Handler[0])) return;
        try {
            Path dir = Paths.get(System.getProperty("user.home"), ".ghrandal-code-city", "logs");
            Files.createDirectories(dir);
            FileHandler fh = new FileHandler(dir.resolve("codecity-%g.log").toString(), 2_000_000, 5, true);
            fh.setFormatter(new SimpleFormatter());
            LOG.addHandler(fh);
        } catch (IOException ex) {
            ConsoleHandler ch = new ConsoleHandler();
            ch.setFormatter(new SimpleFormatter());
            LOG.addHandler(ch);
        }
        LOG.setLevel(Level.INFO);
    }
}
