import * as tuning from './tuning';

describe('tuning constants', () => {
  it('are all positive numbers', () => {
    for (const [name, value] of Object.entries(tuning)) {
      expect({ name, ok: typeof value === 'number' && value > 0 }).toEqual({ name, ok: true });
    }
  });

  it('keep JPEG quality between 0 and 1', () => {
    expect(tuning.JPEG_QUALITY).toBeGreaterThan(0);
    expect(tuning.JPEG_QUALITY).toBeLessThanOrEqual(1);
  });
});
