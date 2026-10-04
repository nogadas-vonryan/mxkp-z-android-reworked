# Observe exceptions without replacing game methods or swallowing game errors.
# Stop at 1 MiB so handled exceptions cannot fill storage during long sessions.
begin
  mkxp_wrapper_log = __LOG_PATH__
  mkxp_wrapper_bytes = 0
  mkxp_wrapper_trace = nil
  mkxp_wrapper_trace = TracePoint.new(:raise) do |event|
    begin
      exception = event.raised_exception
      entry = "\n[#{Time.now}] #{exception.class}: #{exception.message}\n" +
              Array(exception.backtrace).join("\n") + "\n"
      remaining = 1_048_576 - mkxp_wrapper_bytes
      if remaining > 0
        entry = entry.byteslice(0, remaining)
        File.open(mkxp_wrapper_log, 'ab') { |file| file.write(entry) }
        mkxp_wrapper_bytes += entry.bytesize
      end
      mkxp_wrapper_trace.disable if mkxp_wrapper_bytes >= 1_048_576
    rescue Exception
      # A diagnostic write must never replace the game's exception.
      mkxp_wrapper_trace.disable
    end
  end
  mkxp_wrapper_trace.enable
rescue Exception
  # Games remain playable if this Ruby build does not support TracePoint.
end
