package com.guitarcoach.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.clickable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

private data class ConceptCard(
    val title: String,
    val plain: String,   // 大白话解释
    val onGuitar: String, // 吉他上的具体操作
    val drill: String,   // 马上能试的练习
)

/**
 * F102 乐理概念卡片：覆盖痛点调研中初学者最高频的乐理问题。
 * 每张卡固定三段：大白话 → 在吉他上怎么操作 → 马上能试的小练习。
 */
private val CONCEPT_CARDS = listOf(
    ConceptCard(
        "六线谱怎么看？",
        "六条线就是吉他的六根弦，从上到下 = 从最细到最粗。线上的数字告诉你「哪根弦按第几品」，从左往右按时间顺序弹。",
        "最上面的线是 1 弦（最细的高音 E），最下面是 6 弦（最粗的低音 E）。数字 0 = 空弦（左手不按直接拨）。",
        "去「识谱」页粘贴一段六线谱，对照解析结果逐个音认一遍。",
    ),
    ConceptCard(
        "G、Am 这些是什么意思？",
        "字母是和弦名字（根音），带 m 的是小三和弦，听感更暗。和弦 = 左手同时按住几个音、右手一起拨或扫。",
        "G 和弦：左手 2 指按 6 弦 3 品、1 指 5 弦 2 品、3 指 1 弦 3 品。Am：1 指 2 弦 1 品、2 指 4 弦 2 品、3 指 3 弦 2 品。",
        "练 G → Am → G 切换，每个和弦停 4 拍，目标是换和弦不停顿。",
    ),
    ConceptCard(
        "变调夹夹几品怎么算？",
        "变调夹是夹在指板上的夹子，夹第 n 品 = 整把吉他升高 n 个半音。指法不变、调变高，是最省力的移调工具。",
        "公式：目标调比原调高几个半音，就夹几品。例：C 形指法想变 D 调，D 比 C 高 2 个半音 → 夹 2 品。",
        "夹 2 品弹你会的 C 和弦，对比空品版本，听整体升高了多少。",
    ),
    ConceptCard(
        "降key / 升key（移调）是什么？",
        "一首歌原调太高或太低唱不上去，就整体挪到另一个调：旋律关系不变，只是整体音高变化。这就是移调（降key=整体调低，升key=整体调高）。",
        "吉他上两条路：① 变调夹移位（保指法）；② 换一套和弦指法（如 G 调进行换成 C 调指法）。",
        "同一段 riff 分别在空品与 5 品弹，跟唱对比哪个调舒服。",
    ),
    ConceptCard(
        "大调和小调有什么区别？",
        "大调听感明亮，小调听感忧郁。两者可以共用同一组音，区别在「把哪个音当家」——小调的主音在大调的第六级上。",
        "C 大调和 A 小调用的是同一组音，但 C 调围绕 C 和弦进行，Am 调围绕 Am。指板上同一个把位能弹两种情绪。",
        "弹 C→G→Am→F，再弹 Am→F→C→G，听情绪差别。",
    ),
    ConceptCard(
        "五声音阶为什么先练它？",
        "五个音组成的音阶（C 调 = C D E G A），没有容易撞车的音，怎么弹都不难听——即兴、riff、solo 的地基。",
        "小调五声音阶第一把位：食指整体按第 5 品，无名指/小指负责 7、8 品，从 6 弦爬到 1 弦。",
        "开节拍器 60 BPM，每天 5 分钟把位爬音，速度每周加 10。",
    ),
    ConceptCard(
        "琶音、分解和弦是什么？",
        "琶音 = 把和弦的音一个个依次弹出来；分解和弦是它的俗称，弹唱伴奏最常用的手法。",
        "C 和弦分解：拇指管 5 弦（根音），食指/中指/无名指分别管 3/2/1 弦，按 5-3-2-1 的顺序拨。",
        "练 53231323 模式，节拍器 60 BPM 起步，拨片匀速。",
    ),
    ConceptCard(
        "为什么总有杂音？怎么护弦？",
        "电吉他拾音器灵敏度极高，不弹的弦会共振拾音。护弦 = 用手指或手掌把不弹的弦消音，是电吉他入门第一功课。",
        "右手掌缘轻搭琴桥附近做闷音；左手空闲手指自然搭在相邻弦上消音；推完弦的音记得松回。",
        "弹 1 弦时检查 2-6 弦是否安静——让旁边的人听，比你自己的耳朵准。",
    ),
    ConceptCard(
        "效果器参数都是什么意思？",
        "Gain=失真度（越大越脏），EQ 三段调音色明暗，Delay=回声，Reverb=空间感，Tone=音色亮度。调音色不是抄参数，是理解每个旋钮在动什么。",
        "摇滚节奏音色起点：Gain 40-60、高频略收、Delay 关、Reverb 小。先清音再加 Gain，每加一档听杂音变化。",
        "固定一个 riff，只动 Gain 从 0 加到 70，用耳朵给每个档位做标记。",
    ),
    ConceptCard(
        "和弦为什么是 135（C = C E G）？",
        "和弦是把音按三度叠起来：大三和弦 = 根音 + 大三度 + 纯五度。C 和弦 = C E G 三个音，指板上的按法只是这三音（含重复音）的集中营。",
        "在指板上分别找出 C、E、G 三个音（C：2 弦 1 品、E：1 弦空弦或 4 弦 2 品、G：3 弦空弦或 6 弦 3 品）。",
        "把 Em（E G B）拆开逐音找位置，再合起来弹，理解和弦不是背图。",
    ),
)

/** F102 概念卡片对话框：默认只显示标题，点开看三段内容。 */
@Composable
internal fun ConceptCardsDialog(onDismiss: () -> Unit) {
    var expandedIndex by remember { mutableIntStateOf(-1) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("乐理概念卡片") },
        text = {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 420.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                itemsIndexed(CONCEPT_CARDS) { index, card ->
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(
                            modifier = Modifier
                                .clickable { expandedIndex = if (expandedIndex == index) -1 else index }
                                .padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Text(card.title, style = MaterialTheme.typography.titleSmall)
                            if (expandedIndex == index) {
                                Text(card.plain, style = MaterialTheme.typography.bodySmall)
                                Text(
                                    "🎸 在吉他上：${card.onGuitar}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Text(
                                    "✏️ 试一试：${card.drill}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            } else {
                                Text(
                                    card.plain,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("关闭") } },
    )
}
