const fs = require('node:fs');
const path = require('node:path');

const runDir = process.argv[2];
if (!runDir) {
  console.error('Usage: node scripts/android-e2e-report.js <run-dir>');
  process.exit(1);
}

function readText(filePath) {
  try {
    return fs.readFileSync(filePath, 'utf8');
  } catch {
    return null;
  }
}

function readJson(filePath) {
  const text = readText(filePath);
  if (!text) return null;
  try {
    return JSON.parse(text);
  } catch {
    return null;
  }
}

function shortExcerpt(text, limit = 4000) {
  if (!text) return null;
  return text.length <= limit ? text : `${text.slice(0, limit)}...`;
}

function escapeForReport(text) {
  return String(text ?? '').trim();
}

function loadAppReport(runDirPath) {
  const filePath = path.join(runDirPath, 'android-e2e-report.json');
  const parsed = readJson(filePath);
  return parsed && typeof parsed === 'object' ? parsed : null;
}

function loadProgress(runDirPath) {
  const filePath = path.join(runDirPath, 'android-e2e-progress.json');
  const parsed = readJson(filePath);
  return parsed && typeof parsed === 'object' ? parsed : null;
}

function findLatestOutputDir(runDirPath) {
  const candidate = path.join(runDirPath, 'androidTest-results');
  return fs.existsSync(candidate) ? candidate : null;
}

function readInstrumentationFailure(runDirPath) {
  const testResultPath = path.join(runDirPath, 'androidTest-results', 'test-result.textproto');
  const testLogPath = path.join(runDirPath, 'androidTest-results', 'testlog', 'test-results.log');
  const textProto = readText(testResultPath);
  if (textProto) {
    const messageMatch = textProto.match(/name: "INSTRUMENTATION_FAILED"[\s\S]*?message: "([^"]+)"/);
    if (messageMatch) {
      return {
        message: messageMatch[1],
        logExcerpt: shortExcerpt(readText(testLogPath) || textProto, 1200),
      };
    }
  }

  const testLog = readText(testLogPath);
  if (testLog) {
    const crashLine = testLog.split(/\r?\n/).find((line) => line.includes('shortMsg='));
    if (crashLine) {
      return {
        message: crashLine.replace(/^.*shortMsg=/, '').trim(),
        logExcerpt: shortExcerpt(testLog, 1200),
      };
    }
  }

  return null;
}

function readCrashException(runDirPath) {
  const logcatText = readText(path.join(runDirPath, 'logcat.txt'));
  const crashBlock = extractCrashBlock(logcatText);
  if (crashBlock) return crashBlock;

  const testLogPath = path.join(runDirPath, 'androidTest-results', 'testlog', 'test-results.log');
  const testLog = readText(testLogPath);
  if (!testLog) return null;

  const lines = testLog.split(/\r?\n/).filter(Boolean);
  return lines.length > 0 ? lines.join('\n') : null;
}

function extractCrashBlock(logcatText) {
  if (!logcatText) return null;
  const lines = logcatText.split(/\r?\n/);
  const startIndex = lines.findIndex((line) => line.includes('FATAL EXCEPTION'));
  if (startIndex === -1) return null;

  const block = [];
  for (let index = startIndex; index < lines.length; index += 1) {
    const line = lines[index];
    if (index > startIndex && /^\d\d-\d\d \d\d:\d\d:\d\d\.\d{3} /.test(line)) {
      break;
    }
    block.push(line);
  }

  return block.join('\n');
}

function defaultIssue(screen, step, type, message, extras = {}) {
  return {
    type,
    screen,
    step,
    message: message || null,
    exception: extras.exception || null,
    logExcerpt: extras.logExcerpt || null,
  };
}

function normalizeIssues(issues) {
  return (issues || []).map((issue) => ({
    type: issue.type || 'warning',
    screen: issue.screen || 'unknown',
    step: issue.step || 'unknown',
    message: issue.message || null,
    exception: issue.exception || null,
    logExcerpt: issue.logExcerpt || null,
  }));
}

