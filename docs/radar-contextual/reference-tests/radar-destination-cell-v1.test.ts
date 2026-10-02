import { radarDestinationCellV1 } from '../../../backend/src/radar-destination-cell-v1';

describe('radarDestinationCellV1',()=>{
  it('matches OfferContextEngine canonical g2 behavior',()=>{
    expect(radarDestinationCellV1(-23.5505,-46.6333)).toBe('g2:-2356:-4664');
  });
});
