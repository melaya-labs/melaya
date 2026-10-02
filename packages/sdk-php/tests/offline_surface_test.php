<?php

declare(strict_types=1);

namespace Melaya;

/**
 * Offline unit test for the 0.3 surface additions (multipart upload, raw-bytes
 * GET, and a few representative bridged JSON calls). Runs with NO network and
 * NO ext-curl / ext-openssl, so it works in build environments (like this
 * machine) where `composer install` cannot fetch phpunit/phpunit (no TLS) and
 * ext-curl is not compiled in.
 *
 * Technique: HttpClient calls curl_init()/curl_setopt_array()/curl_exec()/...
 * UNQUALIFIED from within `namespace Melaya`. PHP resolves an unqualified
 * function call to the same-namespace function first, falling back to the
 * global one only if no such function exists. Defining Melaya\curl_*() below
 * (this file) therefore intercepts every cURL call HttpClient makes, without
 * touching src/ or adding a dependency — the same trick works whether or not
 * the real ext-curl is loaded. CURLOPT_* and CURLINFO_* are plain ints
 * normally supplied by ext-curl, so we define them too when missing.
 *
 * Run: php tests/offline_surface_test.php
 * Exit code 0 = all assertions passed.
 *
 * The SDK has no other unit-test scaffolding yet (the only existing test is
 * e2e/php_smoke.txt, a live smoke test against the real API); this file
 * mirrors that script's plain-PHP, no-framework reporting style (PASS/FAIL
 * lines + a summary + a process exit code) rather than inventing a new one.
 */

// ── cURL stand-in ────────────────────────────────────────────────────────────

if (!defined('CURLOPT_CUSTOMREQUEST'))  define('CURLOPT_CUSTOMREQUEST', 990001);
if (!defined('CURLOPT_RETURNTRANSFER')) define('CURLOPT_RETURNTRANSFER', 990002);
if (!defined('CURLOPT_TIMEOUT'))        define('CURLOPT_TIMEOUT', 990003);
if (!defined('CURLOPT_SSL_VERIFYPEER')) define('CURLOPT_SSL_VERIFYPEER', 990004);
if (!defined('CURLOPT_SSL_VERIFYHOST')) define('CURLOPT_SSL_VERIFYHOST', 990005);
if (!defined('CURLOPT_HTTPHEADER'))     define('CURLOPT_HTTPHEADER', 990006);
if (!defined('CURLOPT_HEADERFUNCTION')) define('CURLOPT_HEADERFUNCTION', 990007);
if (!defined('CURLOPT_POSTFIELDS'))     define('CURLOPT_POSTFIELDS', 990008);
if (!defined('CURLINFO_HTTP_CODE'))     define('CURLINFO_HTTP_CODE', 990009);

/** @var array<int, array{url: string, options: array<int, mixed>, status?: int}> */
$GLOBALS['__melaya_handles'] = [];
$GLOBALS['__melaya_next_handle'] = 1;
/** @var list<array{method: string, url: string, headers: list<string>, body: ?string}> */
$GLOBALS['__melaya_requests'] = [];
/** @var list<array{status: int, body: string}> */
$GLOBALS['__melaya_queue'] = [];

function curl_init(string $url = '')
{
    $h = $GLOBALS['__melaya_next_handle']++;
    $GLOBALS['__melaya_handles'][$h] = ['url' => $url, 'options' => []];
    return $h;
}

function curl_setopt_array($ch, array $options): bool
{
    foreach ($options as $k => $v) {
        $GLOBALS['__melaya_handles'][$ch]['options'][$k] = $v;
    }
    return true;
}

function curl_setopt($ch, $opt, $val): bool
{
    $GLOBALS['__melaya_handles'][$ch]['options'][$opt] = $val;
    return true;
}

function curl_exec($ch)
{
    $options = $GLOBALS['__melaya_handles'][$ch]['options'];
    // Exercise the header-capture closure exactly like real cURL would, one
    // header line at a time (retry-after parsing is covered indirectly by
    // whichever call queues a 429 — none of the tests below need that path).
    if (isset($options[CURLOPT_HEADERFUNCTION])) {
        ($options[CURLOPT_HEADERFUNCTION])($ch, "HTTP/1.1 200 OK\r\n");
    }
    $GLOBALS['__melaya_requests'][] = [
        'method'  => $options[CURLOPT_CUSTOMREQUEST] ?? 'GET',
        'url'     => $GLOBALS['__melaya_handles'][$ch]['url'],
        'headers' => $options[CURLOPT_HTTPHEADER] ?? [],
        'body'    => $options[CURLOPT_POSTFIELDS] ?? null,
    ];
    $resp = array_shift($GLOBALS['__melaya_queue']) ?? ['status' => 200, 'body' => '{}'];
    $GLOBALS['__melaya_handles'][$ch]['status'] = $resp['status'];
    return $resp['body'];
}

