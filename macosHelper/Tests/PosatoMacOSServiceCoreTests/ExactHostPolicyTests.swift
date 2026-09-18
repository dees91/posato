import Testing

@testable import PosatoMacOSHelper

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
