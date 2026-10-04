# Disable this game's optional Steam integration without changing its archive.
# Returning zero from SteamAPI_Init keeps SteamUserStatsLite uninitialized;
# its achievement/stat methods then use their existing unavailable branches.
class Win32API
  alias_method :wrapper_original_initialize, :initialize
  alias_method :wrapper_original_call, :call

  def initialize(library, *args)
    library_name = library.to_s.tr('\\', '/').split('/').last
    @wrapper_steam_disabled = !!(library_name =~ /\Asteam_api(?:64)?(?:\.dll)?\z/i)
    return if @wrapper_steam_disabled

    wrapper_original_initialize(library, *args)
  end

  def call(*args)
    return 0 if @wrapper_steam_disabled

    wrapper_original_call(*args)
  end
end
