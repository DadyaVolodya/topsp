import { cancelVisit, updateVisitDescription, type Visit } from "../api/visitsApi";

type Props = {
  petId: number;
  visit: Visit;
  /** Driven by SP: v1 always false; v2 true until 24h before visit */
  canCancel: boolean;
  /** Driven by SP: v1 always false; v2 true until visit day */
  canEditDescription: boolean;
  onChanged: () => void;
};

/** PetClinic front mock: one visit row with cancel / edit description. */
export function VisitRow({ petId, visit, canCancel, canEditDescription, onChanged }: Props) {
  async function onCancel() {
    if (!canCancel) return;
    await cancelVisit(petId, visit.id);
    onChanged();
  }

  async function onSaveDescription(value: string) {
    if (!canEditDescription) return;
    await updateVisitDescription(petId, visit.id, value);
    onChanged();
  }

  return (
    <div className="visit-row" data-visit-id={visit.id}>
      <span>{visit.date}</span>
      <input
        defaultValue={visit.description}
        disabled={!canEditDescription}
        aria-label="visit description"
        onBlur={(e) => onSaveDescription(e.target.value)}
      />
      <button type="button" disabled={!canCancel} onClick={onCancel}>
        Cancel visit
      </button>
    </div>
  );
}
