package com.megernolep.islandeditor.domain

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** Katalog natural/landmark/bangunan/hewan + preset tipe pulau. Dimuat sekali dari assets. */
class Catalog(
    val naturals: List<NatItem>,
    val landmarks: List<LmItem>,
    val buildings: List<BldItem>,
    val animals: Map<Int, AnimalItem>,
    val types: Map<String, TypePreset>,
    val species: Map<String, Map<String, List<SpeciesRow>>>,
) {
    val natById: Map<Int, NatItem> = naturals.associateBy { it.id }
    val bldById: Map<Int, BldItem> = buildings.associateBy { it.id }

    /** Hewan yang tersedia untuk (tipe, grup). Jika tipe itu tak punya grup tsb: gabungan semua tipe. */
    fun speciesFor(type: String, group: String, showAll: Boolean): List<SpeciesRow> {
        if (showAll) return animals.values.map { SpeciesRow(it.id, 0, 0) }
        val own = species[type]?.get(group)
        if (!own.isNullOrEmpty()) return own
        val merged = LinkedHashMap<Int, SpeciesRow>()
        for (g in species.values) for (r in g[group].orEmpty()) {
            val cur = merged[r.id]
            if (cur == null || cur.count < r.count) merged[r.id] = r
        }
        return merged.values.toList()
    }

    fun groupsFor(type: String, showAll: Boolean): List<String> =
        GROUPS.filter { g ->
            showAll || species[type]?.get(g).orEmpty().isNotEmpty() ||
                species.values.any { it[g].orEmpty().isNotEmpty() }
        }

    companion object {
        val GROUPS = listOf("land", "beach", "lake_shallow", "lake_deep", "ocean")

        fun load(ctx: Context): Catalog {
            val cat = JSONObject(readAsset(ctx, "catalog.json"))
            val pre = JSONObject(readAsset(ctx, "presets.json"))

            val nats = cat.getJSONArray("N").mapObjs { a ->
                NatItem(a.getInt(0), a.getString(1), a.getString(2), a.getJSONArray(3).ints())
            }
            val lms = cat.getJSONArray("L").mapObjs { a -> LmItem(a.getString(0), a.getString(1), a.getString(2)) }
            val blds = cat.getJSONArray("B").mapObjs { a ->
                BldItem(a.getInt(0), a.getString(1), a.getString(2), a.getInt(3), a.getInt(4))
            }

            val animals = LinkedHashMap<Int, AnimalItem>()
            val an = pre.getJSONObject("animals")
            for (k in an.keys()) {
                val a = an.getJSONArray(k)
                animals[k.toInt()] = AnimalItem(k.toInt(), a.getString(0), a.getString(1), a.getInt(2), a.getInt(3), a.getString(4))
            }

            val types = LinkedHashMap<String, TypePreset>()
            val tj = pre.getJSONObject("types")
            for (k in tj.keys()) {
                val o = tj.getJSONObject(k)
                types[k] = TypePreset(
                    k, o.getString("label"), o.getInt("land"), o.getInt("beach"), o.getInt("ocean"),
                    o.getString("ocean_biome"), o.getString("lake_biome"), o.getString("river_biome"),
                    o.optBoolean("lava", false),
                )
            }

            val species = LinkedHashMap<String, Map<String, List<SpeciesRow>>>()
            val sj = pre.getJSONObject("species")
            for (t in sj.keys()) {
                val groups = LinkedHashMap<String, List<SpeciesRow>>()
                val gj = sj.getJSONObject(t)
                for (g in gj.keys()) {
                    val rows = gj.getJSONArray(g)
                    groups[g] = (0 until rows.length()).map { i ->
                        val r = rows.getJSONArray(i)
                        SpeciesRow(r.getInt(0), r.getInt(1), r.getInt(2))
                    }
                }
                species[t] = groups
            }
            return Catalog(nats, lms, blds, animals, types, species)
        }

        private fun readAsset(ctx: Context, name: String): String =
            ctx.assets.open(name).bufferedReader(Charsets.UTF_8).use { it.readText() }

        private fun <T> JSONArray.mapObjs(f: (JSONArray) -> T): List<T> =
            (0 until length()).map { f(getJSONArray(it)) }

        private fun JSONArray.ints(): List<Int> = (0 until length()).map { getInt(it) }
    }
}
