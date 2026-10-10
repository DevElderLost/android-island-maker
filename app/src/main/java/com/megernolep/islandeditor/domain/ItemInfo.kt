package com.megernolep.islandeditor.domain

/**
 * ITEM_INFO: indikator tambahan supaya tidak salah pilih.
 *  - Hewan: banyak id berbagi nama yang sama (mis. 3x "Direwolf"), beda hanya di nama internal.
 *    Di sini ditandai EVENT / PET / ELITE / BABY / UNKNOWN + rentang level valid.
 *  - Bangunan: banyak id berbagi nama yang sama (mis. 5x "Communication Center"); bedanya tema model
 *    (nama internal) dan rentang level blueprint, dari data server (assets/building_info.json).
 * Penanda hewan dibaca dari nama internal (AnimalItem.internal); ubah regex di bawah jika ada yang meleset.
 */
/** Data tambahan bangunan dari data server (assets/building_info.json): nama internal + rentang level blueprint. */
data class BldExtra(val internal: String, val lvMin: Int, val lvMax: Int)

object ItemInfo {
    private val eventRe = Regex("xmas|hanbok|witch|pumpkin|season2|_bee|shark|hula|watermelon|paper")

    fun animalTags(a: AnimalItem): List<String> = buildList {
        val s = a.internal
        if (eventRe.containsMatchIn(s)) add("EVENT")
        if (s.contains("_for_pet") || s.contains("_store")) add("PET")
        if (s.endsWith("_elite") || s.contains("_elite_")) add("ELITE")
        if (s.contains("_baby")) add("BABY")
        if (s.contains("_unknown")) add("UNKNOWN")
    }

    fun isEvent(a: AnimalItem): Boolean = "EVENT" in animalTags(a)

    fun levelText(a: AnimalItem): String = if (a.lvMin == a.lvMax) "lv${a.lvMin}" else "lv${a.lvMin}\u2013${a.lvMax}"

    /** Label baris daftar: hewan event diberi awalan [EVENT] supaya beda dari hewan biasa bernama sama. */
    fun animalLabel(a: AnimalItem, name: String = a.name): String = if (isEvent(a)) "[EVENT] $name" else name

    /** Teks kanan baris daftar: id, rentang level, penanda. */
    fun animalSub(a: AnimalItem): String {
        val t = animalTags(a).filter { it != "EVENT" }
        return "#${a.id} \u00b7 ${levelText(a)}" + if (t.isEmpty()) "" else " \u00b7 " + t.joinToString("/")
    }

    fun animalSearch(a: AnimalItem): String = a.internal + " " + animalTags(a).joinToString(" ")

    /** Hewan lain dengan nama tampilan yang sama. */
    fun sameName(cat: Catalog, a: AnimalItem): List<AnimalItem> =
        cat.animals.values.filter { it.name == a.name && it.id != a.id }

    // ---------------- bangunan ----------------
    /** Tema/varian model dari nama internal (id berbeda, nama tampilan sama). */
    fun bldTheme(cat: Catalog, id: Int): String? {
        val s = cat.bldExtra[id]?.internal ?: return null
        return when {
            s.contains("seol") -> "salju (seol)"
            s.contains("volca") -> "vulkanik"
            s.contains("neglected") -> "terbengkalai"
            s.contains("sunset") -> "sunset"
            s.contains("be_building") -> "be_building"
            s.endsWith("_02") -> "tipe 02"
            else -> "standar"
        }
    }

    /** Rentang level blueprint bangunan, mis. "lv1\u201380". */
    fun bldRange(cat: Catalog, id: Int): String? =
        cat.bldExtra[id]?.let { if (it.lvMin == it.lvMax) "lv${it.lvMin}" else "lv${it.lvMin}\u2013${it.lvMax}" }

    /** Nama + tema bila nama itu dipakai lebih dari satu id. */
    fun bldLabel(cat: Catalog, b: BldItem): String {
        val dup = cat.buildings.count { it.name == b.name } > 1
        val th = if (dup) bldTheme(cat, b.id) else null
        return if (th != null) "${b.name} \u00b7 $th" else b.name
    }

    fun bldSub(cat: Catalog, b: BldItem): String =
        "#${b.id} \u00b7 ${b.w}\u00d7${b.h}" + (bldRange(cat, b.id)?.let { " \u00b7 $it" } ?: "")

    fun bldSameName(cat: Catalog, b: BldItem): List<BldItem> =
        cat.buildings.filter { it.name == b.name && it.id != b.id }.sortedBy { it.id }

    /** true bila level pulau di luar rentang level bangunan. */
    fun bldOutOfRange(cat: Catalog, id: Int, islandLevel: Int): Boolean =
        cat.bldExtra[id]?.let { islandLevel < it.lvMin || islandLevel > it.lvMax } ?: false
}