function summarizeSteps(steps) {
  return (steps || []).map((step) => ({
    screen: step.screen || 'unknown',
    step: step.step || 'unknown',
    status: step.status || 'unknown',
  }));
}

function runIdToIsoTimestamp(runId) {
  const match = /^(\d{4})(\d{2})(\d{2})-(\d{2})(\d{2})(\d{2})$/.exec(runId);
  if (!match) return null;

  const [, year, month, day, hour, minute, second] = match;
  return new Date(`${year}-${month}-${day}T${hour}:${minute}:${second}`).toISOString();
}

function buildOverallStatus(exitCode, testRan, issues) {
  if (exitCode !== 0 && !testRan) return 'setup_failed';
  if (issues.some((issue) => issue.type === 'handled_error' || issue.type === 'assertion_failure' || issue.type === 'crash' || issue.type === 'setup_failure' || issue.type === 'runner_failure')) {
    return 'failed';
  }
  if (exitCode !== 0) return 'failed';
  if (issues.some((issue) => issue.type === 'warning')) return 'passed_with_warnings';
  return 'passed';
}

function formatIssueLine(issue) {
  const parts = [`- [${issue.type}] ${issue.screen}/${issue.step}`];
  if (issue.message) parts.push(`: ${issue.message}`);
  return parts.join('');
}

function buildReportText(report) {
  const lines = [];
  lines.push('Android E2E Report');
  lines.push(`Overall status: ${report.overallStatus}`);
  lines.push(`Run id: ${report.run.runId}`);
  lines.push(`Device: ${report.device.serial || 'none'} (${report.device.avdName})`);
  lines.push(`App: ${report.app.packageId}`);
  lines.push(`Fixture URL: ${report.app.fixtureUrl}`);
  lines.push('');
  lines.push('Steps:');
  if (report.steps.length === 0) {
    lines.push('- none');
  } else {
    report.steps.forEach((step) => {
      lines.push(`- ${step.screen}/${step.step}: ${step.status}`);
    });
  }
  lines.push('');
  lines.push('Issues:');
  if (report.issues.length === 0) {
    lines.push('- none');
  } else {
    report.issues.forEach((issue) => {
      lines.push(formatIssueLine(issue));
      if (issue.exception) {
        lines.push('  exception:');
        lines.push(...shortExcerpt(issue.exception, 1200).split('\n').map((line) => `  ${line}`));
      }
      if (issue.logExcerpt) {
        lines.push('  log excerpt:');
        lines.push(...shortExcerpt(issue.logExcerpt, 1200).split('\n').map((line) => `  ${line}`));
      }
    });
  }
  lines.push('');
  lines.push('Artifacts:');
  Object.entries(report.artifacts).forEach(([name, filePath]) => {
    lines.push(`- ${name}: ${filePath || 'missing'}`);
  });
  return lines.join('\n');
}

const exitCode = Number(process.env.ANDROID_E2E_EXIT_CODE || '0');
const runId = process.env.ANDROID_E2E_RUN_ID || path.basename(runDir);
const stage = process.env.ANDROID_E2E_STAGE || 'unknown';
const appPackage = process.env.ANDROID_E2E_APP_ID || 'com.franklinharper.social.media.client';
const mainActivity = process.env.ANDROID_E2E_MAIN_ACTIVITY || `${appPackage}/.MainActivity`;
const fixtureUrl = process.env.ANDROID_E2E_FIXTURE_URL || 'http://10.0.2.2:9090/feeds/hn-frontpage.xml';
const serverHealthUrl = process.env.ANDROID_E2E_SERVER_HEALTH_URL || 'http://127.0.0.1:8080/health';
const fixtureHealthUrl = process.env.ANDROID_E2E_FIXTURE_HEALTH_URL || fixtureUrl;
const deviceSerial = process.env.ANDROID_E2E_DEVICE_SERIAL || '';
const avdName = process.env.ANDROID_E2E_AVD_NAME || 'AndroidE2E_API35_Pixel5';
const deviceProfile = process.env.ANDROID_E2E_DEVICE_PROFILE || 'pixel_5';
const startedServer = process.env.ANDROID_E2E_STARTED_SERVER === '1';
const startedFixture = process.env.ANDROID_E2E_STARTED_FIXTURE === '1';
const startedEmulator = process.env.ANDROID_E2E_STARTED_EMULATOR === '1';
const testRan = process.env.ANDROID_E2E_TEST_RAN === '1';

