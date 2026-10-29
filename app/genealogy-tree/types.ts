/** Graph model for relatives-tree (MIT). Display fields are app-specific. */
export interface Spouse {
  id: string;
  type: 'married' | 'divorced' | 'cohabitation';
}

export interface Parent {
  id: string;
  type: 'blood' | 'adopted' | 'step';
}

export interface Person {
  id: string;
  firstName: string;
  lastName: string;
  gender: 'male' | 'female' | 'other';
  birthDate?: string;
  deathDate?: string;
  spouses: Spouse[];
  parents: Parent[];
}

/** relatives-tree Node (layout input). Built from Person + kinship edges. */
export interface RelativesNode {
  id: string;
  gender: 'male' | 'female';
  parents: { id: string; type: string }[];
  children: { id: string; type: string }[];
  siblings: { id: string; type: string }[];
  spouses: { id: string; type: string }[];
}
