import Foundation
import Testing

@testable import PosatoMacOSSync

@Test func givenUppercaseUUIDWhenCheckedThenItIsNotCanonical() {
  #expect(!RecordCodec.isCanonicalUUID("AAAAAAAA-BBBB-CCCC-DDDD-EEEEEEEEEEEE"))
  #expect(!RecordCodec.isCanonicalUUID("not-a-uuid"))
  #expect(!RecordCodec.isCanonicalUUID(""))
}

@Test func givenValidAnchorRecordWhenValidatedThenFieldsAreCombined() {
  let record = testAnchorRecord()

  #expect(RecordCodec.validateAnchor(record) == testAnchorFields())
}

@Test func givenAnchorWithWrongTypeWhenValidatedThenItIsRejected() {
  var record = testAnchorRecord()
  record.type = CloudNames.bundleType

  #expect(RecordCodec.validateAnchor(record) == nil)
}

@Test func givenAnchorWithWrongNameWhenValidatedThenItIsRejected() {
  var record = testAnchorRecord()
  record.name = "other"

  #expect(RecordCodec.validateAnchor(record) == nil)
}

@Test func givenAnchorWithExtraFieldWhenValidatedThenItIsRejected() {
  var record = testAnchorRecord()
  record.fields["extra"] = Data([1])
  record.allKeys = CloudNames.anchorFields + ["extra"]

  #expect(RecordCodec.validateAnchor(record) == nil)
}

@Test func givenAnchorWithShortFieldWhenValidatedThenItIsRejected() {
  var record = testAnchorRecord()
  record.fields[CloudNames.anchorFields[0]] = Data([1, 2, 3])

  #expect(RecordCodec.validateAnchor(record) == nil)
}

@Test func givenValidBundleRecordWhenValidatedThenIdentifierAndPayloadAreReturned() {
  let record = testBundleRecord()

  let validated = RecordCodec.validateBundle(record)!

  #expect(validated.0 == testBundleIdentifier())
  #expect(validated.1 == Data(repeating: 11, count: 8))
}

@Test func givenBundleWithNonCanonicalNameWhenValidatedThenItIsRejected() {
  var record = testBundleRecord()
  record.name = "NOT-CANONICAL"

  #expect(RecordCodec.validateBundle(record) == nil)
}

@Test func givenBundleWithOversizedPayloadWhenValidatedThenItIsRejected() {
  var record = testBundleRecord()
  record.fields[CloudNames.bundlePayloadField] = Data(
    repeating: 1,
    count: SyncLimits.bundleBytes + 1
  )

  #expect(RecordCodec.validateBundle(record) == nil)
}

@Test func givenBundleWithExtraFieldWhenValidatedThenItIsRejected() {
  var record = testBundleRecord()
  record.fields["extra"] = Data([1])
  record.allKeys = [CloudNames.bundlePayloadField, "extra"]

  #expect(RecordCodec.validateBundle(record) == nil)
}

@Test func givenPageWhenEncodedAndDecodedThenFieldsArePreserved() {
  let page = ChangePageFields(
    moreComing: true,
    cursor: Data([1, 2, 3]),
    bundleIdentifier: testBundleIdentifier(),
    bundle: Data([4, 5, 6])
  )

  let decoded = PageCodec.decode(PageCodec.encode(page)!)!

  #expect(decoded == page)
}

@Test func givenPageWithoutBundleWhenEncodedAndDecodedThenAbsenceIsPreserved() {
  let page = ChangePageFields(
    moreComing: true,
    cursor: Data([7]),
    bundleIdentifier: nil,
    bundle: nil
  )

  let decoded = PageCodec.decode(PageCodec.encode(page)!)!

  #expect(decoded == page)
}

@Test func givenPageWithHalfBundleWhenEncodedThenItIsRejected() {
  let page = ChangePageFields(
    moreComing: false,
    cursor: Data([7]),
    bundleIdentifier: testBundleIdentifier(),
    bundle: nil
  )

  #expect(PageCodec.encode(page) == nil)
}

