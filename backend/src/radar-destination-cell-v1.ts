/** Must stay byte-for-byte compatible in behavior with Android OfferContextEngine.geoCell(). */
export function radarDestinationCellV1(lat:number,lng:number):string {
  if(!Number.isFinite(lat)||!Number.isFinite(lng)||lat < -90||lat > 90||lng < -180||lng > 180) throw new Error('invalid_location');
  return `g2:${Math.floor(lat*100)}:${Math.floor(lng*100)}`;
}
