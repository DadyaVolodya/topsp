package org.springframework.samples.petclinic.owner;

import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** PetClinic mock: owners and their pets. */
@RestController
@RequestMapping("/api/owners")
public class OwnerController {

    @GetMapping("/{ownerId}")
    public OwnerView get(@PathVariable long ownerId) {
        return new OwnerView(ownerId, "George Franklin", "110 W. Liberty St.", List.of(
                new PetView(1, "Leo", "cat"),
                new PetView(2, "Basil", "dog")));
    }

    @GetMapping("/{ownerId}/pets")
    public List<PetView> pets(@PathVariable long ownerId) {
        return get(ownerId).pets();
    }

    public record OwnerView(long id, String name, String address, List<PetView> pets) {
    }

    public record PetView(long id, String name, String type) {
    }
}
