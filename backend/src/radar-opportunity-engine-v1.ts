import { ContinuityEvidenceV1, Potential, RadarContextV1, RadarOpportunityV1 } from './radar-opportunity-contract-v1';

export type ContinuitySignalV1 = {
  probabilityPct: number|null;
  samples: number;
  reliability: 'insufficient'|'low'|'medium'|'high';
  source: 'personal'|'collective'|'insufficient';
};

export type ScoreInputV1 = {
  destination:{lat:number;lng:number;eta:string};
  context:RadarContextV1;
  target:ContinuitySignalV1;
  baseline:ContinuitySignalV1;
};

const clamp=(n:number,min=0,max=100)=>Math.max(min,Math.min(max,n));
const r2=(n:number)=>Math.round(n*100)/100;

export function haversineKmV1(aLat:number,aLng:number,bLat:number,bLng:number){
  const rad=Math.PI/180,dLat=(bLat-aLat)*rad,dLng=(bLng-aLng)*rad;
  const x=Math.sin(dLat/2)**2+Math.cos(aLat*rad)*Math.cos(bLat*rad)*Math.sin(dLng/2)**2;
  return 6371*2*Math.atan2(Math.sqrt(x),Math.sqrt(1-x));
}

function reliabilityWeight(r:ContinuitySignalV1['reliability']){
  return r==='high'?1:r==='medium'?.85:r==='low'?.65:.35;
}

function temporalFit(c:RadarContextV1,eta:string){
  if(!c.activeFrom||!c.activeUntil) return 50;
  const t=new Date(eta).getTime(),a=new Date(c.activeFrom).getTime(),b=new Date(c.activeUntil).getTime();
  if(![t,a,b].every(Number.isFinite)) return 50;
  if(t>=a&&t<=b) return 100;
  const delta=Math.min(Math.abs(t-a),Math.abs(t-b))/60000;
  return clamp(100-delta*2.5);
}

function potential(score:number|null,confidence:number):Potential{
  if(score===null||confidence<.35) return 'insufficient';
  if(score>=75) return 'high'; if(score>=55) return 'medium'; return 'low';
}

/** Pure deterministic scorer. It never manufactures demand from an event. */
export function scoreRadarOpportunityV1(input:ScoreInputV1):RadarOpportunityV1{
  const {destination,context,target,baseline}=input;
  const distanceKm=haversineKmV1(destination.lat,destination.lng,context.lat,context.lng);
  const reasons:ContinuityEvidenceV1[]=[];
  const targetP=target.probabilityPct;
  const baseP=baseline.probabilityPct;
  const temporal=temporalFit(context,destination.eta);
  const distanceScore=clamp(100-distanceKm*18);
  const contextSignal=temporal*clamp(context.sourceConfidence,0,1);
  const targetReliability=reliabilityWeight(target.reliability);

  if(targetP!==null) reasons.push({source:target.source==='personal'?'personal':'collective',code:'continuity_probability',label:`Continuidade histórica ${targetP}%`,contribution:targetP,samples:target.samples,reliability:target.reliability});
  reasons.push({source:'context',code:'temporal_fit',label:'Contexto compatível com o horário de chegada',contribution:r2(contextSignal)});
  reasons.push({source:'distance',code:'reposition_cost',label:`Reposicionamento de ${r2(distanceKm)} km`,contribution:r2(distanceScore)});

  // Without statistically usable continuity evidence we expose context, not a fake opportunity score.
  const score=targetP===null?null:r2(clamp(targetP*.62*targetReliability + contextSignal*.23 + distanceScore*.15));
  const baselineScore=baseP===null?null:r2(baseP);
  const uplift=score===null||baselineScore===null?null:r2(score-baselineScore);
  if(baseP!==null) reasons.push({source:'baseline',code:'destination_baseline',label:`Permanecer no destino: ${r2(baseP)}%`,contribution:r2(baseP),samples:baseline.samples,reliability:baseline.reliability});

  const sampleConfidence=target.samples>=100?1:target.samples>=50?.8:target.samples>=20?.6:.25;
  const confidence=r2(clamp((sampleConfidence*.65+context.sourceConfidence*.35),0,1));
  return {contextId:context.id,name:context.name,kind:context.kind,lat:context.lat,lng:context.lng,distanceKm:r2(distanceKm),eta:destination.eta,score,baselineScore,uplift,confidence,potential:potential(score,confidence),activeWindow:{from:context.activeFrom,until:context.activeUntil},reasons:reasons.sort((a,b)=>b.contribution-a.contribution)};
}
