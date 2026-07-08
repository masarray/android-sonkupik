#include "arsonkupik_dsp.hpp"

#include <algorithm>
#include <cmath>
#include <cstring>

namespace arsonkupik {
namespace {
constexpr double kPi = 3.1415926535897932384626433832795;
constexpr double kMinDb = -120.0;

std::vector<double> butterworth_q_values(int slope)
{
    switch (slope) {
    case 48: return {0.50979558, 0.60134489, 0.89997622, 2.56291545};
    case 36: return {0.51763809, 0.70710678, 1.93185165};
    case 24: return {0.54119610, 1.30656296};
    default: return {0.70710678};
    }
}

float sane(float x)
{
    if (!std::isfinite(x)) return 0.0f;
    return std::clamp(x, -8.0f, 8.0f);
}

double mode_factor(const std::string& mode)
{
    if (mode == "mastering") return 0.96;
    if (mode == "modern") return 0.92;
    if (mode == "warm") return 0.84;
    return 0.58;
}
} // namespace

double db_to_gain(double db) { return std::pow(10.0, db / 20.0); }
double gain_to_db(double gain) { return gain <= 1e-12 ? kMinDb : 20.0 * std::log10(gain); }
double clamp(double value, double lo, double hi) { return std::max(lo, std::min(hi, value)); }
double clamp01(double value) { return clamp(value, 0.0, 1.0); }

void Biquad::reset()
{
    z1_ = 0.0;
    z2_ = 0.0;
}

void Biquad::set_identity()
{
    b0_ = 1.0; b1_ = 0.0; b2_ = 0.0; a1_ = 0.0; a2_ = 0.0;
}

void Biquad::set(EqType type, double sample_rate, double frequency, double q, double gain_db)
{
    if (sample_rate <= 0.0 || frequency <= 0.0) {
        set_identity();
        return;
    }
    frequency = clamp(frequency, 8.0, sample_rate * 0.475);
    q = std::max(0.05, q);

    const double w0 = 2.0 * kPi * frequency / sample_rate;
    const double cw = std::cos(w0);
    const double sw = std::sin(w0);
    const double A = std::pow(10.0, gain_db / 40.0);
    double alpha = sw / (2.0 * q);

    double b0, b1, b2, a0, a1, a2;
    switch (type) {
    case EqType::LowCut:
        b0 = (1.0 + cw) / 2.0;
        b1 = -(1.0 + cw);
        b2 = (1.0 + cw) / 2.0;
        a0 = 1.0 + alpha;
        a1 = -2.0 * cw;
        a2 = 1.0 - alpha;
        break;
    case EqType::HighCut:
        b0 = (1.0 - cw) / 2.0;
        b1 = 1.0 - cw;
        b2 = (1.0 - cw) / 2.0;
        a0 = 1.0 + alpha;
        a1 = -2.0 * cw;
        a2 = 1.0 - alpha;
        break;
    case EqType::Bell:
        b0 = 1.0 + alpha * A;
        b1 = -2.0 * cw;
        b2 = 1.0 - alpha * A;
        a0 = 1.0 + alpha / A;
        a1 = -2.0 * cw;
        a2 = 1.0 - alpha / A;
        break;
    case EqType::Notch:
        b0 = 1.0;
        b1 = -2.0 * cw;
        b2 = 1.0;
        a0 = 1.0 + alpha;
        a1 = -2.0 * cw;
        a2 = 1.0 - alpha;
        break;
    case EqType::LowShelf: {
        const double S = std::max(0.1, q);
        alpha = sw / 2.0 * std::sqrt((A + 1.0 / A) * (1.0 / S - 1.0) + 2.0);
        const double twoSqrtAAlpha = 2.0 * std::sqrt(A) * alpha;
        b0 = A * ((A + 1.0) - (A - 1.0) * cw + twoSqrtAAlpha);
        b1 = 2.0 * A * ((A - 1.0) - (A + 1.0) * cw);
        b2 = A * ((A + 1.0) - (A - 1.0) * cw - twoSqrtAAlpha);
        a0 = (A + 1.0) + (A - 1.0) * cw + twoSqrtAAlpha;
        a1 = -2.0 * ((A - 1.0) + (A + 1.0) * cw);
        a2 = (A + 1.0) + (A - 1.0) * cw - twoSqrtAAlpha;
        break;
    }
    case EqType::HighShelf: {
        const double S = std::max(0.1, q);
        alpha = sw / 2.0 * std::sqrt((A + 1.0 / A) * (1.0 / S - 1.0) + 2.0);
        const double twoSqrtAAlpha = 2.0 * std::sqrt(A) * alpha;
        b0 = A * ((A + 1.0) + (A - 1.0) * cw + twoSqrtAAlpha);
        b1 = -2.0 * A * ((A - 1.0) + (A + 1.0) * cw);
        b2 = A * ((A + 1.0) + (A - 1.0) * cw - twoSqrtAAlpha);
        a0 = (A + 1.0) - (A - 1.0) * cw + twoSqrtAAlpha;
        a1 = 2.0 * ((A - 1.0) - (A + 1.0) * cw);
        a2 = (A + 1.0) - (A - 1.0) * cw - twoSqrtAAlpha;
        break;
    }
    case EqType::AllPass:
    default:
        b0 = 1.0 - alpha;
        b1 = -2.0 * cw;
        b2 = 1.0 + alpha;
        a0 = 1.0 + alpha;
        a1 = -2.0 * cw;
        a2 = 1.0 - alpha;
        break;
    }

    b0_ = b0 / a0;
    b1_ = b1 / a0;
    b2_ = b2 / a0;
    a1_ = a1 / a0;
    a2_ = a2 / a0;
}

float Biquad::process(float x)
{
    const double y = b0_ * x + z1_;
    z1_ = b1_ * x - a1_ * y + z2_;
    z2_ = b2_ * x - a2_ * y;
    if (!std::isfinite(z1_)) z1_ = 0.0;
    if (!std::isfinite(z2_)) z2_ = 0.0;
    return sane(static_cast<float>(y));
}

void OnePoleSmoother::prepare(double sample_rate, double seconds, double initial)
{
    seconds = std::max(0.001, seconds);
    alpha_ = 1.0 - std::exp(-1.0 / (sample_rate * seconds));
    state_ = initial;
}

double OnePoleSmoother::process(double target)
{
    state_ += alpha_ * (target - state_);
    return state_;
}

void OnePoleSmoother::reset(double value) { state_ = value; }

void DynamicsProcessor::prepare(double sample_rate, const CompressorConfig& cfg)
{
    sample_rate_ = sample_rate > 0.0 ? sample_rate : kDefaultSampleRate;
    cfg_ = cfg;
    reset();
}

void DynamicsProcessor::set_config(const CompressorConfig& cfg) { cfg_ = cfg; }
void DynamicsProcessor::reset() { env_ = 0.0; last_gr_db_ = 0.0; }

std::pair<float,float> DynamicsProcessor::process(float l, float r)
{
    if (!cfg_.enabled) return {l, r};
    const double peak = std::max(std::abs(l), std::abs(r));
    const double attack = std::exp(-1.0 / (sample_rate_ * std::max(0.001, cfg_.attack_sec)));
    const double release = std::exp(-1.0 / (sample_rate_ * std::max(0.001, cfg_.release_sec)));
    const double coeff = peak > env_ ? attack : release;
    env_ = coeff * env_ + (1.0 - coeff) * peak;
    const double in_db = gain_to_db(env_ + 1e-12);
    double over = in_db - cfg_.threshold_db;
    double gr_db = 0.0;
    if (cfg_.knee_db > 0.0) {
        const double half_knee = cfg_.knee_db * 0.5;
        if (over <= -half_knee) {
            gr_db = 0.0;
        } else if (over < half_knee) {
            const double x = over + half_knee;
            const double compressed = x * x / (2.0 * cfg_.knee_db);
            gr_db = compressed * (1.0 / cfg_.ratio - 1.0);
        } else {
            gr_db = over * (1.0 / cfg_.ratio - 1.0);
        }
    } else if (over > 0.0) {
        gr_db = over * (1.0 / cfg_.ratio - 1.0);
    }
    last_gr_db_ = std::abs(std::min(0.0, gr_db));
    const double wet_gain = db_to_gain(gr_db + cfg_.makeup_gain_db);
    const double mix = clamp01(cfg_.parallel_mix / 100.0);
    const double dry = std::cos(mix * kPi / 2.0);
    const double wet = std::sin(mix * kPi / 2.0);
    return {sane(static_cast<float>(l * dry + l * wet_gain * wet)),
            sane(static_cast<float>(r * dry + r * wet_gain * wet))};
}

void LimiterProcessor::prepare(double sample_rate, const OutputConfig& cfg)
{
    sample_rate_ = sample_rate > 0.0 ? sample_rate : kDefaultSampleRate;
    cfg_ = cfg;
    reset();
}

void LimiterProcessor::set_config(const OutputConfig& cfg) { cfg_ = cfg; }
void LimiterProcessor::reset() { env_ = 0.0; last_gr_db_ = 0.0; }

std::pair<float,float> LimiterProcessor::process(float l, float r)
{
    if (!cfg_.limiter_enabled) return {l, r};

    // v0.4.2 clean limiter policy:
    // limiter drive is a gentle perceived-loudness nudge, not a distortion stage.
    // The clipper is safety-only and stays out of the signal until peaks are risky.
    const double drive_db = clamp(cfg_.limiter_drive, 0.0, 1.4) * 1.05;
    const double drive_gain = db_to_gain(drive_db);
    l = sane(static_cast<float>(l * drive_gain));
    r = sane(static_cast<float>(r * drive_gain));

    auto safety_clip = [](float x) {
        const double ax = std::abs(static_cast<double>(x));
        constexpr double threshold = 0.975;
        if (ax <= threshold) return x;
        const double sign = x < 0.0f ? -1.0 : 1.0;
        const double over = (ax - threshold) / (1.0 - threshold);
        const double y = threshold + (1.0 - threshold) * std::tanh(over);
        return sane(static_cast<float>(sign * y));
    };
    if (cfg_.punch_protect) {
        l = safety_clip(l);
        r = safety_clip(r);
    }

    const double peak = std::max(std::abs(l), std::abs(r));
    const double attack = std::exp(-1.0 / (sample_rate_ * 0.0014));
    const double release = std::exp(-1.0 / (sample_rate_ * (cfg_.punch_protect ? 0.105 : 0.060)));
    const double coeff = peak > env_ ? attack : release;
    env_ = coeff * env_ + (1.0 - coeff) * peak;
    const double ceiling = db_to_gain(cfg_.limiter_ceiling_db);
    double gain = 1.0;
    if (env_ > ceiling) gain = ceiling / (env_ + 1e-12);
    last_gr_db_ = std::max(0.0, -gain_to_db(gain));
    return {sane(static_cast<float>(l * gain)), sane(static_cast<float>(r * gain))};
}

ArSonKuPikEngine::ArSonKuPikEngine()
{
    preset_ = default_preset();
    working_ = preset_;
}

void ArSonKuPikEngine::prepare(double sample_rate, std::size_t channels)
{
    sample_rate_ = sample_rate > 0.0 ? sample_rate : kDefaultSampleRate;
    channels_ = std::max<std::size_t>(1, std::min<std::size_t>(channels, kMaxChannels));
    bypass_smooth_.prepare(sample_rate_, 0.075, params_.bypass ? 1.0 : 0.0);
    meter_smooth_.prepare(sample_rate_, 0.180, 0.0);
    compressor_.prepare(sample_rate_, working_.compressor);
    limiter_.prepare(sample_rate_, working_.output);
    rebuild_from_preset();
}

void ArSonKuPikEngine::set_runtime_params(const RuntimeParams& params)
{
    params_ = params;
    const Preset* p = find_preset(params.preset_id);
    preset_ = p ? *p : default_preset();
    working_ = preset_;
    apply_macros();
    rebuild_from_preset();
}

void ArSonKuPikEngine::reset()
{
    for (auto& chs : eq_filters_) for (auto& b : chs) b.reset();
    for (auto& b : bass_pre_) b.reset();
    for (auto& b : bass_post_hp_) b.reset();
    for (auto& b : bass_post_lp_) b.reset();
    for (auto& b : warmth_pre_) b.reset();
    for (auto& b : presence_pre_) b.reset();
    for (auto& b : air_pre_) b.reset();
    for (auto& b : deharsh_) b.reset();
    side_air_hp_.reset(); side_air_tone_.reset(); mid_anchor_bp1_.reset(); mid_anchor_bp2_.reset();
    vocal_tickle_bp_.reset(); treble_skin_bp_.reset(); low_body_bp_.reset(); width_pre_hp_.reset();
    width_lowmid_hp_.reset(); width_lowmid_lp_.reset(); width_mid_hp_.reset(); width_mid_lp_.reset();
    width_high_hp_.reset(); width_air_tone_.reset();
    width_side_sub_hp_.reset(); width_side_sub_lp_.reset(); width_side_body_hp_.reset(); width_side_body_lp_.reset();
    width_side_mid_hp_.reset(); width_side_mid_lp_.reset(); width_side_high_hp_.reset(); width_side_high_lp_.reset();
    width_side_air_hp_.reset(); width_side_air_tone_.reset();
    width_mid_env_ = 1.0e-6; width_side_env_ = 1.0e-6; width_side_fast_env_ = 1.0e-6; width_side_slow_env_ = 1.0e-6; width_corr_env_ = 1.0;
    smart_input_peak_env_ = 1.0e-6; smart_prelim_peak_env_ = 1.0e-6; smart_headroom_db_ = 0.0; smart_makeup_db_ = 0.0;
    compressor_.reset(); limiter_.reset();
}

void ArSonKuPikEngine::apply_macros()
{
    // Macro controls are intentionally deltas around the frozen preset recipe.
    const double ui_enhance = params_.advanced_override ? params_.enhance : working_.macro.enhance;
    const double ui_bass = params_.advanced_override ? params_.smart_bass : working_.macro.smart_bass;
    const double ui_treble = params_.advanced_override ? params_.smart_treble : working_.macro.smart_treble;
    const double ui_vocal = params_.advanced_override ? params_.vocal_body : working_.macro.vocal_body;
    const double ui_stereo = params_.advanced_override ? params_.stereo_magic : working_.macro.stereo_magic;

    const double enhance = clamp01(ui_enhance / 100.0);
    const double bass = clamp01(ui_bass / 100.0);
    const double treble = clamp01(ui_treble / 100.0);
    const double vocal = clamp01(ui_vocal / 100.0);
    const double stereo = clamp01(ui_stereo / 100.0);
    const double masari = clamp01(working_.macro.masari_feel / 100.0);

    auto add = [](double base, double delta, double lo, double hi) {
        return clamp(base + delta, lo, hi);
    };

    working_.color.mix = add(working_.color.mix, (enhance - 0.65) * 18.0 + masari * 1.2, 0.0, 42.0);
    working_.color.smart_bass = add(working_.color.smart_bass, (bass - working_.macro.smart_bass / 100.0) * 60.0, 0.0, 100.0);
    working_.color.body = add(working_.color.body, (bass - 0.55) * 13.0 + (vocal - 0.65) * 5.5 + masari * 1.0, 0.0, 30.0);
    working_.color.warmth = add(working_.color.warmth, (bass - 0.55) * 7.5 + (vocal - 0.65) * 4.2 + masari * 0.6, 0.0, 25.0);
    working_.color.air = add(working_.color.air, (treble - working_.macro.smart_treble / 100.0) * 34.0 + masari * 2.2, -8.0, 70.0);
    working_.color.god_particles = add(working_.color.god_particles, (treble - 0.70) * 22.0 + masari * 4.5, 0.0, 100.0);
    working_.color.velvet_treble = add(working_.color.velvet_treble, treble > 0.62 ? (treble - 0.62) * 18.0 : 0.0, 0.0, 100.0);
    working_.color.vocal_tickle = add(working_.color.vocal_tickle, (treble - 0.70) * 14.0 + (vocal - 0.65) * 16.0 + masari * 1.2, 0.0, 100.0);
    working_.color.vocal_presence = add(working_.color.vocal_presence, (vocal - 0.65) * 34.0 + masari * 1.1, 0.0, 100.0);
    working_.color.mid_projection = add(working_.color.mid_projection, (vocal - 0.65) * 36.0 + masari * 1.1, 0.0, 100.0);
    working_.width.enabled = working_.width.enabled && stereo > 0.02;
    working_.width.mix = add(working_.width.mix, (stereo - working_.macro.stereo_magic / 100.0) * 52.0 + masari * 1.8, 0.0, 100.0);
    working_.width.width = add(working_.width.width, (stereo - 0.85) * 82.0 + masari * 2.8, 88.0, 220.0);
    working_.width.mid_width = add(working_.width.mid_width, (stereo - 0.85) * 43.0 + masari * 1.2, 90.0, 165.0);
    working_.width.high_width = add(working_.width.high_width, (stereo - 0.85) * 84.0 + masari * 2.2, 98.0, 224.0);
    // Always give the user an audible ON benefit. The extension's trick is not
    // distortion; it is smart compressor/makeup staging plus clean tonal lift.
    const double perceived_benefit_db = 1.10 + enhance * 1.05 + std::max(0.0, vocal - 0.50) * 0.35 + std::max(0.0, treble - 0.55) * 0.25;
    working_.output.output_gain_db = params_.output_trim_db + perceived_benefit_db;
    working_.compressor.makeup_gain_db = clamp(working_.compressor.makeup_gain_db + 0.75 + enhance * 0.80, 0.0, 4.8);
    working_.compressor.parallel_mix = clamp(working_.compressor.parallel_mix + enhance * 3.0, 72.0, 96.0);
    working_.output.bypass = params_.bypass;
    working_.output.punch_protect = params_.smart_protect;

    // Headroom compensation: Smart Bass and Smart Treble are musical macros, not simple boosts.
    const double headroom = std::max(0.0, bass - 0.86) * 0.25 + std::max(0.0, treble - 0.90) * 0.08 + std::max(0.0, enhance - 0.88) * 0.12;
    if (params_.smart_protect) {
        working_.output.output_gain_db -= headroom;
        working_.output.limiter_ceiling_db = std::min(working_.output.limiter_ceiling_db, -1.0 - std::max(0.0, bass - 0.78) * 0.7);
        working_.output.limiter_drive = clamp(working_.output.limiter_drive + std::max(0.0, bass - 0.80) * 0.035 + masari * 0.010, 0.0, 0.90);
    }
}

void ArSonKuPikEngine::rebuild_from_preset()
{
    rebuild_eq();
    rebuild_color_filters();
    rebuild_width_filters();
    compressor_.set_config(working_.compressor);
    limiter_.set_config(working_.output);
}

void ArSonKuPikEngine::rebuild_eq()
{
    eq_filters_.clear();
    for (const auto& band : working_.eq) {
        if (!band.enabled) continue;
        const auto qs = (band.type == EqType::LowCut || band.type == EqType::HighCut) ? butterworth_q_values(band.slope_db_per_oct) : std::vector<double>{band.q};
        for (double q : qs) {
            std::array<Biquad, kMaxChannels> chs;
            for (auto& b : chs) b.set(band.type, sample_rate_, band.frequency, q, band.gain_db);
            eq_filters_.push_back(chs);
        }
    }
}

void ArSonKuPikEngine::rebuild_color_filters()
{
    const auto& c = working_.color;
    const double body_center = clamp(c.body_freq, 95.0, 260.0);
    const double warm_center = clamp(c.warmth_freq, 300.0, 760.0);
    const double harmonic_center = clamp(c.harmonics_freq, 1200.0, 3600.0);
    const double air_center = clamp(c.air_freq, 6500.0, 16000.0);
    for (int i = 0; i < 2; ++i) {
        bass_pre_[i].set(EqType::Bell, sample_rate_, clamp(body_center * 0.72, 50.0, 180.0), 0.58, 0.0);
        bass_post_hp_[i].set(EqType::LowCut, sample_rate_, 34.0, 0.707, 0.0);
        bass_post_lp_[i].set(EqType::HighCut, sample_rate_, clamp(body_center * 2.35, 230.0, 420.0), 0.64, 0.0);
        warmth_pre_[i].set(EqType::Bell, sample_rate_, warm_center, 0.78, 0.0);
        presence_pre_[i].set(EqType::Bell, sample_rate_, harmonic_center, 0.68, 0.0);
        air_pre_[i].set(EqType::HighShelf, sample_rate_, air_center, 0.50, 0.0);
        const double velvet = clamp01(c.velvet_treble / 100.0);
        const double repair = clamp01(c.ai_high_repair / 100.0);
        deharsh_[i].set(EqType::Bell, sample_rate_, 6800.0 + repair * 1800.0, 1.10, -0.15 - velvet * 0.65);
    }
    side_air_hp_.set(EqType::LowCut, sample_rate_, 7800.0, 0.707, 0.0);
    side_air_tone_.set(EqType::HighShelf, sample_rate_, 10800.0, 0.58, clamp(c.air * 0.036 + c.god_particles * 0.014, -1.2, 4.8));
    mid_anchor_bp1_.set(EqType::LowCut, sample_rate_, 540.0, 0.707, 0.0);
    mid_anchor_bp2_.set(EqType::HighCut, sample_rate_, 4100.0, 0.707, 0.0);
    vocal_tickle_bp_.set(EqType::Bell, sample_rate_, 1450.0, 0.72, 0.0);
    treble_skin_bp_.set(EqType::Bell, sample_rate_, 8750.0, 0.86, 0.0);
    low_body_bp_.set(EqType::Bell, sample_rate_, clamp(body_center * 1.45, 220.0, 300.0), 0.42, 0.0);
}

void ArSonKuPikEngine::rebuild_width_filters()
{
    const auto& w = working_.width;
    const double tone = std::max(0.0, w.side_tone);
    const double generated_low_cut = w.mono_bass ? std::max(210.0, w.mono_bass_freq) : 150.0;

    // Generated side is now only a small air/space filler. Real stereo width is
    // handled from the original Side signal in process_width().
    width_pre_hp_.set(EqType::LowCut, sample_rate_, generated_low_cut, 0.707, 0.0);
    width_lowmid_hp_.set(EqType::LowCut, sample_rate_, 360.0, 0.707, 0.0);
    width_lowmid_lp_.set(EqType::HighCut, sample_rate_, 900.0, 0.707, 0.0);
    width_mid_hp_.set(EqType::LowCut, sample_rate_, 900.0, 0.707, 0.0);
    width_mid_lp_.set(EqType::HighCut, sample_rate_, 4200.0 + tone * 22.0, 0.707, 0.0);
    width_high_hp_.set(EqType::LowCut, sample_rate_, 7200.0 + tone * 62.0, 0.707, 0.0);
    width_air_tone_.set(EqType::HighShelf, sample_rate_, 11200.0 + tone * 180.0, 0.62, clamp(w.side_tone * 0.20, -1.2, 4.2));

    // Original-side multiband lanes. Low stereo is not blindly monoed: it is
    // reduced dynamically only when anti-phase/sustained low-side risk is high.
    width_side_sub_hp_.set(EqType::LowCut, sample_rate_, 22.0, 0.707, 0.0);
    width_side_sub_lp_.set(EqType::HighCut, sample_rate_, clamp(w.mono_bass_freq, 95.0, 170.0), 0.707, 0.0);
    width_side_body_hp_.set(EqType::LowCut, sample_rate_, clamp(w.mono_bass_freq * 0.82, 85.0, 150.0), 0.707, 0.0);
    width_side_body_lp_.set(EqType::HighCut, sample_rate_, 680.0, 0.707, 0.0);
    width_side_mid_hp_.set(EqType::LowCut, sample_rate_, 680.0, 0.707, 0.0);
    width_side_mid_lp_.set(EqType::HighCut, sample_rate_, 3600.0, 0.707, 0.0);
    width_side_high_hp_.set(EqType::LowCut, sample_rate_, 3600.0, 0.707, 0.0);
    width_side_high_lp_.set(EqType::HighCut, sample_rate_, 11200.0, 0.707, 0.0);
    width_side_air_hp_.set(EqType::LowCut, sample_rate_, 7800.0 + tone * 42.0, 0.707, 0.0);
    width_side_air_tone_.set(EqType::HighShelf, sample_rate_, 11200.0 + tone * 190.0, 0.62, clamp(w.side_tone * 0.22, -1.2, 4.6));
}

float ArSonKuPikEngine::soft_saturate(float x, double drive) const
{
    // Saturn-inspired micro texture, CPU-safe: no oversampling by default because
    // this is not a loudness/distortion stage. It blends only a tiny amount of a
    // rounded curve into the filtered polish layer, preserving bass punch/detail.
    const double d = clamp(drive, 0.1, 2.4);
    const double shaped = std::tanh(static_cast<double>(x) * d) / std::max(0.1, d);
    const double wet = clamp((d - 1.0) * 0.014, 0.0, 0.026);
    const double y = static_cast<double>(x) * (1.0 - wet) + shaped * wet;
    return sane(static_cast<float>(y));
}

float ArSonKuPikEngine::process_channel_eq(std::size_t ch, float x)
{
    for (auto& filter : eq_filters_) {
        x = filter[ch].process(x);
    }
    return x;
}

std::pair<float,float> ArSonKuPikEngine::process_color(float l, float r)
{
    const auto& c = working_.color;
    if (!c.enabled || c.mix <= 0.0) return {l, r};

    const double mix = clamp01(c.mix / 100.0);
    const double harmonic = clamp01(c.harmonics / 100.0);
    const double bass_amt = clamp01(c.smart_bass / 100.0);
    const double air_amt = clamp(c.air / 48.0, -0.5, 1.0);
    const double god = clamp01(c.god_particles / 100.0);
    const double stereo_mid = clamp01(c.stereo_mid / 100.0);
    const double vocal_presence = clamp01(c.vocal_presence / 100.0);
    const double mid_projection = clamp01(c.mid_projection / 100.0);
    const double vocal_tickle = clamp01(c.vocal_tickle / 100.0);
    const double velvet = clamp01(c.velvet_treble / 100.0);
    const double mode = mode_factor(c.mode);
    const double drive = clamp(c.drive * 0.22 + c.harmonics * 0.002, 0.0, 1.60) * mode;

    float dry_l = l;
    float dry_r = r;

    float bass_l = bass_pre_[0].process(l);
    float bass_r = bass_pre_[1].process(r);
    bass_l = soft_saturate(bass_l, 0.92 + drive * 0.10 + bass_amt * 0.35);
    bass_r = soft_saturate(bass_r, 0.92 + drive * 0.10 + bass_amt * 0.35);
    bass_l = bass_post_lp_[0].process(bass_post_hp_[0].process(bass_l));
    bass_r = bass_post_lp_[1].process(bass_post_hp_[1].process(bass_r));
    const double bass_wet = mix * (0.024 + bass_amt * 0.078 + std::max(0.0, c.body) * 0.0022);

    float warm_l = soft_saturate(warmth_pre_[0].process(l), 0.94 + drive * 0.08 + c.warmth * 0.006);
    float warm_r = soft_saturate(warmth_pre_[1].process(r), 0.94 + drive * 0.08 + c.warmth * 0.006);
    const double warm_wet = mix * clamp(0.020 + c.warmth * 0.0020, 0.0, 0.078);

    float pres_l = soft_saturate(presence_pre_[0].process(l), 0.92 + drive * 0.06 + harmonic * 0.10);
    float pres_r = soft_saturate(presence_pre_[1].process(r), 0.92 + drive * 0.06 + harmonic * 0.10);
    const double pres_wet = mix * (0.020 + harmonic * 0.003 + vocal_presence * 0.038);

    float air_l = soft_saturate(air_pre_[0].process(l), 0.76 + std::max(0.0, air_amt) * 0.40 + harmonic * 0.06);
    float air_r = soft_saturate(air_pre_[1].process(r), 0.76 + std::max(0.0, air_amt) * 0.40 + harmonic * 0.06);
    const double air_wet = mix * std::max(0.0, 0.042 + std::max(0.0, air_amt) * 0.135 + harmonic * 0.001 + velvet * 0.014);

    double mid = 0.5 * (l + r);
    double side = 0.5 * (l - r);

    float side_air = side_air_tone_.process(side_air_hp_.process(static_cast<float>(side)));
    side_air = soft_saturate(side_air, 0.74 + god * 0.28 + std::max(0.0, air_amt) * 0.26);
    const double side_air_wet = mix * clamp(god * (0.034 + std::max(0.0, air_amt) * 0.066 + harmonic * 0.0008), 0.0, 0.105);

    float mid_anchor = mid_anchor_bp2_.process(mid_anchor_bp1_.process(static_cast<float>(mid)));
    mid_anchor = soft_saturate(mid_anchor, 0.86 + stereo_mid * 0.38 + vocal_presence * 0.28);
    const double mid_wet = mix * clamp(stereo_mid * 0.032 + vocal_presence * 0.042 + mid_projection * 0.044, 0.0, 0.120);

    float low_body = low_body_bp_.process(static_cast<float>(mid));
    low_body = soft_saturate(low_body, 0.82 + bass_amt * 0.30 + std::max(0.0, c.body) * 0.006);
    const double low_body_wet = mix * clamp(bass_amt * 0.022 + std::max(0.0, c.body) * 0.0021 + std::max(0.0, c.warmth) * 0.0012, 0.0, 0.070);

    float tickle = vocal_tickle_bp_.process(static_cast<float>(mid));
    tickle = soft_saturate(tickle, 0.82 + vocal_tickle * 0.34 + harmonic * 0.04);
    const double tickle_wet = mix * clamp(vocal_tickle * 0.036, 0.0, 0.052);

    float skin = treble_skin_bp_.process(static_cast<float>(mid));
    skin = soft_saturate(skin, 0.72 + god * 0.20 + std::max(0.0, air_amt) * 0.18);
    const double skin_wet = mix * clamp((god * 0.020 + std::max(0.0, air_amt) * 0.020 + vocal_tickle * 0.014) * (1.0 - velvet * 0.04), 0.0, 0.055);

    l += static_cast<float>(bass_l * bass_wet + warm_l * warm_wet + pres_l * pres_wet + air_l * air_wet);
    r += static_cast<float>(bass_r * bass_wet + warm_r * warm_wet + pres_r * pres_wet + air_r * air_wet);

    // +Side/-Side layer for sparkle; +Mid/+Mid anchors for vocal/body so width does not hollow the center.
    l += static_cast<float>(side_air * side_air_wet + mid_anchor * mid_wet + low_body * low_body_wet + tickle * tickle_wet + skin * skin_wet);
    r += static_cast<float>(-side_air * side_air_wet + mid_anchor * mid_wet + low_body * low_body_wet + tickle * tickle_wet + skin * skin_wet);

    l = deharsh_[0].process(l);
    r = deharsh_[1].process(r);

    const double comp = 1.0 / (1.0 + mix * (drive / 12.2) * 0.012 + harmonic * mix * 0.002 + side_air_wet * 0.008 + mid_wet * 0.010 + low_body_wet * 0.006 + air_wet * 0.004);
    l = static_cast<float>(l * comp);
    r = static_cast<float>(r * comp);

    // Keep direct musical texture alive, matching the extension's color dry gain policy.
    const double dry_keep = clamp(1.0 + mix * 0.080, 1.010, 1.115);
    l = static_cast<float>(dry_l * dry_keep + (l - dry_l));
    r = static_cast<float>(dry_r * dry_keep + (r - dry_r));
    return {sane(l), sane(r)};
}

std::pair<float,float> ArSonKuPikEngine::process_width(float l, float r)
{
    const auto& w = working_.width;
    if (!w.enabled || w.mix <= 0.0) return {l, r};

    const double width_mix = clamp01(w.mix / 100.0);
    const double master_expand = clamp((w.width - 100.0) / 100.0, 0.0, 1.0);
    const double source_protect = clamp01(w.source_protect / 100.0);
    auto additive_gain = [&](double percent, double weight, double linked, double max_value) {
        const double band_expand = clamp((percent - 100.0) / 100.0, 0.0, 1.0);
        return clamp(band_expand * weight + master_expand * linked, 0.0, max_value) * width_mix;
    };

    const double mid = 0.5 * (static_cast<double>(l) + static_cast<double>(r));
    const double side = 0.5 * (static_cast<double>(l) - static_cast<double>(r));
    const double mid_abs = std::abs(mid);
    const double side_abs = std::abs(side);

    const double slow = 1.0 - std::exp(-1.0 / (sample_rate_ * 0.055));
    const double fast = 1.0 - std::exp(-1.0 / (sample_rate_ * 0.006));
    width_mid_env_ += slow * (mid_abs - width_mid_env_);
    width_side_env_ += slow * (side_abs - width_side_env_);
    width_side_fast_env_ += fast * (side_abs - width_side_fast_env_);
    width_side_slow_env_ += slow * (side_abs - width_side_slow_env_);

    const double denom = std::sqrt((static_cast<double>(l) * l + 1e-12) * (static_cast<double>(r) * r + 1e-12));
    const double corr_now = denom > 0.0 ? clamp((static_cast<double>(l) * r) / denom, -1.0, 1.0) : 1.0;
    const double corr_alpha = 1.0 - std::exp(-1.0 / (sample_rate_ * 0.090));
    width_corr_env_ += corr_alpha * (corr_now - width_corr_env_);

    const double side_ratio = width_side_env_ / (width_mid_env_ + 1e-6);
    const double center_dominance = width_mid_env_ / (width_side_env_ + 1e-6);
    const double transient_score = clamp((width_side_fast_env_ / (width_side_slow_env_ + 1e-6) - 1.0) * 0.48, 0.0, 1.0);
    const double vocal_prob = clamp((center_dominance - 1.35) / 2.25, 0.0, 1.0) * (1.0 - transient_score * 0.55);
    const double pan_event = clamp((side_ratio - 0.34) / 0.78, 0.0, 1.0) * clamp((width_corr_env_ + 0.08) / 0.58, 0.0, 1.0);
    const double anti_phase = clamp((0.18 - width_corr_env_) / 0.55, 0.0, 1.0);
    const double corr_guard = clamp((width_corr_env_ + 0.12) / 0.52, 0.0, 1.0);

    const double low_risk = anti_phase * source_protect * (1.0 - transient_score * 0.72) * clamp((side_ratio - 0.20) / 0.55, 0.0, 1.0);
    const double low_reduce = clamp(low_risk * 0.68, 0.0, 0.68);

    const float side_f = static_cast<float>(side);
    const float side_sub = width_side_sub_lp_.process(width_side_sub_hp_.process(side_f));
    const float side_body = width_side_body_lp_.process(width_side_body_hp_.process(side_f));
    const float side_mid = width_side_mid_lp_.process(width_side_mid_hp_.process(side_f));
    const float side_high = width_side_high_lp_.process(width_side_high_hp_.process(side_f));
    float side_air = width_side_air_tone_.process(width_side_air_hp_.process(side_f));
    side_air = soft_saturate(side_air, 0.68 + width_mix * 0.12 + std::max(0.0, w.side_tone) * 0.008);

    const double body_gain = additive_gain(w.low_mid_width, 0.052, 0.016, 0.068) * (0.60 + transient_score * 0.70 + pan_event * 0.35) * corr_guard;
    const double mid_gain = additive_gain(w.mid_width, 0.104, 0.040, 0.142) * (1.0 - vocal_prob * 0.52) * corr_guard;
    const double high_gain = additive_gain(w.high_width, 0.215, 0.088, 0.285) * (1.0 - vocal_prob * 0.16) * (0.55 + corr_guard * 0.45);
    const double air_gain = additive_gain(w.high_width, 0.145, 0.064, 0.210) * (1.0 - anti_phase * 0.72) * (0.78 + pan_event * 0.20);

    // Very small generated-side filler. It adds life to narrow/mono material but
    // backs off when center vocal is likely or when the original mix already has
    // strong left-right call/response.
    float generated = width_pre_hp_.process(static_cast<float>(mid));
    float lowmid_gen = width_lowmid_lp_.process(width_lowmid_hp_.process(generated));
    float mid_gen = width_mid_lp_.process(width_mid_hp_.process(generated));
    float air_gen = width_air_tone_.process(width_high_hp_.process(generated));
    const double gen_lowmid_gain = additive_gain(w.low_mid_width, 0.012, 0.004, 0.018) * (1.0 - vocal_prob * 0.88) * (1.0 - pan_event * 0.62) * corr_guard;
    const double gen_mid_gain = additive_gain(w.mid_width, 0.022, 0.008, 0.035) * (1.0 - vocal_prob * 0.86) * (1.0 - pan_event * 0.70) * corr_guard;
    const double gen_air_gain = additive_gain(w.high_width, 0.052, 0.020, 0.075) * (1.0 - vocal_prob * 0.42) * (1.0 - anti_phase * 0.66);

    double side_out = side;
    side_out -= static_cast<double>(side_sub) * low_reduce;
    side_out += static_cast<double>(side_body) * body_gain;
    side_out += static_cast<double>(side_mid) * mid_gain;
    side_out += static_cast<double>(side_high) * high_gain;
    side_out += static_cast<double>(side_air) * air_gain;
    side_out += static_cast<double>(lowmid_gen) * gen_lowmid_gain;
    side_out += static_cast<double>(mid_gen) * gen_mid_gain;
    side_out += static_cast<double>(air_gen) * gen_air_gain;

    // Center anchor: when vocal/lead is detected, do not widen by stealing from
    // the center. Keep it slightly more solid while instruments get side polish.
    const double mid_anchor_gain = 1.0 + width_mix * vocal_prob * 0.010 + width_mix * source_protect * 0.004;
    const double out_l = mid * mid_anchor_gain + side_out;
    const double out_r = mid * mid_anchor_gain - side_out;
    return {sane(static_cast<float>(out_l)), sane(static_cast<float>(out_r))};
}

void ArSonKuPikEngine::update_meters(double in_l, double in_r, double out_l, double out_r, double gr_db)
{
    const double in_peak = std::max(std::abs(in_l), std::abs(in_r));
    const double out_peak = std::max(std::abs(out_l), std::abs(out_r));
    meters_.input_peak_db = gain_to_db(0.92 * db_to_gain(meters_.input_peak_db) + 0.08 * in_peak + 1e-12);
    meters_.output_peak_db = gain_to_db(0.92 * db_to_gain(meters_.output_peak_db) + 0.08 * out_peak + 1e-12);
    meters_.gain_reduction_db = 0.88 * meters_.gain_reduction_db + 0.12 * gr_db;
    const double denom = std::sqrt((in_l * in_l + 1e-12) * (in_r * in_r + 1e-12));
    const double corr = denom > 0.0 ? clamp((in_l * in_r) / denom, -1.0, 1.0) : 1.0;
    meters_.correlation = 0.995 * meters_.correlation + 0.005 * corr;
    meters_.clipping = out_peak >= 0.985 || gr_db > 8.0;
}

void ArSonKuPikEngine::process(float** planes, std::size_t channels, std::size_t frames)
{
    if (!planes || channels == 0 || frames == 0) return;
    channels = std::min<std::size_t>(channels, channels_);
    if (channels == 0) return;

    const double input_gain = db_to_gain(working_.output.input_gain_db);
    const double output_gain = db_to_gain(working_.output.output_gain_db);
    const double bypass_t = params_.bypass ? 1.0 : 0.0;

    // Mono sources are processed as dual-mono so color/limiter behavior remains stable.
    for (std::size_t i = 0; i < frames; ++i) {
        float original[kMaxChannels]{};
        for (std::size_t ch = 0; ch < channels; ++ch) original[ch] = planes[ch] ? sane(planes[ch][i]) : 0.0f;
        float l = original[0];
        float r = channels > 1 ? original[1] : original[0];
        const double in_l = l;
        const double in_r = r;

        // Extension-style smart headroom: hot mastered media is trimmed before
        // the creative rack, so the compressor/color/width stages have room to
        // work cleanly. The reserved headroom is restored later by smart makeup.
        const double input_peak_now = std::max(std::abs(in_l), std::abs(in_r));
        const double peak_attack = 1.0 - std::exp(-1.0 / (sample_rate_ * 0.006));
        const double peak_release = 1.0 - std::exp(-1.0 / (sample_rate_ * 0.180));
        const double peak_alpha = input_peak_now > smart_input_peak_env_ ? peak_attack : peak_release;
        smart_input_peak_env_ += peak_alpha * (input_peak_now - smart_input_peak_env_);
        const double input_db = gain_to_db(smart_input_peak_env_ + 1.0e-12);
        const double target_headroom_db = input_db > -10.0 ? clamp(-9.0 - input_db, -12.0, 0.0) : 0.0;
        const double stage_alpha = 1.0 - std::exp(-1.0 / (sample_rate_ * 0.180));
        smart_headroom_db_ += stage_alpha * (target_headroom_db - smart_headroom_db_);
        const double headroom_gain = db_to_gain(smart_headroom_db_);

        l = sane(static_cast<float>(l * input_gain * headroom_gain));
        r = sane(static_cast<float>(r * input_gain * headroom_gain));

        l = process_channel_eq(0, l);
        r = process_channel_eq(1, r);

        auto comp = compressor_.process(l, r);
        l = comp.first; r = comp.second;

        auto col = process_color(l, r);
        l = col.first; r = col.second;

        auto wid = process_width(l, r);
        l = wid.first; r = wid.second;

        // Extension-style smart restore: bring back the headroom and add clear
        // perceived loudness, but back off when the pre-limiter peak or limiter
        // gain reduction says the rack is getting too hot. This is the main
        // "user hears it immediately" trick, without using saturation as volume.
        const double prelim_peak_now = std::max(std::abs(static_cast<double>(l)), std::abs(static_cast<double>(r))) * output_gain;
        const double prelim_alpha = prelim_peak_now > smart_prelim_peak_env_ ? peak_attack : peak_release;
        smart_prelim_peak_env_ += prelim_alpha * (prelim_peak_now - smart_prelim_peak_env_);
        const double prelim_db = gain_to_db(smart_prelim_peak_env_ + 1.0e-12);
        const double reserved_db = -smart_headroom_db_;
        const double macro_loudness_db = 1.05 + clamp01(params_.enhance / 100.0) * 1.35 + clamp01(params_.vocal_body / 100.0) * 0.30;
        const double peak_penalty = std::max(0.0, prelim_db + 1.35) * 0.90;
        const double limiter_penalty = std::max(0.0, limiter_.gain_reduction_db() - 1.25) * 0.65;
        const double target_makeup_db = clamp(macro_loudness_db + reserved_db * 0.62 - peak_penalty - limiter_penalty, 0.75, 5.6);
        smart_makeup_db_ += stage_alpha * (target_makeup_db - smart_makeup_db_);
        const double total_output_gain = output_gain * db_to_gain(smart_makeup_db_);

        l = sane(static_cast<float>(l * total_output_gain));
        r = sane(static_cast<float>(r * total_output_gain));

        if (working_.output.limiter_enabled) {
            auto lim = limiter_.process(l, r);
            l = lim.first; r = lim.second;
        }

        const double bypass = bypass_smooth_.process(bypass_t);
        l = sane(static_cast<float>(l * (1.0 - bypass) + original[0] * bypass));
        r = sane(static_cast<float>(r * (1.0 - bypass) + (channels > 1 ? original[1] : original[0]) * bypass));

        if (planes[0]) planes[0][i] = l;
        if (channels > 1 && planes[1]) planes[1][i] = r;
        // Additional channels get the same core EQ/dynamics/output but no stereo magic, useful for 5.1/7.1 compatibility.
        for (std::size_t ch = 2; ch < channels; ++ch) {
            float x = sane(static_cast<float>(original[ch] * input_gain));
            x = process_channel_eq(ch, x);
            x = sane(static_cast<float>(x * output_gain));
            x = sane(static_cast<float>(x * (1.0 - bypass) + original[ch] * bypass));
            if (planes[ch]) planes[ch][i] = x;
        }

        const double gr = std::max(compressor_.gain_reduction_db(), limiter_.gain_reduction_db());
        update_meters(in_l, in_r, l, r, gr);
    }
}

} // namespace arsonkupik
