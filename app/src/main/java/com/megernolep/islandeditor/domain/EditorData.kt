package com.megernolep.islandeditor.domain

/** Konstanta & tabel statis editor (kategori natural, NPC, zona pemicu, alias, dst). */
object EditorData {
    // ---- tipe pulau ----
    val typeOrder = listOf(
        "temperate_forest", "tropical_forest", "desert", "tundra",
        "snow_field", "grassland", "swamp_mud", "volcanic",
    )

    // ---- natural ----
    class NatCat(val key: String, val label: String, val color: Int)
    val natCats: List<NatCat> = listOf(
        NatCat("tree", "Pohon", 0xFF1C6B1C.toInt()),
        NatCat("bush", "Semak", 0xFF4C8A3A.toInt()),
        NatCat("grass", "Rumput/bunga", 0xFF8FAE3A.toInt()),
        NatCat("rock", "Batu (semua)", 0xFF8A8578.toInt()),
        NatCat("rock_big", "Batu besar", 0xFF5E5A50.toInt()),
        NatCat("rock_small", "Batu kecil / kerikil", 0xFFCFC8B4.toInt()),
        NatCat("rock_unk", "Batu belum ditandai ukurannya", 0xFFA09A88.toInt()),
        NatCat("rock_spec", "Batu khusus / mineral / bangunan", 0xFFB08A8A.toInt()),
        NatCat("ore", "Bijih/mineral", 0xFFB48A3A.toInt()),
        NatCat("cactus", "Kaktus", 0xFF5AA04A.toInt()),
        NatCat("other", "Lainnya", 0xFFD0D0D0.toInt()),
    )
    val natCatByKey: Map<String, NatCat> = natCats.associateBy { it.key }

    val rockSpecialRe = Regex("Communication Center|Defense Fortress|Ice Box|Decaying|Alpha Stone|Bravo Stone|Charlie Stone|Lava Hardens|Frozen Mud|Marble|Obsidian|Sapphire|Silver Ore", RegexOption.IGNORE_CASE)
    val rockBigRe = Regex("Big Stone|Large River Stone", RegexOption.IGNORE_CASE)
    val rockSmallRe = Regex("^(Stone|River Stone|Gravel Filled With Moss)$", RegexOption.IGNORE_CASE)
    val rockSizeCat = mapOf("besar" to "rock_big", "kecil" to "rock_small", "?" to "rock_unk", "khusus" to "rock_spec")
    val rockSizeTag = mapOf("besar" to "BESAR", "kecil" to "kecil", "khusus" to "khusus", "?" to "?")

    /** Tebakan ukuran batu dari nama; [marks] = penanda buatan pengguna (menang atas tebakan). */
    fun rockSize(n: NatItem, marks: Map<Int, String>): String? {
        if (n.cat != "rock") return null
        marks[n.id]?.let { return it }
        return when {
            rockSpecialRe.containsMatchIn(n.name) -> "khusus"
            rockBigRe.containsMatchIn(n.name) -> "besar"
            rockSmallRe.matches(n.name) -> "kecil"
            else -> "?"
        }
    }

    fun natCatsOf(n: NatItem, marks: Map<Int, String>): List<String> =
        if (n.cat == "rock") listOf("rock", rockSizeCat[rockSize(n, marks)] ?: "rock_unk") else listOf(n.cat)

    fun natColor(n: NatItem, marks: Map<Int, String>): Int {
        val key = if (n.cat == "rock") (rockSizeCat[rockSize(n, marks)] ?: "rock_unk") else n.cat
        return (natCatByKey[key] ?: natCatByKey.getValue("other")).color
    }

    fun natLabel(n: NatItem, marks: Map<Int, String>): String =
        if (n.cat == "rock") "[${rockSizeTag[rockSize(n, marks)]}] ${n.name}" else n.name