function curl_getinfo($ch, $opt = null)
{
    if ($opt === CURLINFO_HTTP_CODE) {
        return $GLOBALS['__melaya_handles'][$ch]['status'] ?? 200;
    }
    return null;
}

function curl_error($ch): string
{
    return '';
}

function curl_close($ch): void
{
    unset($GLOBALS['__melaya_handles'][$ch]);
}

// ── Test helpers ─────────────────────────────────────────────────────────────

function melaya_test_queue(int $status, string $body): void
{
    $GLOBALS['__melaya_queue'][] = ['status' => $status, 'body' => $body];
}

/** @return array{method: string, url: string, headers: list<string>, body: ?string} */
function melaya_test_last_request(): array
{
    $r = end($GLOBALS['__melaya_requests']);
    if ($r === false) {
        throw new \RuntimeException('No request was captured');
    }
    return $r;
}

function melaya_test_reset(): void
{
    $GLOBALS['__melaya_requests'] = [];
    $GLOBALS['__melaya_queue'] = [];
}

/** @param list<string> $headers */
function melaya_header(array $headers, string $name): ?string
{
    $needle = strtolower($name) . ':';
    foreach ($headers as $h) {
        if (str_starts_with(strtolower($h), $needle)) {
            return trim(substr($h, strlen($needle)));
        }
    }
    return null;
}

/** @var list<array{0: string, 1: bool, 2: string}> */
$RESULTS = [];

function check(string $name, bool $condition, string $detail = ''): void
{
    global $RESULTS;
    $RESULTS[] = [$name, $condition, $detail];
    echo ($condition ? 'PASS' : 'FAIL') . '  ' . $name . ($detail !== '' ? "  — {$detail}" : '') . PHP_EOL;
}

// ── SDK under test ───────────────────────────────────────────────────────────

require __DIR__ . '/../autoload.php';

$sdk = new Melaya(apiKey: 'mk_test_offline_key', baseUrl: 'https://api.test.invalid');

// 1) pipelines->run() sends run_inputs in the JSON body, echoes it back ------
melaya_test_reset();
melaya_test_queue(200, json_encode([
    'run_id'     => 'run123',
    'queued'     => true,
    'run_inputs' => ['brief' => 'hi'],
]));
$runResult = $sdk->agents->pipelines->run('my-pipe', [
    'project'    => 'acme',
    'run_inputs' => ['brief' => 'Summarize', 'values' => ['report' => ['file_id' => 'f1']]],
]);
$req  = melaya_test_last_request();
$body = json_decode((string) $req['body'], true);
check('pipelines.run: POST to /pipelines/{name}/run', $req['method'] === 'POST'
    && str_ends_with($req['url'], '/api/v1/private/pipelines/my-pipe/run'));
check('pipelines.run: run_inputs.brief in JSON body', ($body['run_inputs']['brief'] ?? null) === 'Summarize');
check('pipelines.run: run_inputs.values.<key>.file_id in JSON body',
    ($body['run_inputs']['values']['report']['file_id'] ?? null) === 'f1');
check('pipelines.run: Content-Type: application/json', melaya_header($req['headers'], 'Content-Type') === 'application/json');
check('pipelines.run: run_inputs echoed back in the result', ($runResult['run_inputs']['brief'] ?? null) === 'hi');

// 2) pipelines->uploadRunFile() builds real multipart/form-data --------------
melaya_test_reset();
melaya_test_queue(200, json_encode(['file_id' => 'file_abc']));
$upload = $sdk->agents->pipelines->uploadRunFile('my-pipe', 'report', "%PDF-1.4 fake bytes\x00\x01\x02", [
    'filename'    => 'report.pdf',
    'contentType' => 'application/pdf',
    'project'     => 'acme',
]);
$req = melaya_test_last_request();
$ct  = melaya_header($req['headers'], 'Content-Type');
check('uploadRunFile: POST to /pipelines/{name}/run-files', $req['method'] === 'POST'
    && str_contains($req['url'], '/api/v1/private/pipelines/my-pipe/run-files'));
check('uploadRunFile: query carries key + project', str_contains($req['url'], 'key=report') && str_contains($req['url'], 'project=acme'));
check('uploadRunFile: Content-Type is multipart/form-data with a boundary',
    $ct !== null && str_starts_with($ct, 'multipart/form-data; boundary='));
check('uploadRunFile: body has the file part (field name + filename)',
    str_contains((string) $req['body'], 'name="file"; filename="report.pdf"'));
check('uploadRunFile: body carries the raw file bytes', str_contains((string) $req['body'], '%PDF-1.4 fake bytes'));
check('uploadRunFile: returns the file_id', ($upload['file_id'] ?? null) === 'file_abc');

