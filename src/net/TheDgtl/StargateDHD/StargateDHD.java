package net.TheDgtl.StargateDHD;

/**
 * Stargate-DHD - A DHD plugin for the Stargate plugin for the Bukkit plugin for Minecraft
 * Copyright (C) 2011 Steven "Drakia" Scott <Drakia@Gmail.com>
 * 
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

import java.util.HashMap;
import java.util.logging.Logger;

import net.TheDgtl.Stargate.Portal;
import net.TheDgtl.Stargate.Stargate;
import net.TheDgtl.Stargate.event.StargateCloseEvent;
import net.TheDgtl.Stargate.event.StargateDeactivateEvent;
import net.TheDgtl.Stargate.event.StargateListener;

import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.Sign;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.Event.Priority;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockListener;
import org.bukkit.event.block.SignChangeEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.EntityListener;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerListener;
import org.bukkit.event.server.PluginDisableEvent;
import org.bukkit.event.server.PluginEnableEvent;
import org.bukkit.event.server.ServerListener;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginDescriptionFile;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;

import com.nijikokun.bukkit.Permissions.Permissions;

public class StargateDHD extends JavaPlugin {
	// Hooking plugins
	private Stargate stargate = null;
	private Permissions permissions = null;
	
	// Listeners
	private final bListener blockListener = new bListener();
	private final sListener serverListener = new sListener();
	private final pListener playerListener = new pListener();
	private final eListener entityListener = new eListener();
	private final sgListener stargateListener = new sgListener();
	
	public static Logger log;
	private PluginManager pm;
	
	// List used for clearing DHD signs
	HashMap<Portal, Block> activeList = new HashMap<Portal, Block>();
	
	public void onEnable() {
		PluginDescriptionFile pdfFile = this.getDescription();
		pm = getServer().getPluginManager();
		log = Logger.getLogger("Minecraft");
		
		log.info(pdfFile.getName() + " v." + pdfFile.getVersion() + " is enabled.");
		
		permissions = (Permissions)checkPlugin("Permissions");
		stargate = (Stargate)checkPlugin("Stargate");
		
		pm.registerEvent(Event.Type.PLAYER_INTERACT, playerListener, Priority.Normal, this);
		pm.registerEvent(Event.Type.SIGN_CHANGE, blockListener, Priority.Normal, this);
		pm.registerEvent(Event.Type.BLOCK_BREAK, blockListener, Priority.Normal, this);
		pm.registerEvent(Event.Type.ENTITY_EXPLODE, entityListener, Priority.Normal, this);
		pm.registerEvent(Event.Type.PLUGIN_ENABLE, serverListener, Priority.Monitor, this);
		pm.registerEvent(Event.Type.PLUGIN_DISABLE, serverListener, Priority.Monitor, this);
		pm.registerEvent(Event.Type.CUSTOM_EVENT, stargateListener, Priority.Monitor, this);
		
	}
	
	public void onDisable() {
		
	}
	
	/*
	 * Fetch the Sign object of a DHD, or null if not a DHD
	 */
	public Sign getDHD(Block b) {
		if (b == null) return null;
		if (b.getType() != Material.WALL_SIGN && b.getType() != Material.SIGN_POST) return null;
		Sign sign = (Sign)b.getState();
		if (!sign.getLine(0).equalsIgnoreCase("::DHD::")) return null;
		return sign;
	}
	
	public void onSignClicked(Player player, Portal portal) {
		Portal destination = portal.getDestination();
		
		// Gate not active
		if (!portal.isActive()) return;
		
		// Always-open gate -- Do nothing
		if (portal.isAlwaysOn()) return;
		
		// Invalid destination
		if ((destination == null) || (destination == portal)) {
			Stargate.sendMessage(player, Stargate.getString("invalidMsg"));
			return;
		}
		
		// Gate is already open
		if (portal.isOpen()) {
			portal.close(false);
			return;
		}
		
		// Check if the player can use the private gate
		if (portal.isPrivate() && !Stargate.canPrivate(player, portal)) {
			Stargate.sendMessage(player, Stargate.getString("denyMsg"));
			return;
		}
		
		// Destination blocked
		if ((destination.isOpen()) && (!destination.isAlwaysOn())) {
			Stargate.sendMessage(player, Stargate.getString("blockMsg"));
			return;
		}
		
		// Open gate
		portal.open(null, false);
	}
	
	private class bListener extends BlockListener {
		/*
		 * We don't want just anybody creating a DHD
		 */
		@Override
		public void onSignChange(SignChangeEvent event) {
			if (stargate == null) return;
			Player p = event.getPlayer();
			Block b = event.getBlock();
			if (b.getType() != Material.WALL_SIGN && b.getType() != Material.SIGN_POST) return;
			if (!event.getLine(0).equals("::DHD::")) return;
			
			if (!hasPerm(p, "stargate.dhd.create")) {
				event.setLine(0,  "");
				p.sendMessage("[DHD] Permission Denied");
				return;
			}
			
			String stargateName = event.getLine(1);
			String stargateNetwork = event.getLine(2);
			if (stargateNetwork.isEmpty()) stargateNetwork = Stargate.getDefaultNetwork();
			if (stargateNetwork.length() > 11) stargateNetwork = stargateNetwork.substring(0, 11);
			Portal portal = Portal.getByName(stargateName, stargateNetwork);
			if (portal == null) {
				event.setLine(0, "");
				p.sendMessage("[DHD] Invalid Stargate");
				return;
			}
			
			event.setLine(1, portal.getName());
			event.setLine(2, portal.getNetwork());
			p.sendMessage("[DHD] Remote dialing device for --" + portal.getName() + "-- created");
			log.info("[Stargate-DHD] " + p.getName() + " created DHD at (" + b.getWorld().getName() + "," + b.getX() + "," + b.getY() + "," + b.getZ() + ")");
		}
		
		@Override
		public void onBlockBreak(BlockBreakEvent event) {
			if (stargate == null) return;
			Player p = event.getPlayer();
			Block b = event.getBlock();
			// Check if a sign
			if (b.getType() != Material.WALL_SIGN && b.getType() != Material.SIGN_POST) return;
			Sign sign = (Sign)b.getState();
			// Check if a DHD
			if (!sign.getLine(0).equals("::DHD::")) return;
			if (!hasPerm(p, "stargate.dhd.destroy")) {
				event.setCancelled(true);
				p.sendMessage("[DHD] Permission Denied");
				return;
			}
			p.sendMessage("[DHD] Remote dialing device destroyed");
			log.info("[Stargate-DHD] " + p.getName() + " destroyed DHD at (" + b.getWorld().getName() + "," + b.getX() + "," + b.getY() + "," + b.getZ() + ")");
		}
	}
	
	private class sgListener extends StargateListener {
		@Override
		public void onStargateDeactivate(StargateDeactivateEvent event) {
			Portal portal = event.getPortal();
			Block b = activeList.get(portal);
			if (b == null) return;
			if (b.getType() != Material.SIGN_POST && b.getType() != Material.WALL_SIGN) return;
			final Sign sign = (Sign)b.getState();
			sign.setLine(3, "");
			sign.update();
			activeList.remove(portal);
		}
		
		@Override
		public void onStargateClose(StargateCloseEvent event) {
			Portal portal = event.getPortal();
			Block b = activeList.get(portal);
			if (b == null) return;
			if (b.getType() != Material.SIGN_POST && b.getType() != Material.WALL_SIGN) return;
			final Sign sign = (Sign)b.getState();
			sign.setLine(3, "");
			sign.update();
			activeList.remove(portal);
		}
	}
	
	private class pListener extends PlayerListener {
		@Override
		public void onPlayerInteract(PlayerInteractEvent event) {
			if (stargate == null) return;
			final Sign sign = getDHD(event.getClickedBlock());
			if (sign == null) return;

			Player p = event.getPlayer();
			String stargateName = sign.getLine(1);
			String stargateNetwork = sign.getLine(2);
			Portal portal = Portal.getByName(stargateName, stargateNetwork);
			if (portal == null) return;
			
			/*
			 * A DHD activates a stargate when it's damaged.
			 */
			if (event.getAction() == Action.LEFT_CLICK_BLOCK) {
				onSignClicked(p, portal);
				return;
			}
			/*
			 * A DHD will scroll destinations on right click
			 */
			if (event.getAction() == Action.RIGHT_CLICK_BLOCK) {
				if (portal.isOpen() || portal.isFixed()) return;
				
				if (!hasPerm(p, "stargate.dhd.use") || !Stargate.canAccessNetwork(p, portal.getNetwork())) {
					Stargate.sendMessage(p, Stargate.getString("denyMsg"));
					return;
				}
				activeList.put(portal, event.getClickedBlock());
				portal.cycleDestination(p);
				sign.setLine(2, portal.getNetwork());
				sign.setLine(3, "--" + portal.getDestinationName() + "--");
				sign.update();
			}
		}
	}
	
	/*
	 * Check if a user has a given permission
	 */
	public boolean hasPerm(Player player, String perm) {
		if (permissions != null) {
			return permissions.getHandler().has(player, perm);
		} else {
			return player.hasPermission(perm);
		}
	}
	
	// Awesome code used to load dependencies!
	
	/*
	 * Check if a plugin is loaded/enabled already. Returns the plugin if so, null otherwise
	 */
	private Plugin checkPlugin(String p) {
		Plugin plugin = pm.getPlugin(p);
		return checkPlugin(plugin);
	}
	
	private Plugin checkPlugin(Plugin plugin) {
		if (plugin != null && plugin.isEnabled()) {
			log.info("[Stargate-DHD] Found " + plugin.getDescription().getName() + " (v" + plugin.getDescription().getVersion() + ")");
			return plugin;
		}
		return null;
	}
	
	private class sListener extends ServerListener {
		@Override
		public void onPluginEnable(PluginEnableEvent event) {
			if (stargate == null) {
				if (event.getPlugin().getDescription().getName().equalsIgnoreCase("Stargate")) {
					stargate = (Stargate)checkPlugin(event.getPlugin());
				}
			}
			if (permissions == null) {
				if (event.getPlugin().getDescription().getName().equalsIgnoreCase("Permissions")) {
					permissions = (Permissions)checkPlugin(event.getPlugin());
				}
			}
		}
		
		@Override
		public void onPluginDisable(PluginDisableEvent event) {
			if (event.getPlugin() == stargate) {
				log.info("[Stargate-DHD] Stargate plugin lost.");
				stargate = null;
			}
			if (event.getPlugin() == permissions) {
				log.info("[Stargate-DHD] Permissions plugin lost.");
				permissions = null;
			}
		}
	}
	
	private class eListener extends EntityListener {
		@Override
		public void onEntityExplode(EntityExplodeEvent event) {
			if (event.isCancelled()) return;
			
			for (Block b : event.blockList()) {
				Sign sign = getDHD(b);
				if (sign == null) continue;
				b.setType(b.getType());
				event.setCancelled(true);
			}
		}
	}
}
