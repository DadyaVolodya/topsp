package org.springframework.samples.petclinic.visit;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

/**
 * Business rules for PetClinic visits (demo slice).
 *
 * v1 SP: cancel after create is forbidden; description locked after create.
 * v2 SP: cancel allowed until 24h before visit; description editable until visit day starts.
 */
@Service
public class VisitService {

    private final AtomicLong seq = new AtomicLong(1);
    private final ConcurrentHashMap<Long, VisitController.Visit> store = new ConcurrentHashMap<>();

    public List<VisitController.Visit> findByPet(long petId) {
        List<VisitController.Visit> out = new ArrayList<>();
        for (VisitController.Visit visit : store.values()) {
            if (visit.petId() == petId) {
                out.add(visit);
            }
        }
        return out;
    }

    public VisitController.Visit create(long petId, LocalDate date, String description) {
        long id = seq.getAndIncrement();
        VisitController.Visit visit = new VisitController.Visit(id, petId, date, description, "SCHEDULED");
        store.put(id, visit);
        return visit;
    }

    public VisitController.Visit updateDescription(long petId, long visitId, String description) {
        VisitController.Visit current = require(petId, visitId);
        if (!canEditDescription(current)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "description locked for this visit");
        }
        VisitController.Visit next = new VisitController.Visit(
                current.id(), current.petId(), current.date(), description, current.status());
        store.put(visitId, next);
        return next;
    }

    public void cancel(long petId, long visitId) {
        VisitController.Visit current = require(petId, visitId);
        if (!canCancel(current)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "cancel is not allowed");
        }
        store.put(visitId, new VisitController.Visit(
                current.id(), current.petId(), current.date(), current.description(), "CANCELLED"));
    }

    /** v1: always false after create. v2: true if more than 24 hours remain. */
    public boolean canCancel(VisitController.Visit visit) {
        if (visit == null || !"SCHEDULED".equals(visit.status())) {
            return false;
        }
        // DEMO_RULE_V1: return false;
        // DEMO_RULE_V2:
        long hours = ChronoUnit.HOURS.between(LocalDate.now().atStartOfDay(), visit.date().atStartOfDay());
        return hours >= 24;
    }

    /** v1: false after create. v2: true while visit.date is in the future. */
    public boolean canEditDescription(VisitController.Visit visit) {
        if (visit == null || !"SCHEDULED".equals(visit.status())) {
            return false;
        }
        // DEMO_RULE_V1: return false;
        // DEMO_RULE_V2:
        return visit.date().isAfter(LocalDate.now());
    }

    private VisitController.Visit require(long petId, long visitId) {
        VisitController.Visit visit = store.get(visitId);
        if (visit == null || visit.petId() != petId) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "visit not found");
        }
        return visit;
    }
}
