import Testing

@testable import PosatoMacOSHelper

// Shared with ExactDomainPolicyTest and IosEnforcementTests: host → counterpart.
private let wwwCounterpartVector: [(String, String)] = [
  ("example.com", "www.example.com"),
  ("www.example.com", "example.com"),
  ("news.example.com", "www.news.example.com"),
  ("www.news.example.com", "news.example.com"),
  ("www.www.example.com", "www.example.com"),
  ("xn--bcher-kva.example", "www.xn--bcher-kva.example"),
]

@Test func givenTheSharedWwwVectorWhenACounterpartIsDerivedThenSwiftMatchesTheTable() {
  for (host, counterpart) in wwwCounterpartVector {
    #expect(ExactHostPolicy.wwwCounterpart(host) == counterpart)
  }
  #expect(ExactHostPolicy.wwwCounterpart("www.com") == nil)
}

@Test func givenExactHostWhenComparedThenWwwAndApexMatch() {
  let selected: Set<String> = ["example.com"]

  #expect(ExactHostPolicy.matches(host: "example.com", selectedHosts: selected))
  #expect(ExactHostPolicy.matches(host: "EXAMPLE.COM", selectedHosts: selected))
  #expect(ExactHostPolicy.matches(host: "example.com.", selectedHosts: selected))
  #expect(ExactHostPolicy.matches(host: "www.example.com", selectedHosts: selected))
  #expect(ExactHostPolicy.matches(host: "WWW.EXAMPLE.COM.", selectedHosts: selected))
  #expect(!ExactHostPolicy.matches(host: "example.org", selectedHosts: selected))
  #expect(!ExactHostPolicy.matches(host: "example.com.invalid", selectedHosts: selected))
  #expect(!ExactHostPolicy.matches(host: "example.com..", selectedHosts: selected))
  #expect(!ExactHostPolicy.matches(host: "mail.example.com", selectedHosts: selected))
  #expect(!ExactHostPolicy.matches(host: "www.www.example.com", selectedHosts: selected))
}

@Test func givenWwwHostWhenComparedThenApexMatches() {
  let selected: Set<String> = ["www.example.com"]

  #expect(ExactHostPolicy.matches(host: "www.example.com", selectedHosts: selected))
  #expect(ExactHostPolicy.matches(host: "example.com", selectedHosts: selected))
  #expect(!ExactHostPolicy.matches(host: "news.example.com", selectedHosts: selected))
}

@Test func givenMultiLabelWwwHostWhenComparedThenRemainderMatches() {
  let selected: Set<String> = ["news.example.com"]

  #expect(ExactHostPolicy.matches(host: "www.news.example.com", selectedHosts: selected))
  #expect(!ExactHostPolicy.matches(host: "example.com", selectedHosts: selected))
}

@Test func givenDoubledWwwHostWhenComparedThenOneStepCounterpartMatches() {
  let selected: Set<String> = ["www.www.example.com"]

  #expect(ExactHostPolicy.matches(host: "www.www.example.com", selectedHosts: selected))
  #expect(ExactHostPolicy.matches(host: "www.example.com", selectedHosts: selected))
  #expect(!ExactHostPolicy.matches(host: "example.com", selectedHosts: selected))
}

@Test func givenWwwComWhenComparedThenRemainderDoesNotMatch() {
  let selected: Set<String> = ["www.com"]

  #expect(ExactHostPolicy.matches(host: "www.com", selectedHosts: selected))
  #expect(!ExactHostPolicy.matches(host: "com", selectedHosts: selected))
}

@Test func givenIPLiteralWhenComparedThenItNeverMatchesAnExactDomain() {
  let selected: Set<String> = ["example.com", "127.0.0.1"]

  #expect(!ExactHostPolicy.matches(host: "127.0.0.1", selectedHosts: selected))
  #expect(!ExactHostPolicy.matches(host: "::1", selectedHosts: selected))
  #expect(!ExactHostPolicy.matches(host: "192.0.2.1", selectedHosts: ["192.0.2.1"]))
}
