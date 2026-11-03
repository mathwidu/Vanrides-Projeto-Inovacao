#!/usr/bin/env ruby
# Produces an isolated UI-test project; never builds, signs, or contacts a device.
require 'digest'
require 'fileutils'
require 'json'
require 'optparse'
require 'pathname'
require 'xcodeproj'

options = {}
OptionParser.new do |parser|
  parser.on('--output PATH') { |value| options[:output] = value }
end.parse!
abort 'Use --output with a new absolute private directory.' unless options[:output]
output = Pathname.new(options[:output]).expand_path
abort 'Output already exists.' if output.exist?
output.ascend { |ancestor| abort 'Output must be outside Git checkouts.' if (ancestor + '.git').exist? }
team = ENV.fetch('VANRIDES_UI_TEAM', '')
abort 'Set VANRIDES_UI_TEAM to the observed signing team.' unless team.match?(/\A[A-Z0-9]{10}\z/)

source = Pathname.new(__dir__) + 'DriverGPSProofUITests.swift'
digest = Digest::SHA256.file(source).hexdigest
FileUtils.mkdir_p(output, mode: 0o700)
File.chmod(0o700, output)
FileUtils.cp(source, output + source.basename)
File.write(output + 'LabBuildIdentity.swift', "let labSourceDigest = #{digest.to_json}\n", perm: 0o600)

project = Xcodeproj::Project.new((output + 'DriverGPSProof.xcodeproj').to_s)
target = project.new_target(:ui_test_bundle, 'DriverGPSProofUITests', :ios, '16.0')
target.build_configurations.each do |configuration|
  configuration.build_settings.merge!(
    'PRODUCT_BUNDLE_IDENTIFIER' => 'dev.matheusduarte.vanrides.gpsproof.uitests',
    'CODE_SIGN_STYLE' => 'Automatic', 'DEVELOPMENT_TEAM' => team,
    'GENERATE_INFOPLIST_FILE' => 'YES', 'SWIFT_VERSION' => '5.0',
    'IPHONEOS_DEPLOYMENT_TARGET' => '16.0', 'TARGETED_DEVICE_FAMILY' => '1',
    'SUPPORTED_PLATFORMS' => 'iphoneos', 'SDKROOT' => 'iphoneos',
    'ENABLE_CODE_COVERAGE' => 'NO', 'GCC_GENERATE_TEST_COVERAGE_FILES' => 'NO',
    'SWIFT_EMIT_LOC_STRINGS' => 'NO'
  )
  %w[TEST_TARGET_NAME TEST_HOST BUNDLE_LOADER].each { |key| configuration.build_settings.delete(key) }
end
%w[DriverGPSProofUITests.swift LabBuildIdentity.swift].each do |filename|
  target.source_build_phase.add_file_reference(project.main_group.new_file(filename))
end
project.save
scheme = Xcodeproj::XCScheme.new
scheme.add_build_target(target, false)
scheme.add_test_target(target)
scheme.test_action.build_configuration = 'Release'
scheme.test_action.code_coverage_enabled = false
scheme.test_action.xml_element.attributes['shouldUseLaunchSchemeArgsEnv'] = 'NO'
scheme.test_action.xml_element.attributes['systemAttachmentLifetime'] = 'keepNever'
scheme.test_action.xml_element.attributes['userAttachmentLifetime'] = 'keepNever'
scheme.save_as(project.path, 'DriverGPSProof', true)
File.write(output + 'lab-manifest.json', JSON.pretty_generate({
  schemaVersion: 1, sourceDigest: digest, minimumIOS: '16.0',
  target: target.name, driverBundleIdentifier: 'dev.matheusduarte.vanrides.driver'
}) + "\n", perm: 0o600)
puts 'Prepared standalone UI-test project. No build or device action executed.'