    // ---- hewan ----
    val dietLabel = mapOf("herbivora" to "Herbivora", "karnivora" to "Karnivora", "omnivora" to "Omnivora", "pemakan_bangkai" to "Pemakan bangkai")
    val dietColor = mapOf("herbivora" to 0xFF5CC85C.toInt(), "karnivora" to 0xFFFF4040.toInt(), "omnivora" to 0xFFE8A04A.toInt(), "pemakan_bangkai" to 0xFFB070D0.toInt())
    val groupLabel = mapOf(
        "land" to "land (darat)", "beach" to "beach (pantai)", "lake_shallow" to "lake_shallow (danau dangkal)",
        "lake_deep" to "lake_deep (danau dalam)", "ocean" to "ocean (laut)",
    )
    val groupTileName = mapOf("land" to "darat", "beach" to "pantai", "ocean" to "laut", "lake_shallow" to "danau", "lake_deep" to "danau")
    fun groupTileOk(group: String, code: Int): Boolean = when (group) {
        "land" -> code <= 7
        "beach" -> code == 9 || code == 10
        "ocean" -> code == 11 || code == 12
        "lake_shallow", "lake_deep" -> code == 14
        else -> true
    }
    val decorAlias = mapOf(2133 to "Brontosaurus", 2143 to "Brontosaurus racun minyak")
    val decorPin = listOf(2133, 2143)

    // ---- landmark ----
    private val lmAlias: List<Pair<Regex, (MatchResult) -> String>> = listOf(
        Regex("ST_train_wreckage_01_([a-z])", RegexOption.IGNORE_CASE) to { m -> "Bangkai kereta rusak ${m.groupValues[1].uppercase()} (gerbong/rel)" },
        Regex("ST_train_wreckage", RegexOption.IGNORE_CASE) to { _ -> "Bangkai kereta rusak" },
        Regex("ST_highway_01", RegexOption.IGNORE_CASE) to { _ -> "Jalan raya rusak (jalan tol / rel layang)" },
        Regex("ST_airplane_wreckage", RegexOption.IGNORE_CASE) to { _ -> "Bangkai pesawat" },
        Regex("ST_wreckedship", RegexOption.IGNORE_CASE) to { _ -> "Kapal karam" },
        Regex("ST_Building_school", RegexOption.IGNORE_CASE) to { _ -> "Gedung sekolah" },
        Regex("overpass", RegexOption.IGNORE_CASE) to { _ -> "Jalan layang" },
        Regex("busstop|schoolbus", RegexOption.IGNORE_CASE) to { _ -> "Halte / bus sekolah" },
    )
    fun lmAlias(name: String): String {
        for ((re, f) in lmAlias) { val m = re.find(name); if (m != null) return f(m) }
        return ""
    }
    private val globalRe = Regex("ST_train_wreckage_01|ST_highway_01", RegexOption.IGNORE_CASE)
    /** Prefab yang diperlakukan client sebagai landmark GLOBAL (kereta/jalan rusak). */
    fun isGlobalPrefab(prefab: String?): Boolean = globalRe.containsMatchIn(prefab ?: "")

    // ---- NPC ----
    private fun bot(id: String, name: String, idx: Int, male: Boolean, radius: Int, active: Boolean = false, prefab: String? = null) =
        NpcDef(id, name, "bot", idx = idx, male = male, radius = radius, active = active, prefab = prefab)

