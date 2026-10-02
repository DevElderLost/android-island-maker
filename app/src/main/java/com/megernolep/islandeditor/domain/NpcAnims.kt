package com.megernolep.islandeditor.domain

import android.content.Context
import org.json.JSONObject

/** Satu klip animasi pemain yang boleh dipilih untuk NPC bot (assets/npc_anims.json, dari player_animation_clips.txt). */
data class NpcAnim(val id: String, val cat: String, val label: String, val loop: Boolean, val length: Float)

object NpcAnims {
    /** Dimuat sekali per ViewModel; katalog kosong kalau asset hilang (panel menampilkan peringatan). */
    fun load(ctx: Context): List<NpcAnim> = try {
        val root = JSONObject(ctx.assets.open("npc_anims.json").bufferedReader(Charsets.UTF_8).use { it.readText() })
        val a = root.getJSONArray("anims")
        (0 until a.length()).map { i ->
            val r = a.getJSONArray(i)
            NpcAnim(r.getString(0), r.getString(1), r.getString(2), r.getInt(3) != 0, r.getDouble(4).toFloat())
        }
    } catch (e: Exception) {
        emptyList()
    }

    fun categories(list: List<NpcAnim>): List<String> = list.map { it.cat }.distinct()
}