// 3) pipelines->runInputFile() returns raw bytes, never JSON-decoded ---------
melaya_test_reset();
$rawBytes = "\x89PNG\r\n\x1a\nnot-json-{-or-}-content";
melaya_test_queue(200, $rawBytes);
$bytes = $sdk->agents->pipelines->runInputFile('my-pipe', '0123456789abcdef', 0);
$req   = melaya_test_last_request();
check('runInputFile: GET /pipelines/{name}/runs/{runId}/inputs/files/{index}',
    str_ends_with($req['url'], '/api/v1/private/pipelines/my-pipe/runs/0123456789abcdef/inputs/files/0'));
check('runInputFile: returns the exact raw bytes untouched', $bytes === $rawBytes);

// 4) pipelines->projectToolCalls() encodes query params correctly -----------
melaya_test_reset();
melaya_test_queue(200, json_encode(['items' => [], 'nextCursor' => null, 'capped' => false]));
$sdk->agents->pipelines->projectToolCalls('my project', [
    'limit'  => 30,
    'status' => 'ok',
    'sort'   => 'recent',
    'tool'   => null,   // must be dropped, not sent as tool=
    'agent'  => '',     // must be dropped, not sent as agent=
]);
$req = melaya_test_last_request();
check('projectToolCalls: GET /projects/{project}/tool-calls (project rawurlencoded)',
    $req['method'] === 'GET' && str_contains($req['url'], '/api/v1/private/projects/my%20project/tool-calls'));
check('projectToolCalls: limit/status/sort in the query string',
    str_contains($req['url'], 'limit=30') && str_contains($req['url'], 'status=ok') && str_contains($req['url'], 'sort=recent'));
check('projectToolCalls: null/empty params are dropped, not sent empty',
    !str_contains($req['url'], 'tool=') && !str_contains($req['url'], 'agent='));

// 5) connectors->applyPersonal() — bridged POST, path param + JSON body -----
melaya_test_reset();
melaya_test_queue(200, json_encode(['ok' => true]));
$sdk->platform->connectors->applyPersonal('acme', 'openai', ['gmail', 'calendar']);
$req  = melaya_test_last_request();
$body = json_decode((string) $req['body'], true);
check('applyPersonal: POST /projects/{project}/connectors/{service}/apply-personal',
    $req['method'] === 'POST' && str_contains($req['url'], '/api/v1/private/projects/acme/connectors/openai/apply-personal'));
check('applyPersonal: googleCapabilities carried in the JSON body',
    ($body['googleCapabilities'] ?? null) === ['gmail', 'calendar']);

// 6) credentials->googleDisconnect() — DELETE with a JSON body --------------
melaya_test_reset();
melaya_test_queue(200, json_encode(['ok' => true]));
$sdk->platform->credentials->googleDisconnect('507f1f77bcf86cd799439011', 'gmail');
$req  = melaya_test_last_request();
$body = json_decode((string) $req['body'], true);
check('googleDisconnect: DELETE /credentials/google/access', $req['method'] === 'DELETE'
    && str_ends_with($req['url'], '/api/v1/private/credentials/google/access'));
check('googleDisconnect: accountId + capability carried in the JSON body (not the query string)',
    ($body['accountId'] ?? null) === '507f1f77bcf86cd799439011' && ($body['capability'] ?? null) === 'gmail');
check('googleDisconnect: Content-Type: application/json', melaya_header($req['headers'], 'Content-Type') === 'application/json');
check('googleDisconnect: credential never leaks into the query string',
    !str_contains($req['url'], 'accountId=') && !str_contains($req['url'], 'mk_test_offline_key'));

// 7) connectorTools->search() — query encoding (spaces, limit) ---------------
melaya_test_reset();
melaya_test_queue(200, json_encode(['query' => 'unread email', 'services' => ['gmail'], 'tools' => []]));
$sdk->agents->connectorTools->search('unread email', 5);
$req = melaya_test_last_request();
check('connectorTools.search: GET /connector-tools/search', $req['method'] === 'GET'
    && str_contains($req['url'], '/api/v1/private/connector-tools/search'));
check('connectorTools.search: q is percent-encoded in the query string',
    str_contains($req['url'], 'q=unread+email') || str_contains($req['url'], 'q=unread%20email'));
check('connectorTools.search: limit carried in the query string', str_contains($req['url'], 'limit=5'));

// 8) connectorTools->call() — a read tool runs immediately (200) -------------
melaya_test_reset();
melaya_test_queue(200, json_encode(['status' => 'done', 'tool' => 'gmail_list_messages', 'readOnly' => true, 'result' => 'read:gmail_list_messages']));
$readResult = $sdk->agents->connectorTools->call('gmail_list_messages');
$req  = melaya_test_last_request();
$body = json_decode((string) $req['body'], true);
check('connectorTools.call: POST /connector-tools/call', $req['method'] === 'POST'
    && str_ends_with($req['url'], '/api/v1/private/connector-tools/call'));