    val npcStory = listOf(
        NpcDef("story_k", "K (cerita)", "story", epic = 0, prefab = "Models/NPC/F_NPC_K_Story.prefab"),
        NpcDef("story_t", "T / Agen (cerita)", "story", epic = 1, prefab = "Models/NPC/NPC_AgentMale_Story.prefab"),
    )
    val npcBots = listOf(
        bot("k", "K", 0, false, 6, true, "Models/NPC/F_NPC_K.prefab"), bot("liu", "Liu", 1, false, 6, true),
        bot("nowak", "Nowak", 2, true, 6, true), bot("x", "X", 3, true, 4, true),
        bot("lama", "Dr. Lama", 4, true, 5, true), bot("rodriguez", "Rodriguez", 5, true, 6),
        bot("sawarat", "Sawarat", 6, false, 6), bot("mccain", "McCain", 7, true, 6),
        bot("zein", "Zein", 8, true, 6), bot("pia", "Pia", 9, false, 5), bot("e", "E", 10, true, 5),
        bot("f", "F", 11, false, 5), bot("g", "G", 12, true, 5),
        bot("_924s", "Sub-komite 924", 13, true, 3), bot("_628s", "Sub-komite 628", 14, false, 3),
        bot("_415s", "Sub-komite 415", 15, true, 3), bot("hauata", "Hauata", 16, true, 6),
        bot("maki", "Maki", 17, false, 6), bot("josipovic", "Josipovic", 18, true, 6),
        bot("charlie", "Charlie", 19, true, 7, true), bot("d383", "Agen D383", 20, true, 5, true),
    )
    val npcAll: List<NpcDef> = npcStory + npcBots
    val npcById: Map<String, NpcDef> = npcAll.associateBy { it.id }

    /** Posisi tetap K/T per level (kolom = digit terakhir template_id - 1). */
    val storyPos: Map<String, List<Pt>> = mapOf(
        "story_k" to listOf(Pt(68, 73), Pt(176, 60), Pt(153, 36), Pt(160, 60), Pt(79, 38)),
        "story_t" to listOf(Pt(73, 73), Pt(171, 60), Pt(148, 36), Pt(155, 60), Pt(79, 43)),
    )
    fun storyCol(templateId: String): Int {
        val d = templateId.trim().lastOrNull()?.digitToIntOrNull() ?: return 0
        return if (d in 1..5) d - 1 else 0
    }

    // ---- zona pemicu misi tutorial ----
    private fun trig(id: String, label: String, flow: String, color: Long) = TrigType(id, label, flow, color.toInt())
    val trigTypes = listOf(
        trig("firststep", "Langkah pertama (trigger_arrive_firststep)", "trigger_arrive_firststep", 0xFFFFD23F),
        trig("fruits", "Area buah / pohon kurma (trigger_gather_fruits)", "trigger_gather_fruits", 0xFFFF8A3D),
        trig("brachio", "Dekat brachiosaurus (trigger_arrive_brachio)", "trigger_arrive_brachio", 0xFF37D7FF),
        trig("meet", "Bertemu brachiosaurus (trigger_meet_brachio)", "trigger_meet_brachio", 0xFF5CFF7A),
        trig("shipyard", "Area rakit (trigger_arrive_shipyard)", "trigger_arrive_shipyard", 0xFFC86BD8),
        trig("todo_pia", "SELESAIKAN misi: ikuti anjing (follow_pia)", "todo:follow_pia", 0xFFFFFFFF),
        trig("todo_north", "SELESAIKAN misi: ke utara (move_northward)", "todo:move_northward", 0xFFB4FF6B),
        trig("todo_chief", "SELESAIKAN misi: temui K di rakit (meet_chief)", "todo:meet_chief", 0xFFFFB3D9),
        trig("thornbush", "Dekat thornbush / jalan terhalang (trigger_arrive_obstacle)", "trigger_arrive_obstacle", 0xFFFF4D4D),
        trig("custom", "Khusus (ketik nama alur)", "", 0xFFFF5AD1),
    )
    val trigColorByFlow: Map<String, Int> = trigTypes.filter { it.flow.isNotEmpty() }.associate { it.flow to it.color }
    const val TRIG_DEFAULT_COLOR = 0xFFFF5AD1.toInt()

    // ---- lain-lain ----
    const val THORN_PROP_ID = "410"
    val thornPlantIds = setOf(11001, 11039)
    val brushSizes = listOf(1, 3, 7, 15)
}
