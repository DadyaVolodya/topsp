import { useEffect, useState } from "react";
import { listVisits, type Visit } from "../api/visitsApi";
import { VisitRow } from "../components/VisitRow";

/**
 * PetClinic front mock: pet visits page.
 * Flags canCancel / canEditDescription must follow VisitService rules from SP.
 */
export function PetVisitsPage({ petId }: { petId: number }) {
  const [visits, setVisits] = useState<Visit[]>([]);

  async function reload() {
    setVisits(await listVisits(petId));
  }

  useEffect(() => {
    reload();
  }, [petId]);

  return (
    <main>
      <h1>Pet visits</h1>
      {visits.map((visit) => (
        <VisitRow
          key={visit.id}
          petId={petId}
          visit={visit}
          canCancel={visit.canCancel}
          canEditDescription={visit.canEditDescription}
          onChanged={reload}
        />
      ))}
    </main>
  );
}
