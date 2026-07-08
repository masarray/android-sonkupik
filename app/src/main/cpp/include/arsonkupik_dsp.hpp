#pragma once

#include <array>
#include <cstddef>
#include <cstdint>
#include <string>
#include <vector>

namespace arsonkupik {

constexpr double kDefaultSampleRate = 48000.0;
constexpr int kMaxChannels = 8;

double db_to_gain(double db);
double gain_to_db(double gain);
double clamp(double value, double lo, double hi);
double clamp01(double value);

enum class EqType {
    LowCut,
    LowShelf,
    Bell,
    Notch,
    HighShelf,
    HighCut,
    AllPass
};

struct EqBand {
    std::string id;
    EqType type = EqType::Bell;
    double frequency = 1000.0;
    double gain_db = 0.0;
    double q = 0.70710678;
    int slope_db_per_oct = 12;
    bool enabled = true;
};

struct CompressorConfig {
    bool enabled = true;
    double threshold_db = -24.4;
    double ratio = 1.7;
    double knee_db = 24.0;
    double attack_sec = 0.032;
    double release_sec = 0.21;
    double makeup_gain_db = 0.88;
    double parallel_mix = 92.0; // percent
};

struct ColorConfig {
    bool enabled = true;
    double drive = 3.12;
    double body_freq = 166.0;
    double body = 12.8;
    double smart_bass = 55.0;
    double warmth_freq = 500.0;
    double warmth = 12.8;
    double harmonics_freq = 2180.0;
    double harmonics = 34.0;
    double air_freq = 12650.0;
    double air = 48.8;
    double ai_high_repair = 46.0;
    double velvet_treble = 66.0;
    double god_particles = 92.0;
    double vocal_tickle = 67.0;
    double vocal_presence = 55.0;
    double mid_projection = 65.0;
    double mix = 30.5;
    double stereo_mid = 70.0;
    std::string mode = "mastering";
};

struct WidthConfig {
    bool enabled = true;
    double mix = 73.0;
    double width = 153.0;
    double low_width = 100.0;
    double low_mid_width = 101.0;
    double mid_width = 128.0;
    double high_width = 200.0;
    double source_protect = 56.0;
    bool mono_bass = true;
    double mono_bass_freq = 150.0;
    double side_tone = 4.28;
};

struct OutputConfig {
    double input_gain_db = 0.0;
    double output_gain_db = -0.55;
    bool limiter_enabled = true;
    double limiter_ceiling_db = -1.05;
    double limiter_drive = 0.76;
    bool punch_protect = true;
    bool bypass = false;
};

struct MacroConfig {
    double masari_feel = 100.0;
    double enhance = 65.0;
    double smart_bass = 55.0;
    double smart_treble = 70.0;
    double vocal_body = 65.0;
    double stereo_magic = 85.0;
    bool smart_protect = true;
};

struct Preset {
    std::string id;
    std::string name;
    std::string description;
    std::vector<EqBand> eq;
    CompressorConfig compressor;
    ColorConfig color;
    WidthConfig width;
    OutputConfig output;
    MacroConfig macro;
};

const std::vector<Preset>& factory_presets();
const Preset* find_preset(const std::string& id);
const Preset& default_preset();

struct RuntimeParams {
    std::string preset_id = "default";
    double enhance = 65.0;
    double smart_bass = 55.0;
    double smart_treble = 70.0;
    double vocal_body = 65.0;
    double stereo_magic = 85.0;
    double output_trim_db = -0.55;
    bool smart_protect = true;
    bool bypass = false;
    bool advanced_override = false;
};

class Biquad {
public:
    Biquad() = default;
    void reset();
    void set_identity();
    void set(EqType type, double sample_rate, double frequency, double q, double gain_db = 0.0);
    float process(float x);
private:
    double b0_ = 1.0, b1_ = 0.0, b2_ = 0.0, a1_ = 0.0, a2_ = 0.0;
    double z1_ = 0.0, z2_ = 0.0;
};

class OnePoleSmoother {
public:
    void prepare(double sample_rate, double seconds, double initial = 0.0);
    double process(double target);
    void reset(double value = 0.0);
private:
    double alpha_ = 0.001;
    double state_ = 0.0;
};

struct MeterState {
    double input_peak_db = -120.0;
    double output_peak_db = -120.0;
    double gain_reduction_db = 0.0;
    double correlation = 1.0;
    bool clipping = false;
};

class DynamicsProcessor {
public:
    void prepare(double sample_rate, const CompressorConfig& cfg);
    void set_config(const CompressorConfig& cfg);
    void reset();
    std::pair<float,float> process(float l, float r);
    double gain_reduction_db() const { return last_gr_db_; }
private:
    double sample_rate_ = kDefaultSampleRate;
    CompressorConfig cfg_{};
    double env_ = 0.0;
    double last_gr_db_ = 0.0;
};

class LimiterProcessor {
public:
    void prepare(double sample_rate, const OutputConfig& cfg);
    void set_config(const OutputConfig& cfg);
    void reset();
    std::pair<float,float> process(float l, float r);
    double gain_reduction_db() const { return last_gr_db_; }
private:
    double sample_rate_ = kDefaultSampleRate;
    OutputConfig cfg_{};
    double env_ = 0.0;
    double last_gr_db_ = 0.0;
};

class ArSonKuPikEngine {
public:
    ArSonKuPikEngine();
    void prepare(double sample_rate, std::size_t channels);
    void set_runtime_params(const RuntimeParams& params);
    void reset();
    void process(float** planes, std::size_t channels, std::size_t frames);
    const MeterState& meters() const { return meters_; }
    const Preset& current_preset() const { return preset_; }

private:
    void rebuild_from_preset();
    void rebuild_eq();
    void rebuild_color_filters();
    void rebuild_width_filters();
    void apply_macros();
    std::pair<float,float> process_color(float l, float r);
    std::pair<float,float> process_width(float l, float r);
    float soft_saturate(float x, double drive) const;
    float process_channel_eq(std::size_t ch, float x);
    void update_meters(double in_l, double in_r, double out_l, double out_r, double gr_db);