check('connectorTools.call: approval defaults to "required" in the JSON body', ($body['approval'] ?? null) === 'required');
check('connectorTools.call: a read tool returns status "done" with its result',
    ($readResult['status'] ?? null) === 'done' && ($readResult['result'] ?? null) === 'read:gmail_list_messages');

// 9) connectorTools->call() — a staged write returns its 202 body, not thrown -
melaya_test_reset();
melaya_test_queue(202, json_encode(['status' => 'pending_approval', 'tool' => 'gmail_send', 'requestId' => 'req-1', 'message' => 'Approve it in the app.']));
$staged = $sdk->agents->connectorTools->call('gmail_send', ['to' => 'a@b.c']);
check('connectorTools.call: HTTP 202 is returned as data, not thrown as an exception',
    ($staged['status'] ?? null) === 'pending_approval' && ($staged['requestId'] ?? null) === 'req-1');

// 10) connectorTools->callStatus() — a decided outcome -----------------------
melaya_test_reset();
melaya_test_queue(200, json_encode(['requestId' => 'req-1', 'tool' => 'gmail_send', 'status' => 'done', 'ok' => true, 'result' => 'wrote:gmail_send']));
$status = $sdk->agents->connectorTools->callStatus('req-1');
$req = melaya_test_last_request();
check('connectorTools.callStatus: GET /connector-tools/calls/{requestId}',
    $req['method'] === 'GET' && str_ends_with($req['url'], '/api/v1/private/connector-tools/calls/req-1'));
check('connectorTools.callStatus: status "done" with ok + result', $status['status'] === 'done'
    && $status['ok'] === true && $status['result'] === 'wrote:gmail_send');

// 11) connectorTools->call() — a money-moving write raises the SDK's error ---
melaya_test_reset();
melaya_test_queue(403, json_encode(['error' => 'money_moving_requires_app_approval', 'message' => 'Tools that move money or trade run only from the Melaya app.']));
try {
    $sdk->agents->connectorTools->call('stripe_create_refund', [], 'none');
    check('connectorTools.call: money-moving write throws MelayaException', false, 'no exception was thrown');
} catch (MelayaException $e) {
    check('connectorTools.call: money-moving write throws MelayaException', true);
    check('connectorTools.call: exception carries the money-moving error code',
        $e->errorCode === 'money_moving_requires_app_approval' && $e->status === 403);
}

// 12) connectorTools->callAndWait() — polls a tiny interval down to "done" ---
melaya_test_reset();
melaya_test_queue(202, json_encode(['status' => 'pending_approval', 'tool' => 'gmail_send', 'requestId' => 'req-2', 'message' => 'Approve it in the app.']));
melaya_test_queue(200, json_encode(['requestId' => 'req-2', 'tool' => 'gmail_send', 'status' => 'pending']));
melaya_test_queue(200, json_encode(['requestId' => 'req-2', 'tool' => 'gmail_send', 'status' => 'pending']));
melaya_test_queue(200, json_encode(['requestId' => 'req-2', 'tool' => 'gmail_send', 'status' => 'done', 'ok' => true, 'result' => 'wrote:gmail_send']));
$outcome = $sdk->agents->connectorTools->callAndWait('gmail_send', ['to' => 'a@b.c'], 'required', 1, 60_000);
check('connectorTools.callAndWait: polls callStatus repeatedly, then returns the "done" outcome',
    $outcome['status'] === 'done' && $outcome['ok'] === true && $outcome['result'] === 'wrote:gmail_send');
check('connectorTools.callAndWait: made exactly 4 requests (1 call + 3 polls)', count($GLOBALS['__melaya_requests']) === 4);

// 13) triggers — every read, dry run and poll control ------------------------
$T = '/api/v1/private/triggers';
check('triggers: exposed on agents and as a flat alias', $sdk->agents->triggers === $sdk->triggers);

melaya_test_reset();
melaya_test_queue(200, json_encode([['id' => 't1', 'kind' => 'webhook', 'enabled' => true, 'webhookUrl' => 'https://x/hooks/abc']]));
$list = $sdk->agents->triggers->list(['project' => 'support', 'pipelineName' => 'refunds']);
$req  = melaya_test_last_request();
check('triggers.list: GET /triggers with project + pipelineName', $req['method'] === 'GET'
    && str_contains($req['url'], $T . '?') && str_contains($req['url'], 'project=support')
    && str_contains($req['url'], 'pipelineName=refunds'));
check('triggers.list: decodes the records', ($list[0]['id'] ?? null) === 't1' && $list[0]['enabled'] === true);

