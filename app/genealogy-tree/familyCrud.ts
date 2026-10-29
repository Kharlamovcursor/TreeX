import type { Parent, Person, Spouse } from './types';

export type FamilyState = Person[];

const spouseLink = (a: string, b: string, type: Spouse['type'] = 'married'): Spouse => ({
  id: b,
  type,
});

const parentLink = (id: string, type: Parent['type'] = 'blood'): Parent => ({
  id,
  type,
});

const upsertSpouse = (person: Person, link: Spouse): Person => {
  const spouses = person.spouses.filter((s) => s.id !== link.id);
  return { ...person, spouses: [...spouses, link] };
};

const removeIdFromSpouses = (person: Person, id: string): Person => ({
  ...person,
  spouses: person.spouses.filter((s) => s.id !== id),
});

const removeIdFromParents = (person: Person, id: string): Person => ({
  ...person,
  parents: person.parents.filter((p) => p.id !== id),
});

export function addPartner(
  people: FamilyState,
  personId: string,
  partner: Omit<Person, 'spouses' | 'parents'> & { spouses?: Spouse[]; parents?: Parent[] },
  linkType: Spouse['type'] = 'married',
): FamilyState {
  const index = people.findIndex((p) => p.id === personId);
  if (index < 0) throw new Error(`Person ${personId} not found`);
  const newPartner: Person = {
    ...partner,
    spouses: partner.spouses ?? [],
    parents: partner.parents ?? [],
  };
  const updatedA = upsertSpouse(people[index], { id: newPartner.id, type: linkType });
  const updatedB = upsertSpouse(newPartner, { id: personId, type: linkType });
  const withoutB = people.filter((p) => p.id !== newPartner.id);
  return withoutB.map((p) => (p.id === personId ? updatedA : p)).concat(updatedB);
}

export function addChild(
  people: FamilyState,
  parent1Id: string,
  parent2Id: string | null,
  child: Omit<Person, 'spouses' | 'parents'> & { spouses?: Spouse[]; parents?: Parent[] },
): FamilyState {
  const parents: Parent[] = [parentLink(parent1Id)];
  if (parent2Id) parents.push(parentLink(parent2Id));
  const newChild: Person = {
    ...child,
    spouses: child.spouses ?? [],
    parents,
  };
  return people.concat(newChild);
}

export function addParentToSingleParent(
  people: FamilyState,
  childId: string,
  existingParentId: string,
  newParent: Omit<Person, 'spouses' | 'parents'> & { spouses?: Spouse[]; parents?: Parent[] },
): FamilyState {
  const child = people.find((p) => p.id === childId);
  if (!child) throw new Error(`Child ${childId} not found`);
  if (!child.parents.some((p) => p.id === existingParentId)) {
    throw new Error(`Parent ${existingParentId} is not linked to child ${childId}`);
  }
  let next = addPartner(people, existingParentId, newParent, 'married');
  next = next.map((p) =>
    p.id === childId
      ? {
          ...p,
          parents: [
            ...p.parents.filter((x) => x.id !== newParent.id),
            parentLink(newParent.id),
          ],
        }
      : p,
  );
  return next;
}

export function deletePerson(people: FamilyState, personId: string): FamilyState {
  return people
    .filter((p) => p.id !== personId)
    .map((p) => removeIdFromParents(removeIdFromSpouses(p, personId), personId));
}
