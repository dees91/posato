import Foundation
import Testing

@testable import PosatoMacOSHelper

private func processArguments(
  executable: String = "/Applications/Posato.app/Contents/MacOS/Posato",
  arguments: [String] = ["/Applications/Posato.app/Contents/MacOS/Posato"],
  environment: [String]
) -> Data {
  var data = Data()
  var count = Int32(arguments.count)
  withUnsafeBytes(of: &count) { data.append(contentsOf: $0) }
  data.append(contentsOf: Array(executable.utf8) + [0, 0, 0])
  for argument in arguments {
    data.append(contentsOf: Array(argument.utf8) + [0])
  }
  for entry in environment {
    data.append(contentsOf: Array(entry.utf8) + [0])
  }
  data.append(0)
  return data
}

@Test(arguments: ["JAVA_TOOL_OPTIONS", "_JAVA_OPTIONS", "JDK_JAVA_OPTIONS"])
func givenJavaOptionVariableWhenLaunchEnvironmentReadThenItIsUnclean(name: String) {
  let data = processArguments(environment: ["HOME=/Users/person", "\(name)=-javaagent:x.jar"])

  #expect(!ParentLaunchEnvironment.isClean(processArguments: data))
}

@Test func givenOnlyOrdinaryVariablesWhenLaunchEnvironmentReadThenItIsClean() {
  let data = processArguments(
    environment: ["HOME=/Users/person", "JAVA_TOOL_OPTIONS_NOTE=x", "PATH=/usr/bin"]
  )

  #expect(ParentLaunchEnvironment.isClean(processArguments: data))
}

@Test func givenEmptyFirstArgumentWhenLaunchEnvironmentReadThenFirstVariableIsStillFound() {
  let data = processArguments(
    arguments: ["", "--flag"],
    environment: ["JAVA_TOOL_OPTIONS=-javaagent:x.jar", "HOME=/Users/person"]
  )

  #expect(!ParentLaunchEnvironment.isClean(processArguments: data))
}

@Test func givenTruncatedProcessArgumentsWhenLaunchEnvironmentReadThenItIsUnclean() {
  let complete = processArguments(environment: ["HOME=/Users/person"])

  #expect(!ParentLaunchEnvironment.isClean(processArguments: Data()))
  #expect(!ParentLaunchEnvironment.isClean(processArguments: complete.prefix(3)))
  #expect(!ParentLaunchEnvironment.isClean(processArguments: complete.prefix(10)))
}