const appReport = loadAppReport(runDir);
const progress = loadProgress(runDir);
const instrumentationFailure = readInstrumentationFailure(runDir);
const crashException = readCrashException(runDir);
const logcatPath = path.join(runDir, 'logcat.txt');
const gradleLogPath = path.join(runDir, 'gradle.log');
const appReportPath = path.join(runDir, 'android-e2e-report.json');
const progressPath = path.join(runDir, 'android-e2e-progress.json');
const testOutputsDir = findLatestOutputDir(runDir);
const logcatText = readText(logcatPath);
const gradleLogText = readText(gradleLogPath);
const crashBlock = extractCrashBlock(logcatText);

let issues = normalizeIssues(appReport?.issues);
const steps = summarizeSteps(appReport?.steps);

if (crashBlock && !issues.some((issue) => issue.type === 'crash')) {
  issues.push(defaultIssue(
    progress?.screen || 'unknown',
    progress?.step || stage || 'unknown',
    'crash',
    'Android process crashed.',
    {
      exception: crashBlock,
      logExcerpt: crashBlock,
    },
  ));
}

if (instrumentationFailure && !issues.some((issue) => issue.type === 'crash' || issue.type === 'runner_failure')) {
  const type = instrumentationFailure.message.includes('Process crashed') ? 'crash' : 'runner_failure';
  issues.push(defaultIssue(
    progress?.screen || 'runner',
    progress?.step || stage,
    type,
    instrumentationFailure.message,
    {
      exception: crashException,
      logExcerpt: instrumentationFailure.logExcerpt,
    },
  ));
}

if (exitCode !== 0 && !testRan && !issues.some((issue) => issue.type === 'setup_failure')) {
  issues.push(defaultIssue(
    'runner',
    stage,
    'setup_failure',
    `Android E2E setup failed during ${stage}.`,
    {
      logExcerpt: shortExcerpt(gradleLogText || logcatText || '', 1200),
    },
  ));
}

if (exitCode !== 0 && testRan && issues.length === 0) {
  issues.push(defaultIssue(
    progress?.screen || 'runner',
    progress?.step || stage,
    'runner_failure',
    `Android E2E failed during ${stage}.`,
    {
      logExcerpt: shortExcerpt(gradleLogText || logcatText || '', 1200),
    },
  ));
}

const overallStatus = buildOverallStatus(exitCode, testRan, issues);
const report = {
  run: {
    runId,
    stage,
    startedAt: runIdToIsoTimestamp(runId),
    finishedAt: new Date().toISOString(),
    exitCode,
  },
  overallStatus,
  device: {
    serial: deviceSerial,
    avdName,
    deviceProfile,
    startedEmulator,
  },
  app: {
    packageId: appPackage,
    mainActivity,
    serverHealthUrl,
    fixtureHealthUrl,
    fixtureUrl,
    startedServer,
    startedFixture,
  },
  steps,
  issues,
  progress: progress || null,
  artifacts: {
    appReport: fs.existsSync(appReportPath) ? appReportPath : null,
    progress: fs.existsSync(progressPath) ? progressPath : null,
    logcat: fs.existsSync(logcatPath) ? logcatPath : null,
    gradleLog: fs.existsSync(gradleLogPath) ? gradleLogPath : null,
    androidTestResults: testOutputsDir,
  },
  instrumentation: {
    testRan,
    testOutputsDir,
  },
};

fs.writeFileSync(path.join(runDir, 'report.json'), `${JSON.stringify(report, null, 2)}\n`);
fs.writeFileSync(path.join(runDir, 'report.txt'), `${buildReportText(report)}\n`);