    double sample_rate_ = kDefaultSampleRate;
    std::size_t channels_ = 2;
    RuntimeParams params_{};
    Preset preset_;
    Preset working_;
    MeterState meters_{};

    std::vector<std::array<Biquad, kMaxChannels>> eq_filters_;

    // Color / psychoacoustic filters. Stereo paths use indexes 0=L, 1=R.
    std::array<Biquad,2> bass_pre_;
    std::array<Biquad,2> bass_post_hp_;
    std::array<Biquad,2> bass_post_lp_;
    std::array<Biquad,2> warmth_pre_;
    std::array<Biquad,2> presence_pre_;
    std::array<Biquad,2> air_pre_;
    std::array<Biquad,2> deharsh_;
    Biquad side_air_hp_;
    Biquad side_air_tone_;
    Biquad mid_anchor_bp1_;
    Biquad mid_anchor_bp2_;
    Biquad vocal_tickle_bp_;
    Biquad treble_skin_bp_;
    Biquad low_body_bp_;

    // Generated phase-safe width from mid copy.
    Biquad width_pre_hp_;
    Biquad width_lowmid_hp_;
    Biquad width_lowmid_lp_;
    Biquad width_mid_hp_;
    Biquad width_mid_lp_;
    Biquad width_high_hp_;
    Biquad width_air_tone_;

    // Smart Multiband M/S Imager v2: real-side analysis/enhancement.
    // These filters process the existing Side signal, so musical stereo
    // instruments are preserved while risky low anti-phase energy is reduced.
    Biquad width_side_sub_hp_;
    Biquad width_side_sub_lp_;
    Biquad width_side_body_hp_;
    Biquad width_side_body_lp_;
    Biquad width_side_mid_hp_;
    Biquad width_side_mid_lp_;
    Biquad width_side_high_hp_;
    Biquad width_side_high_lp_;
    Biquad width_side_air_hp_;
    Biquad width_side_air_tone_;

    DynamicsProcessor compressor_;
    LimiterProcessor limiter_;

    OnePoleSmoother bypass_smooth_;
    OnePoleSmoother meter_smooth_;

    // Slow detectors used by Smart Stereo Integrity Engine.
    double width_mid_env_ = 1.0e-6;
    double width_side_env_ = 1.0e-6;
    double width_side_fast_env_ = 1.0e-6;
    double width_side_slow_env_ = 1.0e-6;
    double width_corr_env_ = 1.0;

    // Extension-style smart gain staging: reserve headroom only when source is
    // hot, then restore perceived loudness with clean makeup before the limiter.
    double smart_input_peak_env_ = 1.0e-6;
    double smart_prelim_peak_env_ = 1.0e-6;
    double smart_headroom_db_ = 0.0;
    double smart_makeup_db_ = 0.0;
};

} // namespace arsonkupik
