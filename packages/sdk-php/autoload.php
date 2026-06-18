<?php
/**
 * Simple PSR-4 autoloader for Melaya\\ → src/.
 * Use this when Composer is not available (e.g. CI without vendor/).
 *
 * Usage:  require_once __DIR__ . '/autoload.php';
 */
spl_autoload_register(function (string $class): void {
    $prefix = 'Melaya\\';
    $baseDir = __DIR__ . '/src/';

    if (strncmp($prefix, $class, strlen($prefix)) !== 0) {
        return;
    }

    $relative = substr($class, strlen($prefix));
    $file = $baseDir . str_replace('\\', '/', $relative) . '.php';

    if (file_exists($file)) {
        require $file;
    }
});
