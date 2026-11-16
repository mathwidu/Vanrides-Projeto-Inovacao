#!/usr/bin/env ruby
# Prepare an isolated UI runner only. No build, signing or device operation.
require 'digest'
require 'fileutils'
require 'json'
require 'optparse'
require 'pathname'
require 'xcodeproj'

options = {}
OptionParser.new { |parser| parser.on('--output PATH') { |value| options[:output] = value } }.parse!
abort 'Use --output with a new private directory.' unless options[:output]
output = Pathname.new(options[:output]).expand_path
abort 'Output already exists.' if output.exist?
output.ascend { |ancestor| abort 'Output must be outside Git.' if (ancestor + '.git').exist? }
team = ENV.fetch('VANRIDES_UI_TEAM', '')
abort 'Set the observed local signing team.' unless team.match?(/\A[A-Z0-9]{10}\z/)
File.umask(0o077)
FileUtils.mkdir_p(output, mode: 0o700)
source = Pathname.new(__dir__) + 'DriverMVPUITests.swift'
FileUtils.cp(source, output + source.basename)
File.write(output + 'MVPBuildIdentity.swift', "let mvpUISourceDigest = #{Digest::SHA256.file(source).hexdigest.to_json}\n")

project = Xcodeproj::Project.new((output + 'DriverMVP.xcodeproj').to_s)
target = project.new_target(:ui_test_bundle, 'DriverMVPUITests', :ios, '16.0')
target.build_configurations.each do |configuration|
  configuration.build_settings.merge!(
    'PRODUCT_BUNDLE_IDENTIFIER' => 'dev.matheusduarte.vanrides.mvp.uitests',
    'CODE_SIGN_STYLE' => 'Automatic', 'DEVELOPMENT_TEAM' => team,
    'GENERATE_INFOPLIST_FILE' => 'YES', 'SWIFT_VERSION' => '5.0',
    'IPHONEOS_DEPLOYMENT_TARGET' => '16.0', 'TARGETED_DEVICE_FAMILY' => '1',
    'SUPPORTED_PLATFORMS' => 'iphoneos', 'SDKROOT' => 'iphoneos',
    'ENABLE_CODE_COVERAGE' => 'NO', 'SWIFT_EMIT_LOC_STRINGS' => 'NO'
  )
  %w[TEST_TARGET_NAME TEST_HOST BUNDLE_LOADER].each { |key| configuration.build_settings.delete(key) }
end
%w[DriverMVPUITests.swift MVPBuildIdentity.swift].each do |filename|
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
scheme.save_as(project.path, 'DriverMVP', true)
puts 'Prepared private MVP UI project. No device action executed.'
