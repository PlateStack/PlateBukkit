/*
 *  Copyright (C) 2017 José Roberto de Araújo Júnior
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package org.platestack.bukkit.cmd

import org.bukkit.Bukkit
import org.platestack.api.cmd.CommandCall
import org.platestack.api.cmd.CommandRegistry
import org.platestack.api.cmd.CommandResult
import org.bukkit.command.CommandSender as BukkitSender

internal object BukkitCommandRegistry : CommandRegistry() {
    override fun execute(call: CommandCall): CommandResult {
        val sender = call.sender as? BukkitSender ?: return CommandResult.FAILED
        val cmd = StringBuilder("/")
        call.path.joinTo(cmd, " ")
        call.arguments.takeIf { it.isNotEmpty() }?.joinTo(cmd, " ", " ")
        if(Bukkit.dispatchCommand(sender, cmd.toString())) {
            return CommandResult.SUCCESS
        }
        else {
            return CommandResult.FAILED
        }
    }
}
