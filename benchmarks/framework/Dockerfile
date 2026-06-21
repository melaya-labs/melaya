# Dockerfile — pinned-tier runner for the framework bench suite.
#
# Why this exists:
#   The headline numbers (especially the sub-microsecond `tool_dispatch`
#   p50) are sensitive to host noise: thermal throttling, frequency
#   scaling, hyperthreading contention, background processes. A
#   `docker run` against this image gives a user a reproducible
#   "this is what your hardware tier can do" number that strips most
#   of those variables — same python interpreter, same numpy build,
#   same minimal Debian userland, no host services.
#
# Usage:
#   docker build -t melaya-framework-bench .
#   docker run --rm -v "$PWD/results:/bench/results" melaya-framework-bench
#
# For tightest results pass `--cpus 1` and ideally `--cpuset-cpus 3`
# to dedicate one physical core (host must have at least 4 cores):
#   docker run --rm \
#       --cpus 1 --cpuset-cpus 3 \
#       -v "$PWD/results:/bench/results" \
#       melaya-framework-bench
#
# Pinned to python:3.12-slim-bookworm so the interpreter matches a
# user's machine exactly (no surprise CPython upgrade changing
# percentile numbers between two of your own runs).

FROM python:3.12-slim-bookworm

# Build deps: gcc for any numpy fallback wheels, procps so the bench
# can introspect /proc, ca-certificates so pip can talk to PyPI.
RUN apt-get update \
    && apt-get install -y --no-install-recommends \
        gcc \
        procps \
        ca-certificates \
    && rm -rf /var/lib/apt/lists/*

WORKDIR /bench

# Copy the bench package. We don't COPY the entire OSS repo to keep
# the image small (~250 MB vs ~600 MB with the SDKs).
COPY pyproject.toml /bench/pyproject.toml
COPY src           /bench/src
COPY benches       /bench/benches

# Editable install so the same `pytest benches/` invocation works
# inside and outside the container.
RUN pip install --no-cache-dir -e .

# Mounted by the user with `docker run -v`; the bench writes CSV +
# summary.json under here.
VOLUME ["/bench/results"]

# Print the env on container start so a reader can audit it.
CMD ["/bin/sh", "-c", "\
    echo '──────────────────────────────────────────────────────────────────' && \
    echo ' Melaya framework bench — Docker tier' && \
    echo ' image python   :' $(python --version) && \
    echo ' container cpus :' $(nproc) && \
    echo '──────────────────────────────────────────────────────────────────' && \
    pytest /bench/benches -s && \
    echo '' && \
    echo ' Results in ./results (mounted volume)' \
"]
