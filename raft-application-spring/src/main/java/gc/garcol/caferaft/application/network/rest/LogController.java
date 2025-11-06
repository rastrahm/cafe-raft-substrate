package gc.garcol.caferaft.application.network.rest;

import gc.garcol.caferaft.core.log.LogEntry;
import gc.garcol.caferaft.core.log.LogManager;
import gc.garcol.caferaft.core.log.Segment;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.List;

/**
 * Endpoint de solo lectura para inspeccionar el log de Raft.
 */
@RestController
@RequiredArgsConstructor
public class LogController {

    private final LogManager logManager;

    @GetMapping("/logs")
    public Mono<List<LogEntryView>> list(
            @RequestParam(name = "fromTerm", required = false) Long fromTerm,
            @RequestParam(name = "fromIndex", required = false) Long fromIndex,
            @RequestParam(name = "limit", required = false, defaultValue = "50") int limit) {

        List<LogEntryView> result = new ArrayList<>();
        if (limit <= 0) {
            return Mono.just(result);
        }

        List<Segment> segments = logManager.segments;
        if (segments == null || segments.isEmpty()) {
            return Mono.just(result);
        }

        // Determinar punto de inicio
        int segStart = 0;
        long idxStart = 0;

        if (fromTerm != null) {
            // buscar el segmento con ese term
            for (int i = 0; i < segments.size(); i++) {
                if (segments.get(i).getTerm() == fromTerm) {
                    segStart = i;
                    idxStart = fromIndex != null ? Math.max(0, fromIndex) : 0;
                    break;
                }
            }
        }

        outer:
        for (int s = segStart; s < segments.size(); s++) {
            Segment seg = segments.get(s);
            long size = logManager.segmentSize(seg.getTerm());
            long start = (s == segStart) ? idxStart : 0;
            for (long i = start; i < size; i++) {
                LogEntry le = logManager.getLog(seg.getTerm(), i);
                if (le != null) {
                    result.add(LogEntryView.from(le));
                    if (result.size() >= limit) {
                        break outer;
                    }
                }
            }
        }

        return Mono.just(result);
    }

    public record LogEntryView(long term, long index, String commandType, Object command) {
        public static LogEntryView from(LogEntry entry) {
            String type = entry.getCommand() != null ? entry.getCommand().getClass().getSimpleName() : null;
            return new LogEntryView(entry.getPosition().term(), entry.getPosition().index(), type, entry.getCommand());
        }
    }
}


