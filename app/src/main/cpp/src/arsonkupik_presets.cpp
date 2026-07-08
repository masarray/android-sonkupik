#include "arsonkupik_dsp.hpp"

#include <algorithm>

namespace arsonkupik {
namespace {
EqBand b(const char* id, EqType type, double frequency, double gain, double q, int slope = 12, bool enabled = true)
{
    EqBand band;
    band.id = id;
    band.type = type;
    band.frequency = frequency;
    band.gain_db = gain;
    band.q = q;
    band.slope_db_per_oct = slope;
    band.enabled = enabled;
    return band;
}

std::vector<EqBand> masari_eq()
{
    return {
        b("cut-low", EqType::LowCut, 25, 0, 0.70710678, 24),
        b("low-body", EqType::LowShelf, 76, 1.30, 0.64, 12),
        b("mud-clean", EqType::Bell, 325, -1.32, 0.86, 12),
        b("presence", EqType::Bell, 2180, 1.16, 0.64, 12),
        b("detail", EqType::Bell, 6250, 1.60, 0.52, 12),
        b("sparkle", EqType::HighShelf, 12650, 4.38, 0.38, 12),
        b("vocal-acoustic-body", EqType::Bell, 170, 0.90, 2.50, 12),
        b("vocal-body-490", EqType::Bell, 490, 1.50, 0.80, 12)
    };
}

CompressorConfig masari_comp()
{
    return {true, -24.4, 1.7, 24.0, 0.032, 0.21, 0.88, 92.0};
}

ColorConfig masari_color()
{
    ColorConfig c;
    c.enabled = true;
    c.drive = 1.36;
    c.body_freq = 166;
    c.body = 12.8;
    c.smart_bass = 55;
    c.warmth_freq = 500;
    c.harmonics = 8;
    c.harmonics_freq = 2180;
    c.warmth = 12.8;
    c.air_freq = 12650;
    c.air = 48.8;
    c.ai_high_repair = 46;
    c.velvet_treble = 66;
    c.god_particles = 92.0;
    c.vocal_tickle = 67;
    c.vocal_presence = 55;
    c.mid_projection = 65;
    c.mix = 30.5;
    c.stereo_mid = 70;
    c.mode = "mastering";
    return c;
}

WidthConfig masari_width()
{
    return {true, 73, 153, 100, 101, 128, 200, 56, true, 150, 4.28};
}

OutputConfig masari_output()
{
    return {0.0, 0.35, true, -0.90, 0.82, true, false};
}

Preset base(const char* id, const char* name, const char* description)
{
    Preset p;
    p.id = id;
    p.name = name;
    p.description = description;
    p.eq = masari_eq();
    p.compressor = masari_comp();
    p.color = masari_color();
    p.width = masari_width();
    p.output = masari_output();
    p.macro = {100, 78, 68, 80, 76, 88, true};
    return p;
}

std::vector<Preset> make_presets()
{
    std::vector<Preset> presets;

    // Frozen golden reference from extension v0.3.100: this is the auditory reference curve.
    presets.push_back(base("default", "MasAri", "Golden reference: bass bulat bernapas, stereo magic luas, airy silky treble, ear-tickle detail."));

    {
        auto p = base("mastering", "Mastering Global", "Global-smart mastering: living mid, glerr deep bass pocket, open stereo sheen, velvet treble.");
        p.eq = {
            b("master-sub-clean", EqType::LowCut, 26, 0, 0.70710678, 24),
            b("master-weight", EqType::LowShelf, 82, 1.18, 0.68),
            b("vocal-acoustic-body", EqType::Bell, 170, 0.98, 2.45),
            b("master-mud-control", EqType::Bell, 345, -0.66, 0.86),
            b("vocal-body-490", EqType::Bell, 490, 1.46, 0.80),
            b("master-vocal-forward", EqType::Bell, 2050, 0.88, 0.70),
            b("master-harsh-polish", EqType::Bell, 4050, -0.16, 1.04),
            b("master-detail", EqType::Bell, 6500, 0.98, 0.84),
            b("master-treble-skin", EqType::Bell, 8750, 0.38, 1.04),
            b("master-air", EqType::HighShelf, 12650, 2.86, 0.42)
        };
        p.compressor = {true, -24.2, 1.62, 26, 0.036, 0.225, 0.64, 91};
        p.color = {true, 1.32, 166, 14.2, 58, 500, 12.7, 2080, 7, 12650, 34.4, 50, 72, 70.5, 50, 48, 56, 26.2, 48, "mastering"};
        p.width = {true, 64, 134, 100, 102, 114, 180, 74, true, 150, 3.05};
        p.output = {0, 0.70, true, -0.90, 0.50, true, false};
        p.macro = {75, 58, 58, 55, 56, 62, true};
        presets.push_back(p);
    }

    {
        auto p = base("max-enhancer", "Max Enhancer", "Maximum musical enhancement: breathing bass, creamy mid detail, tasteful side tickle, lively stereo.");
        p.eq = {
            b("max-sub-clean", EqType::LowCut, 28, 0, 0.70710678, 24),
            b("max-low-body", EqType::LowShelf, 88, 1.55, 0.66),
            b("max-mud-clean", EqType::Bell, 380, -0.52, 0.62),
            b("max-presence", EqType::Bell, 2550, 1.10, 0.56),
            b("max-detail", EqType::Bell, 6700, 0.76, 0.54),
            b("max-air", EqType::HighShelf, 12950, 2.55, 0.42),
            b("vocal-body-490", EqType::Bell, 490, 1.50, 0.80)
        };
        p.compressor = {true, -24.2, 1.8, 24, 0.032, 0.19, 0.82, 90};
        p.color = {true, 1.48, 164, 15.8, 64, 505, 14.0, 2100, 8, 12750, 31.0, 62, 80, 68.0, 47, 52, 66, 29, 64, "mastering"};
        p.width = {true, 66, 137, 100, 102, 115, 184, 72, true, 158, 3.10};
        p.output = {0, 0.80, true, -0.90, 0.68, true, false};
        p.macro = {70, 74, 64, 62, 64, 70, true};
        presets.push_back(p);
    }

    {
        auto p = base("sonkuhoreg", "SonKuHoreg", "Deep slow-bass pressure: sub torque, wall-shake bass harmonics, far mid, enjoyable 3D sparkle.");
        p.eq = {
            b("horeg-sub-clean", EqType::LowCut, 21, 0, 0.70710678, 24),
            b("horeg-low-body", EqType::LowShelf, 58, 3.20, 0.54),
            b("horeg-sub-torque", EqType::Bell, 72, 1.15, 0.58),
            b("horeg-wall-push", EqType::Bell, 118, 1.05, 0.62),
            b("vocal-acoustic-body", EqType::Bell, 185, 1.15, 2.0),
            b("horeg-mud-clean", EqType::Bell, 345, -0.82, 0.74),
            b("vocal-body-490", EqType::Bell, 490, 1.76, 0.76),
            b("horeg-far-mid-glow", EqType::Bell, 1380, 0.42, 0.68),
            b("horeg-presence", EqType::Bell, 2140, 1.34, 0.60),
            b("horeg-3d-mid-sparkle", EqType::Bell, 3720, 1.00, 0.76),
            b("horeg-detail", EqType::Bell, 6750, 0.54, 0.60),
            b("horeg-air", EqType::HighShelf, 13000, 2.18, 0.50)
        };
        p.compressor = {true, -24.8, 1.75, 24, 0.038, 0.24, 0.72, 89};
        p.color = {true, 1.55, 145, 20.6, 94, 500, 17.2, 2050, 10, 12650, 20.8, 78, 94, 64.5, 54, 63, 76, 32, 78, "mastering"};
        p.width = {true, 64, 126, 100, 101, 113, 148, 92, true, 170, 1.62};
        p.output = {0, 0.90, true, -1.00, 0.76, true, false};
        p.macro = {60, 82, 90, 48, 68, 48, true};
        presets.push_back(p);
    }

    {
        auto p = base("sonkubattle", "SonKuBattle", "SPL battle preset: dBA/dBC energy, dense bass torque, far-throwing mid, clip-aware output control.");
        p.eq = {
            b("battle-sub-clean", EqType::LowCut, 26, 0, 0.70710678, 24),
            b("battle-low-body", EqType::LowShelf, 62, 2.85, 0.52),
            b("battle-dbc-torque", EqType::Bell, 78, 1.45, 0.54),
            b("battle-dbc-punch", EqType::Bell, 112, 1.28, 0.58),
            b("battle-small-speaker-bass", EqType::Bell, 166, 0.78, 0.72),
            b("vocal-acoustic-body", EqType::Bell, 205, 0.92, 1.90),
            b("battle-mud-clean", EqType::Bell, 318, -1.12, 0.78),
            b("vocal-body-490", EqType::Bell, 490, 1.62, 0.74),
            b("battle-dba-throw", EqType::Bell, 1850, 1.18, 0.62),
            b("battle-presence", EqType::Bell, 2550, 1.26, 0.58),
            b("battle-3d-spark", EqType::Bell, 3480, 1.10, 0.72),
            b("battle-harsh-guard", EqType::Bell, 4700, -0.22, 1.00),
            b("battle-detail", EqType::Bell, 7050, 0.58, 0.60),
            b("battle-air", EqType::HighShelf, 12650, 1.92, 0.50)
        };
        p.compressor = {true, -25.1, 1.92, 24, 0.030, 0.17, 0.62, 88};
        p.color = {true, 1.55, 150, 21.4, 94, 505, 15.0, 2250, 10, 12550, 20.2, 79, 94, 66.0, 52, 70, 80, 32, 82, "mastering"};
        p.width = {true, 65, 126, 100, 101, 114, 148, 92, true, 178, 1.66};
        p.output = {0, 0.95, true, -1.00, 0.80, true, false};
        p.macro = {45, 88, 92, 52, 76, 38, true};
        presets.push_back(p);
    }

    {
        auto p = base("sonkubalap", "SonKuBalap", "Efficient sound-battle preset: powerful dBA/dBC energy while avoiding wasteful ultra-low amp load.");
        p.eq = {
            b("balap-sub-clean", EqType::LowCut, 31, 0, 0.70710678, 24),
            b("balap-low-body", EqType::LowShelf, 66, 2.42, 0.50),
            b("balap-efficient-torque", EqType::Bell, 82, 1.30, 0.52),
            b("balap-amp-punch", EqType::Bell, 118, 1.18, 0.56),
            b("balap-bass-harmonic", EqType::Bell, 158, 0.92, 0.68),
            b("vocal-acoustic-body", EqType::Bell, 210, 0.86, 1.85),
            b("balap-mud-clean", EqType::Bell, 305, -1.18, 0.80),
            b("balap-box-control", EqType::Bell, 430, -0.38, 0.88),
            b("vocal-body-490", EqType::Bell, 490, 1.58, 0.74),
            b("balap-mid-throw", EqType::Bell, 1680, 1.05, 0.62),
            b("balap-presence", EqType::Bell, 2450, 1.20, 0.58),
            b("balap-3d-spark", EqType::Bell, 3550, 1.02, 0.72),
            b("balap-tweeter-safe", EqType::Bell, 5200, -0.28, 0.96),
            b("balap-detail", EqType::Bell, 7200, 0.48, 0.60),
            b("balap-air", EqType::HighShelf, 12750, 1.72, 0.50)
        };
        p.compressor = {true, -25.0, 1.88, 25, 0.026, 0.16, 0.56, 88};
        p.color = {true, 1.50, 152, 19.8, 90, 505, 14.2, 2300, 10, 12600, 19.8, 80, 94, 61.5, 53, 72, 82, 30, 84, "mastering"};
        p.width = {true, 65, 127, 100, 100, 116, 150, 92, true, 185, 1.72};
        p.output = {0, 0.95, true, -1.00, 0.78, true, false};
        p.macro = {42, 88, 88, 50, 78, 40, true};
        presets.push_back(p);
    }

    {
        auto p = base("audiophile-pop", "Audiophile", "Popular audiophile balance: clean vocal center, refined sparkle, controlled bass, non-fatiguing.");
        p.eq = {
            b("audiophile-cut", EqType::LowCut, 30, 0, 0.70710678, 24),
            b("audiophile-low", EqType::LowShelf, 84, 0.95, 0.70),
            b("audiophile-clean", EqType::Bell, 350, -0.72, 0.88),
            b("vocal-focus-audiophile", EqType::Bell, 1900, 0.65, 0.72),
            b("audiophile-detail", EqType::Bell, 6100, 0.78, 0.62),
            b("audiophile-air", EqType::HighShelf, 12800, 2.18, 0.48),
            b("vocal-body-490", EqType::Bell, 490, 1.50, 0.80)
        };
        p.compressor = {true, -24, 1.65, 22, 0.032, 0.22, 0.55, 90};
        p.color = {true, 1.18, 170, 12.8, 54, 500, 10.8, 2050, 6, 12680, 28.4, 53, 77, 63.0, 40, 42, 52, 24, 37, "mastering"};
        p.width = {true, 67, 140, 100, 102, 116, 186, 70, true, 148, 3.18};
        p.output = {0, 0.70, true, -0.90, 0.44, true, false};
        p.macro = {55, 48, 48, 58, 52, 58, true};
        presets.push_back(p);
    }

    {
        auto p = base("pro-music", "Punchy Music", "Punchy bass, thick groove, stronger transient glue, and sparkling musical detail.");
        p.eq = {
            b("pro-cut", EqType::LowCut, 28, 0, 0.70710678, 24),
            b("pro-low", EqType::LowShelf, 82, 2.12, 0.64),
            b("pro-clean", EqType::Bell, 360, -0.62, 0.86),
            b("mid-thick", EqType::Bell, 520, 0.52, 0.78),
            b("pro-presence", EqType::Bell, 2150, 0.95, 0.78),
            b("pro-detail", EqType::Bell, 6000, 0.48, 0.60),
            b("pro-air", EqType::HighShelf, 13600, 1.68, 0.38),
            b("vocal-body-490", EqType::Bell, 490, 1.50, 0.80)
        };
        p.compressor = {true, -23.8, 1.95, 18, 0.026, 0.18, 0.68, 91};
        p.color = {true, 1.55, 165, 20.2, 78, 505, 16.2, 2180, 9, 12700, 30.0, 59, 80, 72.0, 52, 56, 68, 34, 70, "mastering"};
        p.width = {true, 68, 142, 100, 103, 118, 188, 70, true, 150, 3.28};
        p.output = {0, 0.90, true, -0.90, 0.72, true, false};
        p.macro = {68, 75, 74, 62, 66, 72, true};
        presets.push_back(p);
    }

    {
        auto p = base("open-air-field", "Open Air", "Sound lapangan/open-air preset: bigger bass contour, forward vocal guard, side-air sparkle, limiter-safe.");
        p.eq = {
            b("field-cut", EqType::LowCut, 32, 0, 0.70710678, 24),
            b("field-low-contour", EqType::LowShelf, 92, 2.55, 0.68),
            b("field-lowmid-clean", EqType::Bell, 330, -1.28, 0.92),
            b("field-vocal-guard", EqType::Bell, 2050, 1.05, 0.72),
            b("field-bite", EqType::Bell, 4300, 0.26, 0.78),
            b("field-air", EqType::HighShelf, 13800, 1.04, 0.46),
            b("vocal-body-490", EqType::Bell, 490, 1.50, 0.80)
        };
        p.compressor = {true, -25.2, 2.05, 20, 0.028, 0.22, 0.45, 88};
        p.color = {true, 1.42, 180, 18.6, 68, 510, 13.7, 2100, 8, 12950, 17.6, 71, 93, 55.8, 41, 49, 60, 31, 46, "mastering"};
        p.width = {true, 56, 117, 100, 101, 105, 135, 98, true, 190, 1.12};
        p.output = {0, 0.85, true, -1.00, 0.66, true, false};
        p.macro = {48, 70, 72, 42, 58, 38, true};
        presets.push_back(p);
    }

    {
        auto p = base("movie-dolby", "Movie Sub", "Thick sub, clean low-mid, guarded dialogue clarity, smooth cinematic width.");
        p.eq = {
            b("movie-cut", EqType::LowCut, 24, 0, 0.70710678, 24),
            b("movie-low", EqType::LowShelf, 58, 2.35, 0.70),
            b("sub-body", EqType::Bell, 118, 0.82, 0.84),
            b("box-clean", EqType::Bell, 370, -1.55, 0.95),
            b("de-honk", EqType::Bell, 680, -0.85, 0.88),
            b("dialogue", EqType::Bell, 2650, 1.25, 0.78),
            b("movie-detail", EqType::Bell, 6100, 0.30, 0.60),
            b("movie-air", EqType::HighShelf, 13250, 1.32, 0.38),
            b("vocal-body-490", EqType::Bell, 490, 1.50, 0.80)
        };
        p.compressor = {true, -24, 1.7, 18, 0.034, 0.28, 0.35, 90};
        p.color = {true, 1.12, 180, 14.0, 58, 520, 12.6, 1850, 5, 12350, 12.8, 68, 94, 36.5, 25, 30, 36, 24, 15, "warm"};
        p.width = {true, 50, 114, 100, 101, 103, 127, 98, true, 165, 0.82};
        p.output = {0, 0.70, true, -0.95, 0.42, true, false};
        p.macro = {35, 52, 62, 36, 42, 35, true};
        presets.push_back(p);
    }

    {
        auto p = base("podcast", "Podcast", "Voice-safe polish: controlled lows, smooth compression, soft air, no crackle.");
        p.eq = {
            b("podcast-cut", EqType::LowCut, 58, 0, 0.70710678, 24),
            b("podcast-low", EqType::LowShelf, 118, 0.10, 0.64),
            b("vocal-chest", EqType::Bell, 180, 1.22, 0.72),
            b("podcast-clean", EqType::Bell, 330, -2.10, 1.00),
            b("podcast-presence", EqType::Bell, 2050, 1.45, 0.72),
            b("podcast-detail", EqType::Bell, 4200, 0.88, 0.78),
            b("sibilance-soften", EqType::Bell, 6900, -1.80, 1.80),
            b("podcast-air", EqType::HighShelf, 12200, 2.25, 0.42),
            b("vocal-body-490", EqType::Bell, 490, 1.50, 0.80)
        };
        p.compressor = {true, -25.5, 1.85, 24, 0.018, 0.24, 1.25, 84};
        p.color = {true, 0.95, 168, 9.5, 38, 510, 11.5, 2050, 4, 12200, 20.0, 66, 94, 48.0, 42, 64, 72, 22, 0, "clean"};
        p.width = {false, 0, 100, 100, 100, 100, 108, 100, true, 145, 0};
        p.output = {0, 0.85, true, -0.95, 0.30, true, false};
        p.macro = {30, 42, 38, 52, 82, 0, true};
        presets.push_back(p);
    }

    {
        auto p = base("night-listening", "Night Listening", "Soft warm sleep-friendly listening: rounded presence, relaxed highs, no stereo stimulation.");
        p.eq = {
            b("night-cut", EqType::LowCut, 42, 0, 0.70710678, 12),
            b("night-low", EqType::LowShelf, 98, -1.15, 0.64),
            b("night-clean", EqType::Bell, 360, -0.55, 0.82),
            b("night-presence", EqType::Bell, 1500, 0.22, 0.75),
            b("night-detail", EqType::Bell, 4800, -1.25, 0.80),
            b("night-air", EqType::HighShelf, 7800, -2.25, 0.38),
            b("vocal-body-490", EqType::Bell, 490, 0.85, 0.80)
        };
        p.compressor = {true, -28.5, 1.85, 24, 0.028, 0.36, 1.25, 76};
        p.color = {true, 0.75, 158, 5.8, 44, 470, 10.8, 1550, 2, 9800, 5.5, 72, 90, 18, 10, 14, 22, 14, 0, "warm"};
        p.width = {false, 0, 96, 100, 96, 92, 98, 100, true, 120, -1.4};
        p.output = {0, 0.65, true, -1.05, 0.22, true, false};
        p.macro = {25, 28, 42, 26, 38, 0, true};
        presets.push_back(p);
    }

    return presets;
}
} // namespace

const std::vector<Preset>& factory_presets()
{
    static const std::vector<Preset> presets = make_presets();
    return presets;
}

const Preset* find_preset(const std::string& id)
{
    const auto& presets = factory_presets();
    auto it = std::find_if(presets.begin(), presets.end(), [&](const Preset& p) { return p.id == id; });
    return it != presets.end() ? &(*it) : nullptr;
}

const Preset& default_preset()
{
    const Preset* p = find_preset("default");
    return p ? *p : factory_presets().front();
}

} // namespace arsonkupik
