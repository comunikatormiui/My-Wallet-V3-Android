source "https://rubygems.org"

gem "bundler"
gem "fastlane", ">= 2.183.2"
gem "dotenv"
plugins_path = File.join(File.dirname(__FILE__), 'fastlane', 'Pluginfile')
eval_gemfile(plugins_path) if File.exist?(plugins_path)
