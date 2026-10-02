export type Reliability = 'insufficient'|'low'|'medium'|'high';
export type Potential = 'insufficient'|'low'|'medium'|'high';

export type RadarContextV1 = {
  id: string;
  kind: string;
  name: string;
  venueName: string|null;
  lat: number;
  lng: number;
  startsAt: string|null;
  endsAt: string|null;
  activeFrom: string|null;
  activeUntil: string|null;
  sourceConfidence: number;
  source: string;
  metadata: Record<string, unknown>;
};

export type ContinuityEvidenceV1 = {
  source: 'personal'|'collective'|'v7'|'context'|'distance'|'baseline';
  code: string;
  label: string;
  contribution: number;
  samples?: number;
  reliability?: Reliability;
};

export type RadarOpportunityV1 = {
  contextId: string;
  name: string;
  kind: string;
  lat: number;
  lng: number;
  distanceKm: number;
  eta: string;
  score: number|null;
  baselineScore: number|null;
  uplift: number|null;
  confidence: number;
  potential: Potential;
  activeWindow: {from:string|null; until:string|null};
  reasons: ContinuityEvidenceV1[];
};