@Test func givenTruncatedPageWhenDecodedThenItIsRejected() {
  let page = ChangePageFields(
    moreComing: false,
    cursor: Data([7]),
    bundleIdentifier: nil,
    bundle: nil
  )
  var encoded = PageCodec.encode(page)!
  encoded.removeLast()

  #expect(PageCodec.decode(encoded) == nil)
}

@Test func givenPageWithTrailingBytesWhenDecodedThenItIsRejected() {
  var encoded = PageCodec.encode(
    ChangePageFields(moreComing: false, cursor: Data(), bundleIdentifier: nil, bundle: nil)
  )!
  encoded.append(9)

  #expect(PageCodec.decode(encoded) == nil)
}

@Test func givenOversizedCursorPageWhenEncodedThenItIsRejected() {
  let page = ChangePageFields(
    moreComing: false,
    cursor: Data(repeating: 1, count: SyncLimits.cursorBytes + 1),
    bundleIdentifier: nil,
    bundle: nil
  )

  #expect(PageCodec.encode(page) == nil)
}

@Test func givenFullSizeBundlePageWhenEncodedThenItFitsTheResponseBound() {
  let page = ChangePageFields(
    moreComing: false,
    cursor: Data(repeating: 2, count: SyncLimits.cursorBytes),
    bundleIdentifier: testBundleIdentifier(),
    bundle: Data(repeating: 3, count: SyncLimits.bundleBytes)
  )

  let encoded = PageCodec.encode(page)!

  #expect(encoded.count == 81_946)
}

@Test func givenBindingOnlyPayloadWhenParsedThenBindingIsReturned() {
  #expect(CloudRequestCodec.bindingOnly(syntheticBinding) == syntheticBinding)
  #expect(CloudRequestCodec.bindingOnly(Data([1])) == nil)
}

@Test func givenAnchorRequestWhenParsedThenBindingAndFieldsAreSplit() {
  let fields = testAnchorFields()
  let parsed = CloudRequestCodec.anchorRequest(
    anchorPayload(binding: syntheticBinding, fields: fields))!

  #expect(parsed.binding == syntheticBinding)
  #expect(parsed.fields == fields)
  #expect(CloudRequestCodec.anchorRequest(syntheticBinding) == nil)
}

@Test func givenBundleRequestWhenParsedThenPartsAreSplit() {
  let identifier = testBundleIdentifier()
  let bundle = Data([1, 2, 3])
  let parsed = CloudRequestCodec.bundleRequest(
    bundlePayload(binding: syntheticBinding, identifier: identifier, bundle: bundle)
  )!

  #expect(parsed.binding == syntheticBinding)
  #expect(parsed.identifier == identifier)
  #expect(parsed.bundle == bundle)
  #expect(CloudRequestCodec.bundleRequest(syntheticBinding) == nil)
  #expect(
    CloudRequestCodec.bundleRequest(
      bundlePayload(binding: syntheticBinding, identifier: identifier, bundle: Data())
    ) == nil
  )
}

@Test func givenCursorRequestWhenParsedThenEmptyCursorStartsTheFirstPage() {
  let parsed = CloudRequestCodec.cursorRequest(syntheticBinding)!

  #expect(parsed.binding == syntheticBinding)
  #expect(parsed.cursor.isEmpty)
  // One extra byte admits the delete-path phase prefix; fetch cursors are
  // still strictly validated downstream, so only the phase-sized frame
  // parses here.
  let phased = CloudRequestCodec.cursorRequest(
    cursorPayload(
      binding: syntheticBinding,
      cursor: Data(repeating: 1, count: SyncLimits.cursorBytes + 1)
    )
  )

  #expect(phased != nil)
  let oversized = CloudRequestCodec.cursorRequest(
    cursorPayload(
      binding: syntheticBinding,
      cursor: Data(repeating: 1, count: SyncLimits.cursorBytes + 2)
    )
  )

  #expect(oversized == nil)
}
