package com.masari.arsonkupik.audio

enum class PresetGroup { Signature, Device, Power, VoiceRelax, User }

data class ArSonKuPikPresetUi(
    val id: String,
    val name: String,
    val group: PresetGroup,
    val shortCopy: String,
    val defaultBass: Float,
    val defaultVocal: Float,
    val defaultWidth: Float,
    val defaultAir: Float,
    val defaultLoud: Float,
)

object PresetCatalog {
    val builtIns = listOf(
        ArSonKuPikPresetUi("default", "Mas Ari Signature", PresetGroup.Signature, "Bass empuk, vocal maju, stereo lebar, treble hidup.", 68f, 76f, 88f, 80f, 78f),
        ArSonKuPikPresetUi("mastering", "Mastering Global", PresetGroup.Signature, "Balance mastering yang hidup dan aman untuk semua lagu.", 58f, 56f, 62f, 55f, 58f),
        ArSonKuPikPresetUi("max-enhancer", "Max Enhancer", PresetGroup.Signature, "Lebih exciting, padat, detail, tapi tetap musical.", 64f, 64f, 70f, 62f, 74f),
        ArSonKuPikPresetUi("audiophile-pop", "Earbuds Sweet", PresetGroup.Device, "Bersih, manis, tidak capek untuk TWS/headphone.", 52f, 62f, 54f, 58f, 52f),
        ArSonKuPikPresetUi("sonkuhoreg", "Bluetooth Horeg", PresetGroup.Device, "Padat, besar, party, tapi limiter-safe.", 90f, 68f, 48f, 48f, 82f),
        ArSonKuPikPresetUi("night-listening", "Relax Night", PresetGroup.VoiceRelax, "Smooth, hangat, tidak menusuk, nyaman lama.", 36f, 48f, 30f, 30f, 36f),
        ArSonKuPikPresetUi("podcast", "Vocal Forward", PresetGroup.VoiceRelax, "Vokal lebih dekat dan artikulasi lebih jelas.", 35f, 80f, 25f, 42f, 45f),
        ArSonKuPikPresetUi("sonkubattle", "SonKuBattle", PresetGroup.Power, "Energi SPL, dense bass torque, jauh, clip-aware.", 88f, 72f, 44f, 58f, 86f),
        ArSonKuPikPresetUi("sonkubalap", "SonKuBalap", PresetGroup.Power, "Efisien untuk lari jauh dan output terasa keras.", 82f, 70f, 48f, 56f, 82f),
        ArSonKuPikPresetUi("open-air-field", "Open Air", PresetGroup.Power, "Vocal guard, side-air sparkle, cocok lapangan.", 70f, 72f, 58f, 60f, 72f),
        ArSonKuPikPresetUi("movie-dolby", "Movie Sub", PresetGroup.Power, "Sub tebal, dialog aman, cinematic width.", 72f, 60f, 62f, 45f, 58f),
        ArSonKuPikPresetUi("pro-music", "Car Audio Loud", PresetGroup.Device, "Low stabil, vocal jelas, loudness aman untuk mobil.", 68f, 62f, 58f, 62f, 70f),
    )
}
