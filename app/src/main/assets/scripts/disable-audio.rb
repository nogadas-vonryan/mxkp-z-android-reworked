# Run silently without opening the game's missing audio files.
module Audio
  class << self
    def bgm_play(*args); end
    def bgs_play(*args); end
    def me_play(*args); end
    def se_play(*args); end

    def bgm_stop; end
    def bgs_stop; end
    def me_stop; end
    def se_stop; end

    def bgm_fade(*args); end
    def bgs_fade(*args); end
    def me_fade(*args); end

    def bgm_pos; 0; end
    def bgs_pos; 0; end
  end
end
