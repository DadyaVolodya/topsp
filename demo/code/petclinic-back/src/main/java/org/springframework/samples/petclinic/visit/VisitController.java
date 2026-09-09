package org.springframework.samples.petclinic.visit;

import java.time.LocalDate;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * PetClinic mock: visits for a pet.
 * Demo rule lives in VisitService.canCancel / canEditDescription.
 */
@RestController
@RequestMapping("/api/pets/{petId}/visits")
public class VisitController {

    private final VisitService visits;

    public VisitController(VisitService visits) {
        this.visits = visits;
    }

    @GetMapping
    public List<Visit> list(@PathVariable long petId) {
        return visits.findByPet(petId);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Visit create(@PathVariable long petId, @RequestBody VisitRequest request) {
        return visits.create(petId, request.date(), request.description());
    }

    @PutMapping("/{visitId}")
    public Visit update(
            @PathVariable long petId,
            @PathVariable long visitId,
            @RequestBody VisitRequest request
    ) {
        return visits.updateDescription(petId, visitId, request.description());
    }

    @DeleteMapping("/{visitId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void cancel(@PathVariable long petId, @PathVariable long visitId) {
        visits.cancel(petId, visitId);
    }

    public record Visit(long id, long petId, LocalDate date, String description, String status) {
    }

    public record VisitRequest(LocalDate date, String description) {
    }
}
