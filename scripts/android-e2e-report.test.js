const assert = require('node:assert/strict');
const fs = require('node:fs');
const os = require('node:os');
const path = require('node:path');
const { execFileSync } = require('node:child_process');
const test = require('node:test');

test('android e2e report falls back to collected additional test output files', () => {
  const runDir = fs.mkdtempSync(path.join(os.tmpdir(), 'android-e2e-report-'));
  const additionalOutputDir = path.join(
    runDir,
    'connected_android_test_additional_output',
    'debugAndroidTest',
    'connected',
    'AndroidE2E_API35_Pixel5(AVD) - 15',
  );
  fs.mkdirSync(additionalOutputDir, { recursive: true });
  fs.writeFileSync(
    path.join(additionalOutputDir, 'android-e2e-report.json'),
    JSON.stringify({
      steps: [
        { screen: 'login', step: 'sign_up', status: 'started' },
        { screen: 'login', step: 'sign_up', status: 'passed' },
      ],
      issues: [
        { type: 'warning', screen: 'login', step: 'sign_up', message: 'test warning' },
      ],
    }),
  );
  fs.writeFileSync(
    path.join(additionalOutputDir, 'android-e2e-progress.json'),
    JSON.stringify({
      screen: 'login',
      step: 'sign_up',
      updatedAtEpochMillis: 123,
    }),
  );

  execFileSync('node', [path.join(__dirname, 'android-e2e-report.js'), runDir], {
    stdio: 'inherit',
    env: {
      ...process.env,
      ANDROID_E2E_EXIT_CODE: '0',
      ANDROID_E2E_TEST_RAN: '1',
      ANDROID_E2E_RUN_ID: '20260327-120000',
    },
  });

  const report = JSON.parse(fs.readFileSync(path.join(runDir, 'report.json'), 'utf8'));
  assert.equal(report.progress.screen, 'login');
  assert.equal(report.progress.step, 'sign_up');
  assert.equal(report.artifacts.appReport, path.join(additionalOutputDir, 'android-e2e-report.json'));
  assert.equal(report.artifacts.progress, path.join(additionalOutputDir, 'android-e2e-progress.json'));
  assert.match(fs.readFileSync(path.join(runDir, 'report.txt'), 'utf8'), /login\/sign_up: passed/);
});

test('android e2e report prefers the newest collected additional output files', async () => {
  const runDir = fs.mkdtempSync(path.join(os.tmpdir(), 'android-e2e-report-'));
  const additionalOutputRoot = path.join(
    runDir,
    'connected_android_test_additional_output',
    'debugAndroidTest',
    'connected',
  );
  const staleDir = path.join(additionalOutputRoot, 'StaleDevice');
  const currentDir = path.join(additionalOutputRoot, 'CurrentDevice');
  fs.mkdirSync(staleDir, { recursive: true });
  fs.mkdirSync(currentDir, { recursive: true });

  fs.writeFileSync(
    path.join(staleDir, 'android-e2e-report.json'),
    JSON.stringify({ steps: [{ screen: 'stale', step: 'old', status: 'passed' }], issues: [] }),
  );
  fs.writeFileSync(
    path.join(staleDir, 'android-e2e-progress.json'),
    JSON.stringify({ screen: 'stale', step: 'old', updatedAtEpochMillis: 1 }),
  );

  await new Promise((resolve) => setTimeout(resolve, 15));

  fs.writeFileSync(
    path.join(currentDir, 'android-e2e-report.json'),
    JSON.stringify({ steps: [{ screen: 'feed', step: 'refresh', status: 'passed' }], issues: [] }),
  );
  fs.writeFileSync(
    path.join(currentDir, 'android-e2e-progress.json'),
    JSON.stringify({ screen: 'feed', step: 'refresh', updatedAtEpochMillis: 2 }),
  );

  execFileSync('node', [path.join(__dirname, 'android-e2e-report.js'), runDir], {
    stdio: 'inherit',
    env: {
      ...process.env,
      ANDROID_E2E_EXIT_CODE: '0',
      ANDROID_E2E_TEST_RAN: '1',
      ANDROID_E2E_RUN_ID: '20260327-120001',
    },
  });

  const report = JSON.parse(fs.readFileSync(path.join(runDir, 'report.json'), 'utf8'));
  assert.deepEqual(report.steps, [{ screen: 'feed', step: 'refresh', status: 'passed' }]);
  assert.equal(report.progress.screen, 'feed');
  assert.equal(report.progress.step, 'refresh');
  assert.equal(report.artifacts.appReport, path.join(currentDir, 'android-e2e-report.json'));
  assert.equal(report.artifacts.progress, path.join(currentDir, 'android-e2e-progress.json'));
});