melaya_test_reset();
melaya_test_queue(200, json_encode(['id' => 'a/b', 'kind' => 'poll']));
$one = $sdk->agents->triggers->get('a/b');
$req = melaya_test_last_request();
check('triggers.get: GET /triggers/{id} (id rawurlencoded)', $req['method'] === 'GET'
    && str_ends_with($req['url'], $T . '/a%2Fb') && $one['kind'] === 'poll');

melaya_test_reset();
melaya_test_queue(200, json_encode([['id' => 'd1', 'verdict' => 'dispatched']]));
$rows = $sdk->agents->triggers->deliveries('t1', 20);
$req  = melaya_test_last_request();
check('triggers.deliveries: GET /triggers/{id}/deliveries?limit=', $req['method'] === 'GET'
    && str_ends_with($req['url'], $T . '/t1/deliveries?limit=20') && $rows[0]['verdict'] === 'dispatched');

melaya_test_reset();
melaya_test_queue(200, json_encode([]));
$sdk->agents->triggers->deliveries('t1');
check('triggers.deliveries: no limit sends no query string', str_ends_with(melaya_test_last_request()['url'], $T . '/t1/deliveries'));

melaya_test_reset();
melaya_test_queue(200, json_encode(['hours' => 48, 'byVerdict' => ['dispatched' => ['n' => 12, 'p50' => 120.5, 'p95' => 900]], 'filtered' => 3, 'sampled' => false]));
$stats = $sdk->agents->triggers->stats('t1', 48);
$req   = melaya_test_last_request();
check('triggers.stats: GET /triggers/{id}/stats?hours=', $req['method'] === 'GET'
    && str_ends_with($req['url'], $T . '/t1/stats?hours=48'));
check('triggers.stats: decodes byVerdict', $stats['byVerdict']['dispatched']['n'] === 12 && $stats['filtered'] === 3);

melaya_test_reset();
melaya_test_queue(200, json_encode([['requestId' => 'r1']]));
$appr = $sdk->agents->triggers->pendingApprovals('t1');
$req  = melaya_test_last_request();
check('triggers.pendingApprovals: GET /triggers/{id}/approvals', $req['method'] === 'GET'
    && str_ends_with($req['url'], $T . '/t1/approvals') && $appr[0]['requestId'] === 'r1');

melaya_test_reset();
melaya_test_queue(200, json_encode(['accepted' => false, 'eventId' => 'test-1', 'reason' => 'rate_limited']));
$test = $sdk->agents->triggers->test('t1', ['type' => 'refund.created', 'amount' => 12]);
$req  = melaya_test_last_request();
$body = json_decode((string) $req['body'], true);
check('triggers.test: POST /triggers/{id}/test', $req['method'] === 'POST' && str_ends_with($req['url'], $T . '/t1/test'));
check('triggers.test: payload carried in the JSON body', ($body['payload']['type'] ?? null) === 'refund.created'
    && ($body['payload']['amount'] ?? null) === 12);
check('triggers.test: decodes accepted/eventId/reason', $test['accepted'] === false && $test['eventId'] === 'test-1'
    && $test['reason'] === 'rate_limited');

melaya_test_reset();
melaya_test_queue(200, json_encode(['accepted' => true, 'eventId' => 'test-2']));
$sdk->agents->triggers->test('t1');
check('triggers.test: no payload sends an empty JSON object', melaya_test_last_request()['body'] === '{}');

melaya_test_reset();
melaya_test_queue(200, json_encode([
    'events'    => [['triggerId' => 't1', 'deliveryId' => null, 'eventId' => 'e1', 'source' => 'webhook', 'verdict' => 'rejected', 'at' => 1790000000000]],
    'scanned'   => 40,
    'retention' => ['maxEvents' => 500, 'ttlSec' => 86400],
]));
$live = $sdk->agents->triggers->events(['triggerId' => 't1', 'since' => 1789999999000, 'verdicts' => ['rejected', 'failed'], 'limit' => 50]);
$req  = melaya_test_last_request();
check('triggers.events: GET /triggers/events', $req['method'] === 'GET' && str_contains($req['url'], $T . '/events?'));
check('triggers.events: triggerId/since/limit in the query string', str_contains($req['url'], 'triggerId=t1')
    && str_contains($req['url'], 'since=1789999999000') && str_contains($req['url'], 'limit=50'));
check('triggers.events: verdicts sent as a comma list', str_contains($req['url'], 'verdicts=rejected%2Cfailed'));
check('triggers.events: decodes events + retention', $live['events'][0]['verdict'] === 'rejected'
    && $live['events'][0]['deliveryId'] === null && $live['retention']['maxEvents'] === 500);

melaya_test_reset();
melaya_test_queue(200, json_encode(['synced' => true, 'status' => 'ok', 'lastError' => null, 'armed' => true, 'baselinePending' => false,
    'seenCount' => 17, 'itemsPublished' => 4, 'consecutiveErrors' => 0, 'requestedIntervalSec' => 60, 'effectiveIntervalSec' => 300, 'tierFloorSec' => 300]));
