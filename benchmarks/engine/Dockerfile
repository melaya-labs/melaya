# Dockerfile — pinned-tier runner for state_ticker bench.
#
# Why this exists:
#   The headline `state_ticker_ns` number is sensitive to host noise:
#   thermal throttling on a laptop, frequency scaling, hyperthreading
#   contention, etc. A `docker run` against this image gives a user a
#   reproducible "this is what your hardware tier can do" number that
#   strips most of those variables — same rustc, same release flags,
#   same minimal Alpine userland, no background services.
#
# Usage:
#   docker build -t melaya-engine-bench .
#   docker run --rm -v "$PWD/results:/bench/results" melaya-engine-bench
#
# For tightest results pass `--cpus 1` and ideally `--cpuset-cpus 3`
# (host has at least 4 cores) to dedicate one physical core.
#
#   docker run --rm \
#       --cpus 1 --cpuset-cpus 3 \
#       -v "$PWD/results:/bench/results" \
#       melaya-engine-bench
#
# The image is intentionally pinned to rust:1.94-bookworm-slim so the
# toolchain match a user's machine exactly (no surprise rustc upgrade
# changing codegen between two of your own runs).

FROM rust:1.94-slim-bookworm

# Build deps: nothing beyond what's in the base image; the bench has no
# C dependencies. We DO install `procps` so the bench can introspect
# governor / cores via /proc files we don't strip from the image.
RUN apt-get update \
    && apt-get install -y --no-install-recommends procps ca-certificates \
    && rm -rf /var/lib/apt/lists/*

WORKDIR /bench

# Copy the bench crate. We don't COPY the whole OSS repo to keep the
# image small (~250 MB instead of ~600 MB with the SDKs).
COPY Cargo.toml /bench/Cargo.toml
COPY src        /bench/src
COPY benches    /bench/benches

# Pre-fetch + pre-build deps so the bench run itself is fast.
RUN cargo build --release --bench state_ticker

# Default `cargo bench` writes into ./results — we mount this from the
# host in `docker run -v` so the CSV + summary land outside the container.
VOLUME ["/bench/results"]

# Print the env on container start so the user can audit it.
CMD ["/bin/sh", "-c", "\
    echo '──────────────────────────────────────────────────────────────────' && \
    echo ' Melaya engine bench — Docker tier' && \
    echo ' image rustc :' $(rustc --version) && \
    echo ' container cpu count :' $(nproc) && \
    echo '──────────────────────────────────────────────────────────────────' && \
    cargo bench --bench state_ticker && \
    echo '' && \
    echo ' Results in ./results (mounted volume)' \
"]
