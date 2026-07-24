# frozen_string_literal: true

require_relative "lib/melaya/version"

Gem::Specification.new do |spec|
  spec.name          = "melaya"
  spec.version       = Melaya::VERSION
  spec.authors       = ["Melaya"]
  spec.email         = ["sdk@melaya.org"]

  spec.summary       = "Official Ruby SDK for Melaya Agent Builder and Mobile Device Control"
  spec.description   = "Official Ruby SDK for Melaya Agent Builder and Mobile Device Control. Includes preview Melaya Trading namespaces that are not yet generally available."
  spec.homepage      = "https://melaya.org"
  spec.license       = "Apache-2.0"

  spec.metadata = {
    "homepage_uri"    => "https://melaya.org",
    "source_code_uri" => "https://github.com/melaya-labs/melaya",
    "changelog_uri"   => "https://github.com/melaya-labs/melaya/blob/main/CHANGELOG.md"
  }

  spec.required_ruby_version = ">= 3.0.0"

  spec.files         = Dir["lib/**/*.rb", "README.md", "LICENSE", "melaya.gemspec"]
  spec.require_paths = ["lib"]

  # stdlib only — no runtime gem dependencies
end
