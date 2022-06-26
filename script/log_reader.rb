# frozen_string_literal: true

require 'byebug'

main_dir = ARGV[0]

def parse_mh_log(files_path)
  file = File.open(files_path)

  consensus_data = file.readlines.map { |line| line if line.include?('consensus') }.compact

  file.close

  node_by_consensus_time = {}

  consensus_data.each do |message|
    splitted_message = message.split(' ')
    node_by_consensus_time[splitted_message[1]] = splitted_message.last
  end

  latest_consensus = node_by_consensus_time.max_by { |node, consensus_ts| consensus_ts.to_i }
  p latest_consensus[0].tr(']', '') + ' ' + latest_consensus[1]
end

def parse_mss_log(files_path)
  file = File.open(files_path)

  consensus_data = file.readlines.map { |line| line if line.include?('consensus') }.compact

  file.close

  node_by_consensus_time = {}

  consensus_data.each do |message|
    splitted_message = message.split(' ')
    node_by_consensus_time[splitted_message[1]] = { ts: splitted_message[-3], round: splitted_message.last }
  end

  latest_consensus = node_by_consensus_time.max_by { |node, consensus_ts| consensus_ts[:ts].to_i }
  p latest_consensus[0].tr(']', '') + ' ' + latest_consensus[1][:ts] + ' ' + latest_consensus[1][:round]
end

mss_file_path = '/Users/matheus.cunha/.sinalgo/logs/mss_merged.txt'
mh_file_path = '/Users/matheus.cunha/.sinalgo/logs/mh_merged.txt'

Dir.glob("#{main_dir}*").each do |dir|
  next if File.file?(dir)

  Dir.chdir(dir) do
    Dir.foreach(dir) do |file|
      if file.include?('mh')
        open(mh_file_path, 'a') do |f|
          f.puts parse_mh_log(file)
        end
      end

      if file.include?('mss')
        open(mss_file_path, 'a') do |f|
          f.puts parse_mss_log(file)
        end
      end
    end
  end
end