$ps  = $sdk->agents->triggers->pollStatus('t1');
$req = melaya_test_last_request();
check('triggers.pollStatus: GET /triggers/{id}/poll', $req['method'] === 'GET' && str_ends_with($req['url'], $T . '/t1/poll'));
check('triggers.pollStatus: decodes state', $ps['synced'] === true && $ps['effectiveIntervalSec'] === 300);

melaya_test_reset();
melaya_test_queue(200, json_encode(['dry' => true, 'ok' => true, 'found' => 3, 'baseline' => false, 'wouldPublish' => 2,
    'items' => [['id' => 'i1', 'preview' => 'a']], 'samplePayload' => ['title' => 'a']]));
$dry  = $sdk->agents->triggers->pollTest('t1');
$req  = melaya_test_last_request();
$body = json_decode((string) $req['body'], true);
check('triggers.pollTest: POST /triggers/{id}/poll/test with dry=true', $req['method'] === 'POST'
    && str_ends_with($req['url'], $T . '/t1/poll/test') && ($body['dry'] ?? null) === true);
check('triggers.pollTest: decodes found/wouldPublish/items', $dry['found'] === 3 && $dry['wouldPublish'] === 2
    && $dry['items'][0]['id'] === 'i1' && $dry['samplePayload']['title'] === 'a');

melaya_test_reset();
melaya_test_queue(200, json_encode(['dry' => true, 'ok' => false, 'error' => 'tool_failed']));
try {
    $failed = $sdk->agents->triggers->pollTest('t1');
    check('triggers.pollTest: a failed dry poll is returned as data, not thrown',
        $failed['ok'] === false && $failed['error'] === 'tool_failed');
} catch (MelayaException $e) {
    check('triggers.pollTest: a failed dry poll is returned as data, not thrown', false, 'threw ' . $e->errorCode);
}

melaya_test_reset();
melaya_test_queue(429, json_encode(['error' => 'poll_dry_run_throttled']));
try {
    $sdk->agents->triggers->pollTest('t1');
    check('triggers.pollTest: throttled dry poll throws MelayaException', false, 'no exception was thrown');
} catch (MelayaException $e) {
    check('triggers.pollTest: throttled dry poll throws MelayaException',
        $e->status === 429 && $e->errorCode === 'poll_dry_run_throttled');
}

melaya_test_reset();
melaya_test_queue(200, json_encode(['dry' => false, 'queued' => true]));
$now  = $sdk->agents->triggers->pollNow('t1');
$req  = melaya_test_last_request();
$body = json_decode((string) $req['body'], true);
check('triggers.pollNow: POST /triggers/{id}/poll/test with dry=false', $req['method'] === 'POST'
    && str_ends_with($req['url'], $T . '/t1/poll/test') && ($body['dry'] ?? null) === false);
check('triggers.pollNow: decodes queued', $now['queued'] === true);

melaya_test_reset();
melaya_test_queue(200, json_encode(['result' => 'armed']));
$sync = $sdk->agents->triggers->pollSync('t1');
$req  = melaya_test_last_request();
check('triggers.pollSync: POST /triggers/{id}/poll/sync', $req['method'] === 'POST'
    && str_ends_with($req['url'], $T . '/t1/poll/sync') && $sync['result'] === 'armed');

foreach ([['presets', '/presets', ['tier' => 'pro', 'tierFloorSec' => 60, 'presets' => [], 'beta' => ['allowed' => true]], 'tier'],
          ['limits', '/limits', ['tierClass' => 'pro', 'triggers' => ['used' => 2, 'cap' => 20]], 'tierClass'],
          ['sources', '/sources', [['id' => 's1']], 0]] as [$method, $path, $resp, $key]) {
    melaya_test_reset();
    melaya_test_queue(200, json_encode($resp));
    $out = $sdk->agents->triggers->{$method}();
    $req = melaya_test_last_request();
    check("triggers.{$method}: GET /triggers{$path}", $req['method'] === 'GET' && str_ends_with($req['url'], $T . $path)
        && isset($out[$key]));
}

// 14) Several accounts per connector, API key, docs/retrieval previews -------

/**
 * Run one call against a fresh queue and return [request, decodedBody].
 *
 * @return array{0: array{method: string, url: string, headers: list<string>, body: ?string}, 1: mixed, 2: mixed}
 */
function melaya_call(callable $fn, string $response = '{}'): array
{
    melaya_test_reset();
    melaya_test_queue(200, $response);
    $out = $fn();
    $req = melaya_test_last_request();
    return [$req, $req['body'] === null ? null : json_decode($req['body'], true), $out];
}

/** Path + query part of a captured URL (base URL stripped). */
function melaya_path(array $req): string
{
    return substr($req['url'], strlen('https://api.test.invalid'));
}

