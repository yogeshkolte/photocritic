module.exports = {
  preset: 'jest-expo',
  setupFilesAfterEnv: ['<rootDir>/test/setup.ts'],
  testPathIgnorePatterns: ['/node_modules/', '/TokenEconomizer/', '/research/', '/mockup/', '/test/rules/', '/spikes/'],
  modulePathIgnorePatterns: ['<rootDir>/TokenEconomizer/', '<rootDir>/research/', '<rootDir>/mockup/'],
  moduleNameMapper: { '^@/(.*)$': '<rootDir>/src/$1' },
};
