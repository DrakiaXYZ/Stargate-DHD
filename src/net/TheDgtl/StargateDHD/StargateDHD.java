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

import java.util.logging.Logger;

import net.TheDgtl.Stargate.Portal;
import net.TheDgtl.Stargate.Stargate;
import net.TheDgtl.Stargate.iConomyHandler;

import org.bukkit.ChatColor;
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
import org.bukkit.util.config.Configuration;

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
	
	public static Logger log;
	private PluginManager pm;
	private Plugin plugin = this;
	
	// Used for getting Stargate config info
	private Configuration sgConfig = null;
	// Stargate config variables
	private String teleMsg = "";
	private String regMsg = "";
	private String dmgMsg = "";
	private String denyMsg = "";
	private String invMsg = "";
	private String blockMsg = "";
	private String defNetwork = "";
	
	public void onEnable() {
		PluginDescriptionFile pdfFile = this.getDescription();
		pm = getServer().getPluginManager();
		log = Logger.getLogger("Minecraft");
		
		log.info(pdfFile.getName() + " v." + pdfFile.getVersion() + " is enabled.");
		
		permissions = (Permissions)checkPlugin("Permissions");
		stargate = (Stargate)checkPlugin("Stargate");
		if (stargate != null) loadSGConfig();
		
		pm.registerEvent(Event.Type.PLAYER_INTERACT, playerListener, Priority.Normal, this);
		pm.registerEvent(Event.Type.SIGN_CHANGE, blockListener, Priority.Normal, this);
		pm.registerEvent(Event.Type.BLOCK_BREAK, blockListener, Priority.Normal, this);
		pm.registerEvent(Event.Type.ENTITY_EXPLODE, entityListener, Priority.Normal, this);
		pm.registerEvent(Event.Type.PLUGIN_ENABLE, serverListener, Priority.Monitor, this);
		pm.registerEvent(Event.Type.PLUGIN_DISABLE, serverListener, Priority.Monitor, this);
		
	}
	
	public void onDisable() {
		
	}
	
	/*
	 * Fetch the Sign object of a DHD, or null if not a DHD
	 */
	public Sign getDHD(Block b) {
		if (b == null) return null;
		if (b.getType() != Material.WALL_SIGN) return null;
		Sign sign = (Sign)b.getState();
		if (!sign.getLine(0).equalsIgnoreCase("::DHD::")) return null;
		return sign;
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
			if (b.getType() != Material.WALL_SIGN) return;
			
			if (event.getLine(0).equals("::DHD::")) {
				if (!hasPerm(p, "stargate.dhd.create", p.isOp())) {
					event.setCancelled(true);
					p.sendMessage("[DHD] Permission Denied");
					return;
				}
				
				String stargateName = event.getLine(1);
				String stargateNetwork = event.getLine(2);
				if (stargateNetwork.isEmpty()) stargateNetwork = defNetwork;
				Portal portal = Portal.getByName(stargateName, stargateNetwork);
				if (portal == null) {
					event.setCancelled(true);
					p.sendMessage("[DHD] Invalid Stargate");
					return;
				}
				
				event.setLine(1, portal.getName());
				event.setLine(2, portal.getNetwork());
				p.sendMessage("[DHD] Remote dialing device for --" + event.getLine(1) + "-- created");
				log.info("[Stargate-DHD] " + p.getName() + " created DHD at (" + b.getWorld().getName() + "," + b.getX() + "," + b.getY() + "," + b.getZ() + ")");
			}
		}
		
		@Override
		public void onBlockBreak(BlockBreakEvent event) {
			if (stargate == null) return;
			Player p = event.getPlayer();
			Block b = event.getBlock();
			// Check if a sign
			if (b.getType() != Material.WALL_SIGN) return;
			Sign sign = (Sign)b.getState();
			// Check if a DHD
			if (!sign.getLine(0).equals("::DHD::")) return;
			if (!hasPerm(p, "stargate.dhd.destroy", p.isOp())) {
				event.setCancelled(true);
				p.sendMessage("[DHD] Permission Denied");
				return;
			}
			p.sendMessage("[DHD] Remote dialing device destroyed");
			log.info("[Stargate-DHD] " + p.getName() + " destroyed DHD at (" + b.getWorld().getName() + "," + b.getX() + "," + b.getY() + "," + b.getZ() + ")");
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
			if (stargateNetwork.isEmpty()) stargateNetwork = defNetwork;
			final Portal portal = Portal.getByName(stargateName, stargateNetwork);
			if (portal == null) return;
			
			/*
			 * A DHD activates a stargate when it's damaged.
			 */
			if (event.getAction() == Action.LEFT_CLICK_BLOCK) {
				if (!portal.isActive()) return;
				
				Portal dest = portal.getDestination();
	
				if (!portal.isOpen()) {
					if (iConomyHandler.useiConomy() && iConomyHandler.getBalance(p.getName()) < iConomyHandler.useCost) {
						p.sendMessage(ChatColor.RED + iConomyHandler.inFundMsg);
					} else if ((!portal.isFixed()) && portal.isActive() &&  (portal.getActivePlayer() != p)) {
						portal.deactivate();
						if (!denyMsg.isEmpty()) {
							p.sendMessage(ChatColor.RED + denyMsg);
						}
					} else if (portal.isPrivate() && !portal.getOwner().equals(p.getName()) && !hasPerm(p, "stargate.private", p.isOp())) {
						if (!denyMsg.isEmpty()) {
							p.sendMessage(ChatColor.RED + denyMsg);
						}
					} else if ((dest == null) || (dest == portal)) {
						if (!invMsg.isEmpty()) {
							p.sendMessage(ChatColor.RED + invMsg);
						}
					} else if ((dest.isOpen()) && (!dest.isAlwaysOn())) {
						if (!blockMsg.isEmpty()) {
							p.sendMessage(ChatColor.RED + blockMsg);
						}
					} else {
						portal.open(null, false);
					}
				} else {
					portal.close(false);
				}
			}
			/*
			 * A DHD will scroll destinations on right click
			 */
			if (event.getAction() == Action.RIGHT_CLICK_BLOCK) {
				if (portal.isOpen() || portal.isFixed()) return;
				
				if (!hasPerm(p, "stargate.use", true) || !hasPerm(p, "stargate.dhd.use", true) ||
					(Stargate.networkFilter && !hasPerm(p, "stargate.network." + portal.getNetwork(), p.isOp()))) {
					if (!denyMsg.isEmpty()) {
						p.sendMessage(denyMsg);
					}
					return;
				}
				
				portal.cycleDestination(p);
				sign.setLine(2, portal.getNetwork());
				sign.setLine(3, "--" + portal.getDestinationName() + "--");
				// Use the scheduler so the sign actually updates.
				getServer().getScheduler().scheduleSyncDelayedTask(plugin, new Runnable() {
					public void run() {
						sign.update();
					}
				}, 2);
			}
		}
	}
	
	/*
	 * Check if a user has a given permission
	 */
	public boolean hasPerm(Player player, String perm, boolean def) {
		if (permissions != null) {
			return permissions.getHandler().has(player, perm);
		} else {
			return def;
		}
	}
	
	// Awesome code used to load dependencies!
	/*
	 * Load stargate config information.
	 */
	private void loadSGConfig() {
		if (stargate == null) return;
		sgConfig = stargate.getConfiguration();
		sgConfig.load();
		
		// Stargate response messages
		teleMsg = sgConfig.getString("teleport-message", teleMsg);
		regMsg = sgConfig.getString("portal-create-message", regMsg);
		dmgMsg = sgConfig.getString("portal-destroy-message", dmgMsg);
		denyMsg = sgConfig.getString("not-owner-message", denyMsg);
		invMsg = sgConfig.getString("not-selected-message", invMsg);
		blockMsg = sgConfig.getString("other-side-blocked-message", blockMsg);
		defNetwork = Stargate.getDefaultNetwork();
	}
	
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
					if (stargate != null) loadSGConfig();
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
				sgConfig = null;
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
