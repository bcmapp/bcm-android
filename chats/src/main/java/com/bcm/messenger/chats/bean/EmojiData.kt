package com.bcm.messenger.chats.bean

import com.bcm.messenger.common.ui.gridhelper.transformAndFillEmptyData
import com.bcm.messenger.chats.util.FourRowDataTransform

/**
 * Created by Kin on 2018/7/26
 */

/**
 *
 * 😃😁😂😲😳😘😆😭😰😉
 * 😊😜😌😍😡😤😱😒😷😖
 * 😅😚😓😝😞😠😢😧😏😣
 * 😨😩😪👿😋😄😫😴😔😟
 * 👦👧👨👩👫👪💏🙅🙋🙇
 * 🙈🐷🐶👻👀🏡👆👇👈👉
 * 👌👏👊👍👎👋👐💪🙌🙏
 * 💅💢❗❓⭕❌🚫🔞🔥🎉
 * ✨💥💔❤💖🌀💦🎵🎮🏀
 * 👙🏊🚶🏃🌵🌱🍃🌹🌻🌼
 */
val emojiList by lazy {
    listOf(
            "\uD83D\uDE03", "\uD83D\uDE01", "\uD83D\uDE02", "\uD83D\uDE32", "\uD83D\uDE33", "\uD83D\uDE18", "\uD83D\uDE06", "\uD83D\uDE2D", "\uD83D\uDE30", "\uD83D\uDE09",
            "\uD83D\uDE0A", "\uD83D\uDE1C", "\uD83D\uDE0D", "\uD83D\uDE0D", "\uD83D\uDE21", "\uD83D\uDE24", "\uD83D\uDE31", "\uD83D\uDE12", "\uD83D\uDE37", "\uD83D\uDE16",
            "\uD83D\uDE05", "\uD83D\uDE1A", "\uD83D\uDE13", "\uD83D\uDE1D", "\uD83D\uDE1E", "\uD83D\uDE20", "\uD83D\uDE22", "\uD83D\uDE27", "\uD83D\uDE0F", "\uD83D\uDE23",
            "\uD83D\uDE28", "\uD83D\uDE29", "\uD83D\uDE2A", "\uD83D\uDC7F", "\uD83D\uDE0B", "\uD83D\uDE04", "\uD83D\uDE2B", "\uD83D\uDE34", "\uD83D\uDE14", "\uD83D\uDE1F",
            "\uD83D\uDC66", "\uD83D\uDC67", "\uD83D\uDC68", "\uD83D\uDC69", "\uD83D\uDC6B", "\uD83D\uDC6A", "\uD83D\uDC8F", "\uD83D\uDE45", "\uD83D\uDE4B", "\uD83D\uDE47",
            "\uD83D\uDE48", "\uD83D\uDC37", "\uD83D\uDC36", "\uD83D\uDC7B", "\uD83D\uDC40", "\uD83C\uDFE1", "\uD83D\uDC46", "\uD83D\uDC47", "\uD83D\uDC48", "\uD83D\uDC49",
            "\uD83D\uDC4C", "\uD83D\uDC4F", "\uD83D\uDC4A", "\uD83D\uDC4D", "\uD83D\uDC4E", "\uD83D\uDC4B", "\uD83D\uDC50", "\uD83D\uDCAA", "\uD83D\uDE4C", "\uD83D\uDE4F",
            "\uD83D\uDC85", "\uD83D\uDCA2", "❗", "❓", "⭕", "❌", "\uD83D\uDEAB", "\uD83D\uDD1E", "\uD83D\uDD25", "\uD83C\uDF89",
            "✨", "\uD83D\uDCA5", "\uD83D\uDC94", "❤", "\uD83D\uDC96", "\uD83C\uDF00", "\uD83D\uDCA6", "\uD83C\uDFB5", "\uD83C\uDFAE", "\uD83C\uDFC0",
            "\uD83D\uDC59", "\uD83C\uDFCA", "\uD83D\uDEB6", "\uD83C\uDFC3", "\uD83C\uDF35", "\uD83C\uDF31", "\uD83C\uDF43", "\uD83C\uDF39", "\uD83C\uDF3B", "\uD83C\uDF3C"
    )
}

val transformEmojiList: List<String?> by lazy {
    try {
        transformAndFillEmptyData(FourRowDataTransform(4, 7), emojiList)
    } catch (e: Exception) {
        emojiList
    }
}