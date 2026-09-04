import Testing

@testable import PosatoMacOSHelper

@Test func givenExactHostWhenComparedThenOnlyEqualityMatches() {
  let selected: Set<String> = ["example.com"]

  #expect(ExactHostPolicy.matches(host: "example.com", selectedHosts: selected))
  #expect(ExactHostPolicy.matches(host: "EXAMPLE.COM", selectedHosts: selected))
  #expect(ExactHostPolicy.matches(host: "example.com.", selectedHosts: selected))
  #expect(!ExactHostPolicy.matches(host: "www.example.com", selectedHosts: selected))
  #expect(!ExactHostPolicy.matches(host: "example.org", selectedHosts: selected))
  #expect(!ExactHostPolicy.matches(host: "example.com.invalid", selectedHosts: selected))
  #expect(!ExactHostPolicy.matches(host: "example.com..", selectedHosts: selected))
}

@Test func givenIPLiteralWhenComparedThenItNeverMatchesAnExactDomain() {
  let selected: Set<String> = ["example.com", "127.0.0.1"]

  #expect(!ExactHostPolicy.matches(host: "127.0.0.1", selectedHosts: selected))
  #expect(!ExactHostPolicy.matches(host: "::1", selectedHosts: selected))
  #expect(!ExactHostPolicy.matches(host: "192.0.2.1", selectedHosts: ["192.0.2.1"]))
}