$C  = '/api/v1/private/credentials';
$PC = '/api/v1/private/projects';
$accList = json_encode([['id' => 'a1', 'label' => 'Shop EU', 'isDefault' => true, 'createdAt' => null]]);

// personal accounts
[$req, , $out] = melaya_call(fn() => $sdk->platform->credentials->accounts('shopify'), $accList);
check('credentials.accounts: GET /credentials/{service}/accounts',
    $req['method'] === 'GET' && melaya_path($req) === $C . '/shopify/accounts');
check('credentials.accounts: decodes the list', $out[0]['id'] === 'a1' && $out[0]['isDefault'] === true
    && $out[0]['createdAt'] === null);

[$req] = melaya_call(fn() => $sdk->platform->credentials->accounts('my service'), '[]');
check('credentials.accounts: service with a space is rawurlencoded', melaya_path($req) === $C . '/my%20service/accounts');

[$req, $body] = melaya_call(fn() => $sdk->platform->credentials->addAccount('shopify', ['token' => 'x'], 'Shop US', 'Shop EU', true), $accList);
check('credentials.addAccount: POST /credentials/{service}/accounts',
    $req['method'] === 'POST' && melaya_path($req) === $C . '/shopify/accounts');
check('credentials.addAccount: body {label, fields, currentLabel, makeDefault}',
    $body === ['label' => 'Shop US', 'fields' => ['token' => 'x'], 'currentLabel' => 'Shop EU', 'makeDefault' => true]);

[$req, $body] = melaya_call(fn() => $sdk->platform->credentials->addAccount('shopify', ['token' => 'x']), $accList);
check('credentials.addAccount: null options are omitted', $body === ['fields' => ['token' => 'x']]);

[$req, $body] = melaya_call(fn() => $sdk->platform->credentials->setDefaultAccount('shopify', 'a 2'), $accList);
check('credentials.setDefaultAccount: PUT /credentials/{service}/accounts/default {accountId}',
    $req['method'] === 'PUT' && melaya_path($req) === $C . '/shopify/accounts/default' && $body === ['accountId' => 'a 2']);

[$req] = melaya_call(fn() => $sdk->platform->credentials->identifyAccount('shopify', 'a 2'), $accList);
check('credentials.identifyAccount: POST /credentials/{service}/accounts/{id}/identify (id rawurlencoded)',
    $req['method'] === 'POST' && melaya_path($req) === $C . '/shopify/accounts/a%202/identify');
check('credentials.identifyAccount: body is a JSON object {}', $req['body'] === '{}');

[$req, $body] = melaya_call(fn() => $sdk->platform->credentials->renameAccount('shopify', 'a1', 'Main shop'), $accList);
check('credentials.renameAccount: PUT /credentials/{service}/accounts/{id} {label}',
    $req['method'] === 'PUT' && melaya_path($req) === $C . '/shopify/accounts/a1' && $body === ['label' => 'Main shop']);

[$req] = melaya_call(fn() => $sdk->platform->credentials->removeAccount('shopify', 'a1'), '[]');
check('credentials.removeAccount: DELETE /credentials/{service}/accounts/{id}',
    $req['method'] === 'DELETE' && melaya_path($req) === $C . '/shopify/accounts/a1');

// project accounts
[$req, , $out] = melaya_call(fn() => $sdk->platform->connectors->accounts('my project', 'shopify'), $accList);
check('connectors.accounts: GET /projects/{project}/connectors/{service}/accounts (project rawurlencoded)',
    $req['method'] === 'GET' && melaya_path($req) === $PC . '/my%20project/connectors/shopify/accounts'
    && $out[0]['label'] === 'Shop EU');

[$req, $body] = melaya_call(fn() => $sdk->platform->connectors->addAccount('acme', 'shopify', ['token' => 'x'], 'Shop US', null, false), $accList);
check('connectors.addAccount: POST /projects/{project}/connectors/{service}/accounts',
    $req['method'] === 'POST' && melaya_path($req) === $PC . '/acme/connectors/shopify/accounts');
check('connectors.addAccount: body carries label/fields/makeDefault, null currentLabel omitted',
    $body === ['label' => 'Shop US', 'fields' => ['token' => 'x'], 'makeDefault' => false]);

[$req, $body] = melaya_call(fn() => $sdk->platform->connectors->setDefaultAccount('acme', 'shopify', 'a1'), $accList);
check('connectors.setDefaultAccount: PUT .../accounts/default {accountId}',
    $req['method'] === 'PUT' && melaya_path($req) === $PC . '/acme/connectors/shopify/accounts/default'
    && $body === ['accountId' => 'a1']);

[$req, $body] = melaya_call(fn() => $sdk->platform->connectors->renameAccount('acme', 'shopify', 'a/1', 'Main'), $accList);
check('connectors.renameAccount: PUT .../accounts/{id} {label} (id rawurlencoded)',
    $req['method'] === 'PUT' && melaya_path($req) === $PC . '/acme/connectors/shopify/accounts/a%2F1'
    && $body === ['label' => 'Main']);

