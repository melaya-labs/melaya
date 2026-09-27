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
