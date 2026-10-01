package com.megernolep.islandeditor.domain

/** Ukuran peta tetap 256x256 tile (sama dengan terrain asli). */
const val W = 256
const val H = 256

/** Format 1 byte/tile (sama dengan whole.biomes): bit 0x80 collidable, 0x40 no-plant, & 0x3F = kode biome. */
const val FLAG_COLLIDABLE = 0x80
const val FLAG_NOPLANT = 0x40
const val FLAGS_ROCK = FLAG_COLLIDABLE or FLAG_NOPLANT

data class Pt(val x: Int, val y: Int)
data class Natural(val x: Int, val y: Int, val entityType: Int)
data class Landmark(
    val x: Int, val y: Int, val prefab: String,
    val rotate: Int = 0,
    val offsetX: Int = 0, val offsetY: Int = 0, val offsetZ: Int = 0,
    val scaleX: Int = 16, val scaleY: Int = 16, val scaleZ: Int = 16,
)
data class Herd(val group: String, val x: Int, val y: Int, val entityType: Int, val level: Int)
data class Decor(val x: Int, val y: Int, val entityType: Int, val yaw: Int)
data class Npc(val id: String, val kind: String, val x: Int, val y: Int, val epic: Int = 0, val radius: Int = 0)
data class Building(val x: Int, val y: Int, val entityType: Int)
data class TrigZone(val flow: String, val exit: String, val color: Int, val cells: Set<Int>)

/**
 * Seluruh isi dokumen pulau. Semua list/set diperlakukan immutable (ganti, jangan ubah);
 * satu-satunya pengecualian adalah [biomes] yang dimutasi langsung saat menggambar,
 * karena itu [deepCopy] dipakai untuk snapshot undo.
 */
data class Doc(
    val templateId: String = "ri20te_gen02",
    val level: Int = 20,
    val role: Int = 4,
    val islandType: String = "temperate_forest",
    val biomes: ByteArray = ByteArray(W * H),
    val naturals: List<Natural> = emptyList(),
    val landmarks: List<Landmark> = emptyList(),
    val ports: List<Pt> = emptyList(),
    val herds: List<Herd> = emptyList(),
    val buildings: List<Building> = emptyList(),
    val decor: List<Decor> = emptyList(),
    val npcs: List<Npc> = emptyList(),
    val thorns: Set<Int> = emptySet(),          // indeks y*W+x
    val trig: List<TrigZone> = emptyList(),
    val spawn: Pt? = null,
    val raft: Pt? = null,
    val buildingAnchor: String = "center",      // "center" | "topleft"
) {
    fun deepCopy(): Doc = copy(biomes = biomes.copyOf())
    fun code(x: Int, y: Int): Int = biomes[y * W + x].toInt() and 0x3F
    fun raw(x: Int, y: Int): Int = biomes[y * W + x].toInt() and 0xFF
    fun inside(x: Int, y: Int) = x in 0 until W && y in 0 until H
    val isBlank: Boolean get() = biomes.all { (it.toInt() and 0x3F) == 0 } && herds.isEmpty()
}

// ---- Katalog (dibaca dari assets) ----
data class NatItem(val id: Int, val name: String, val cat: String, val biomes: List<Int>)
data class LmItem(val prefab: String, val name: String, val folder: String)
data class BldItem(val id: Int, val name: String, val cat: String, val w: Int, val h: Int)
data class AnimalItem(val id: Int, val name: String, val diet: String, val lvMin: Int, val lvMax: Int, val internal: String)
data class TypePreset(
    val key: String, val label: String, val land: Int, val beach: Int, val ocean: Int,
    val oceanBiome: String, val lakeBiome: String, val riverBiome: String, val lava: Boolean,
)
/** Baris spesies template asli: [id hewan, level, jumlah]. */
data class SpeciesRow(val id: Int, val level: Int, val count: Int)

data class NpcDef(
    val id: String, val name: String, val kind: String,
    val idx: Int = 0, val male: Boolean = false, val radius: Int = 0,
    val active: Boolean = false, val prefab: String? = null, val epic: Int = 0,
)
data class TrigType(val id: String, val label: String, val flow: String, val color: Int)