[$req] = melaya_call(fn() => $sdk->platform->connectors->removeAccount('acme', 'shopify', 'a1'), '[]');
check('connectors.removeAccount: DELETE .../accounts/{id}',
    $req['method'] === 'DELETE' && melaya_path($req) === $PC . '/acme/connectors/shopify/accounts/a1');

// platform API key
[$req, , $out] = melaya_call(fn() => $sdk->trading->account->rotateApiKey(), json_encode(['apiKey' => 'mk_new']));
check('account.rotateApiKey: POST /api-key with a JSON object body {}',
    $req['method'] === 'POST' && melaya_path($req) === '/api/v1/private/api-key' && $req['body'] === '{}');
check('account.rotateApiKey: returns the new apiKey', $out['apiKey'] === 'mk_new');

[$req, , $out] = melaya_call(fn() => $sdk->trading->account->revokeApiKey(), json_encode(['ok' => true]));
check('account.revokeApiKey: DELETE /api-key',
    $req['method'] === 'DELETE' && melaya_path($req) === '/api/v1/private/api-key' && $out['ok'] === true);

[$req] = melaya_call(fn() => $sdk->trading->account->apiKeyUsage(), json_encode(['requests' => 3]));
check('account.apiKeyUsage: GET /api-key/usage',
    $req['method'] === 'GET' && melaya_path($req) === '/api/v1/private/api-key/usage');

// pipelines docs / retrieval / inputs / run messages
$PP = '/api/v1/private/pipelines';
[$req] = melaya_call(fn() => $sdk->agents->pipelines->docsPreview('my pipe', 'qwen3.7-plus', 'qwen'));
check('pipelines.docsPreview: GET /pipelines/{name}/docs/preview with model_name + model_provider',
    $req['method'] === 'GET' && melaya_path($req) === $PP . '/my%20pipe/docs/preview?model_name=qwen3.7-plus&model_provider=qwen');

[$req] = melaya_call(fn() => $sdk->agents->pipelines->docsPreview('pipe'));
check('pipelines.docsPreview: null model params are omitted', melaya_path($req) === $PP . '/pipe/docs/preview');

[$req] = melaya_call(fn() => $sdk->agents->pipelines->retrievalPreview('my pipe'));
check('pipelines.retrievalPreview: GET /pipelines/{name}/docs/retrieval/preview',
    $req['method'] === 'GET' && melaya_path($req) === $PP . '/my%20pipe/docs/retrieval/preview');

[$req, $body] = melaya_call(fn() => $sdk->agents->pipelines->testRetrieve('my pipe', 'refund policy', 3));
check('pipelines.testRetrieve: POST /pipelines/{name}/docs/retrieval/test_retrieve {query, limit}',
    $req['method'] === 'POST' && melaya_path($req) === $PP . '/my%20pipe/docs/retrieval/test_retrieve'
    && $body === ['query' => 'refund policy', 'limit' => 3]);

[$req, $body] = melaya_call(fn() => $sdk->agents->pipelines->testRetrieve('pipe', 'q'));
check('pipelines.testRetrieve: limit omitted when null', $body === ['query' => 'q']);

$decl = [['key' => 'brief_doc', 'label' => 'Brief', 'type' => 'file', 'required' => true]];
[$req, $body] = melaya_call(fn() => $sdk->agents->pipelines->setInputs('my pipe', 'acme', $decl));
check('pipelines.setInputs: PUT /pipelines/{name}/inputs {inputs, project}',
    $req['method'] === 'PUT' && melaya_path($req) === $PP . '/my%20pipe/inputs'
    && $body === ['inputs' => $decl, 'project' => 'acme']);

[$req] = melaya_call(fn() => $sdk->agents->hitl->runMessages('run 1', ['limit' => 10]), json_encode(['messages' => []]));
check('hitl.runMessages: GET /runs/{runId}/messages (runId rawurlencoded, params in query)',
    $req['method'] === 'GET' && melaya_path($req) === '/api/v1/private/runs/run%201/messages?limit=10');

// ── Summary ──────────────────────────────────────────────────────────────────

$fail = 0;
foreach ($RESULTS as [, $pass]) {
    if (!$pass) {
        $fail++;
    }
}
echo PHP_EOL . str_repeat('=', 72) . PHP_EOL;
echo 'TOTAL ' . count($RESULTS) . '   PASS ' . (count($RESULTS) - $fail) . '   FAIL ' . $fail . PHP_EOL;
echo $fail === 0 ? 'RESULT: GO' . PHP_EOL : 'RESULT: NO-GO' . PHP_EOL;
exit($fail === 0 ? 0 : 1);
