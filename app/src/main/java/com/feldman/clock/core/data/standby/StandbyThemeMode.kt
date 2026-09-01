package com.feldman.clock.core.data.standby

enum class StandbyThemeMode(val id: Int) {
    Auto(0),
    Light(1),
    Dark(2);

    companion object {
        fun from(id: Int) = entries.firstOrNull { it.id == id } ?: Auto
    }
}
