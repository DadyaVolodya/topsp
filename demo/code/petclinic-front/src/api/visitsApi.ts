export type Visit = {
  id: number;
  petId: number;
  date: string;
  description: string;
  status: string;
  canCancel: boolean;
  canEditDescription: boolean;
};

const BASE = "/api/pets";

export async function listVisits(petId: number): Promise<Visit[]> {
  const res = await fetch(`${BASE}/${petId}/visits`);
  return res.json();
}

export async function updateVisitDescription(petId: number, visitId: number, description: string) {
  const res = await fetch(`${BASE}/${petId}/visits/${visitId}`, {
    method: "PUT",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ description }),
  });
  if (!res.ok) throw new Error("update failed");
  return res.json();
}

export async function cancelVisit(petId: number, visitId: number) {
  const res = await fetch(`${BASE}/${petId}/visits/${visitId}`, { method: "DELETE" });
  if (!res.ok) throw new Error("cancel failed");
}
