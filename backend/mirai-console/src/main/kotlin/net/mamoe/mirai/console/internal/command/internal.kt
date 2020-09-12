/*
 * Copyright 2019-2020 Mamoe Technologies and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AFFERO GENERAL PUBLIC LICENSE version 3 license that can be found via the following link.
 *
 * https://github.com/mamoe/mirai/blob/master/LICENSE
 */

package net.mamoe.mirai.console.internal.command

import net.mamoe.mirai.console.command.Command
import net.mamoe.mirai.console.command.Command.Companion.primaryName
import net.mamoe.mirai.console.permission.Permission
import net.mamoe.mirai.console.permission.PermissionService
import net.mamoe.mirai.contact.Group
import net.mamoe.mirai.contact.Member
import kotlin.math.max
import kotlin.math.min


internal infix fun Array<String>.matchesBeginning(list: List<Any>): Boolean {
    this.forEachIndexed { index, any ->
        if (list[index] != any) return false
    }
    return true
}

internal infix fun Array<out String>.intersectsIgnoringCase(other: Array<out String>): Boolean {
    val max = this.size.coerceAtMost(other.size)
    for (i in 0 until max) {
        if (this[i].equals(other[i], ignoreCase = true)) return true
    }
    return false
}

internal fun String.fuzzyMatchWith(target: String): Double {
    if (this == target) {
        return 1.0
    }
    var match = 0
    for (i in 0..(max(this.lastIndex, target.lastIndex))) {
        val t = target.getOrNull(match) ?: break
        if (t == this.getOrNull(i)) {
            match++
        }
    }

    val longerLength = max(this.length, target.length)
    val shorterLength = min(this.length, target.length)

    return match.toDouble() / (longerLength + (shorterLength - match))
}


/**
 * @return candidates
 */
internal fun Group.fuzzySearchMember(
    nameCardTarget: String,
    minRate: Double = 0.2, // 参与判断, 用于提示可能的解
    matchRate: Double = 0.6,// 最终选择的最少需要的匹配率, 减少歧义
    /**
     * 如果有多个值超过 [matchRate], 并相互差距小于等于 [disambiguationRate], 则认为有较大歧义风险, 返回可能的解的列表.
     */
    disambiguationRate: Double = 0.1,
): List<Pair<Member, Double>> {
    val candidates = (this.members + botAsMember)
        .associateWith { it.nameCard.fuzzyMatchWith(nameCardTarget) }
        .filter { it.value >= minRate }
        .toList()
        .sortedByDescending { it.second }

    val bestMatches = candidates.filter { it.second >= matchRate }

    return when {
        bestMatches.isEmpty() -> candidates
        bestMatches.size == 1 -> listOf(bestMatches.single().first to 1.0)
        else -> {
            if (bestMatches.first().second - bestMatches.last().second <= disambiguationRate) {
                // resolution ambiguity
                candidates
            } else {
                listOf(bestMatches.first().first to 1.0)
            }
        }
    }
}

internal fun Command.createOrFindCommandPermission(parent: Permission): Permission {
    val id = owner.permissionId(primaryName)
    return PermissionService.INSTANCE[id] ?: PermissionService.INSTANCE.register(id, description, parent)
}

//// internal
