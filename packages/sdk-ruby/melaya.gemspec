# frozen_string_literal: true

require_relative "lib/melaya/version"

Gem::Specification.new do |spec|
  spec.name          = "melaya"
  spec.version       = Melaya::VERSION
  spec.authors       = ["Melaya"]
  spec.email         = ["sdk@melaya.org"]

  spec.summary       = "Official Ruby SDK for the Melaya unified market-data & trading API"
  spec.description   = "Access 70+ exchanges via Melaya's normalized REST and WebSocket API. " \
                       "Market data, strategies, backtesting, sim trading, and streaming."
  spec.homepage      = "https://melaya.org"
  spec.license       = "MIT"

  spec.required_ruby_version = ">= 3.0.0"

  spec.files         = Dir["lib/**/*.rb", "README.md", "melaya.gemspec"]
  spec.require_paths = ["lib"]

  # stdlib only — no runtime gem dependencies
end
